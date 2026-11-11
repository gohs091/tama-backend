package com.tamagochi.tamagochi.service.plaza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.service.battle.MeetBattleService.Peer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PlazaServiceTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final PlazaService plaza = new PlazaService(now::get);
    private long seq = 0;

    static final class FakePeer implements Peer {
        final String id;
        final List<JsonNode> got = new ArrayList<>();
        boolean closed;
        FakePeer(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public void send(String json) { try { got.add(OM.readTree(json)); } catch (Exception e) { throw new IllegalStateException(e); } }
        @Override public void close() { closed = true; }
        JsonNode last(String t) { for (int i = got.size() - 1; i >= 0; i--) if (got.get(i).path("t").asText().equals(t)) return got.get(i); return null; }
        long count(String t) { return got.stream().filter(m -> m.path("t").asText().equals(t)).count(); }
    }

    /** 한 사람이 들어온다 (그림은 CDN 주소가 있는 것으로) */
    private FakePeer enter(String inviteKey) { return enter(inviteKey, 3); }

    private FakePeer enter(String inviteKey, int level) {
        long n = ++seq;
        FakePeer p = new FakePeer("p" + n);
        plaza.connect(p, new PlazaService.Profile(n, "닉" + n, "끄적" + n, level, "CHILD", "https://cdn.example/pet" + n + ".png", "pub" + n, null), inviteKey);
        plaza.onMessage(p, "{\"t\":\"hello\",\"facing\":\"left\"}");
        return p;
    }

    @Test
    void 들어오면_같은_방에_모이고_서로_들어온_것을_안다() {
        FakePeer a = enter(null), b = enter(null);
        JsonNode roomB = b.last("room");
        assertThat(roomB.path("no").asInt()).isEqualTo(a.last("room").path("no").asInt());
        assertThat(roomB.path("members")).hasSize(2);
        assertThat(roomB.path("cap").asInt()).isEqualTo(PlazaService.CAP);
        assertThat(a.last("enter").path("m").path("nickname").asText()).isEqualTo("닉2");
        assertThat(a.last("enter").path("m").path("facing").asText()).isEqualTo("left");
        assertThat(plaza.roomCount()).isEqualTo(1);
    }

    @Test
    void 방이_꽉_차면_새_방이_생기고_초대_열쇠로는_꽉_찬_방에도_들어간다() {
        FakePeer first = enter(null);
        for (int i = 1; i < PlazaService.CAP; i++) enter(null);
        assertThat(plaza.roomCount()).isEqualTo(1);
        FakePeer overflow = enter(null);                       // 21번째는 새 방
        assertThat(plaza.roomCount()).isEqualTo(2);
        assertThat(overflow.last("room").path("no").asInt()).isNotEqualTo(first.last("room").path("no").asInt());

        FakePeer friend = enter(first.last("room").path("key").asText());   // 초대받은 친구는 꽉 찬 방으로
        assertThat(friend.last("room").path("no").asInt()).isEqualTo(first.last("room").path("no").asInt());
        assertThat(friend.last("room").path("members")).hasSize(PlazaService.CAP + 1);

        FakePeer next = enter(null);                           // 다음 사람은 자리 남은 방 가운데 북적이는 방(두 번째 방)
        assertThat(next.last("room").path("no").asInt()).isEqualTo(overflow.last("room").path("no").asInt());
    }

    @Test
    void 나가면_방에_알리고_아무도_없는_방은_사라지며_다녀간_끄적이로_남는다() {
        FakePeer a = enter(null), b = enter(null);
        String bId = b.last("room").path("me").asText();
        plaza.disconnect(b);
        assertThat(a.last("leave").path("id").asText()).isEqualTo(bId);
        plaza.onMessage(a, "{\"t\":\"leave\"}");
        assertThat(a.closed).isTrue();
        assertThat(plaza.roomCount()).isZero();
        assertThat(plaza.recentCount()).isEqualTo(2);

        FakePeer c = enter(null);                              // 혼자 들어온 사람에게는 다녀간 끄적이를 채워 보낸다
        assertThat(c.last("room").path("members")).hasSize(1);
        assertThat(c.last("room").path("ghosts")).hasSize(2);
        assertThat(c.last("room").path("ghosts").get(0).path("id").asText()).startsWith("g");
    }

    @Test
    void 걷기와_인사는_방_사람에게만_전하고_너무_잦으면_버린다() {
        FakePeer a = enter(null), b = enter(null);
        String aId = a.last("room").path("me").asText();
        now.addAndGet(1000);
        plaza.onMessage(a, "{\"t\":\"move\",\"x\":5000,\"y\":-30}");
        assertThat(b.last("move").path("id").asText()).isEqualTo(aId);
        assertThat(b.last("move").path("x").asInt()).isEqualTo(980);     // 광장 밖으로는 못 나간다
        assertThat(b.last("move").path("y").asInt()).isZero();
        assertThat(a.count("move")).isZero();                            // 보낸 사람에게는 돌려보내지 않는다
        plaza.onMessage(a, "{\"t\":\"move\",\"x\":100,\"y\":100}");        // 바로 또 보내면 버린다
        assertThat(b.count("move")).isEqualTo(1);

        plaza.onMessage(a, "{\"t\":\"emote\",\"k\":\"bow\",\"to\":\"" + b.last("room").path("me").asText() + "\"}");
        assertThat(b.last("emote").path("k").asText()).isEqualTo("bow");
        now.addAndGet(1000);
        plaza.onMessage(a, "{\"t\":\"emote\",\"k\":\"<script>\"}");        // 정해진 인사만 받는다
        assertThat(b.count("emote")).isEqualTo(1);
        now.addAndGet(1000);
        plaza.onMessage(b, "{\"t\":\"emote\",\"k\":\"bow\",\"to\":\"" + aId + "\",\"re\":true}");   // 답 인사는 표시를 달아 전한다
        assertThat(a.last("emote").path("re").asBoolean()).isTrue();
    }

    @Test
    void 그림이_없으면_앱이_보낸_작은_그림을_쓰고_그것도_없으면_들이지_않는다() {
        FakePeer p = new FakePeer("x1");
        plaza.connect(p, new PlazaService.Profile(900L, "닉", "끄적", 1, "BABY", null, "pub", null), null);
        plaza.onMessage(p, "{\"t\":\"hello\",\"image\":\"data:image/png;base64,AAAA\"}");
        assertThat(p.last("room").path("members").get(0).path("image").asText()).startsWith("data:image/png");

        FakePeer q = new FakePeer("x2");
        plaza.connect(q, new PlazaService.Profile(901L, "닉", "끄적", 1, "BABY", null, "pub", null), null);
        plaza.onMessage(q, "{\"t\":\"hello\"}");
        assertThat(q.last("error")).isNotNull();
        assertThat(q.closed).isTrue();
    }

    @Test
    void 기술은_방_모두에게_전하고_맞은_쪽이_알리면_레벨_차이만큼만_다른_세기로_튕긴다() {
        FakePeer strong = enter(null, 20), weak = enter(null, 4), other = enter(null, 4);
        String strongId = strong.last("room").path("me").asText(), weakId = weak.last("room").path("me").asText();
        now.addAndGet(5000);
        plaza.onMessage(strong, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":600,\"ty\":500,\"to\":\"" + weakId + "\"}");
        assertThat(strong.last("skill")).isNotNull();                              // 쓴 사람도 받는다
        assertThat(weak.last("skill").path("id").asText()).isEqualTo(strongId);
        assertThat(weak.last("skill").has("kb")).isFalse();                        // 쏜 것만으로는 튕기지 않는다 (피할 수 있다)
        plaza.onMessage(strong, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":600,\"ty\":500}");   // 쉬는 시간 안에는 못 쓴다
        assertThat(weak.count("skill")).isEqualTo(1);

        plaza.onMessage(other, "{\"t\":\"hit\",\"by\":\"" + strongId + "\"}");   // 겨눈 상대가 아닌 사람의 알림은 버린다
        assertThat(weak.count("knock")).isZero();
        plaza.onMessage(weak, "{\"t\":\"hit\",\"by\":\"" + strongId + "\"}");    // 맞은 본인이 알린다
        assertThat(other.last("knock").path("id").asText()).isEqualTo(weakId);
        assertThat(other.last("knock").path("by").asText()).isEqualTo(strongId);
        assertThat(other.last("knock").path("kb").asDouble()).isEqualTo(1.32);     // 16레벨 위 → 1 + 16×0.02
        plaza.onMessage(weak, "{\"t\":\"hit\",\"by\":\"" + strongId + "\"}");    // 한 번 쏜 기술은 한 번만 맞는다
        assertThat(other.count("knock")).isEqualTo(1);

        plaza.onMessage(weak, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":400,\"ty\":500,\"to\":\"" + strongId + "\"}");
        now.addAndGet(PlazaService.HIT_WINDOW_MS + 1);
        plaza.onMessage(strong, "{\"t\":\"hit\",\"by\":\"" + weakId + "\"}");    // 너무 늦은 알림은 버린다
        assertThat(other.count("knock")).isEqualTo(1);
        plaza.onMessage(weak, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":400,\"ty\":500,\"to\":\"" + strongId + "\"}");
        plaza.onMessage(strong, "{\"t\":\"hit\",\"by\":\"" + weakId + "\"}");
        assertThat(other.last("knock").path("kb").asDouble()).isEqualTo(0.7);      // 낮은 레벨이 쳐도 하한만큼은 밀린다
        assertThat(PlazaService.knockback(10, 10)).isEqualTo(1.0);
        assertThat(PlazaService.knockback(60, 1)).isEqualTo(1.4);                  // 상한
    }
}
