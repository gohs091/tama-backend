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

    private static final PlazaService.EventTimes T = PlazaService.EventTimes.HOURLY;

    /** 다음 정각으로 가서 모이기 → 준비(주어진 사람들이 참가) → 밀어내기까지 넘긴다 */
    private void startFight(FakePeer... joiners) {
        now.set((now.get() / T.period() + 1) * T.period());
        plaza.tick();                                   // 모이기
        now.addAndGet(T.gather());
        plaza.tick();                                   // 준비: 놀이터 안에 있는 앱이 참가를 알린다
        for (FakePeer p : joiners) plaza.onMessage(p, "{\"t\":\"join\"}");
        now.addAndGet(T.ready());
        plaza.tick();                                   // 밀어내기
    }

    private static List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.isTextual() ? n.asText() : n.path("id").asText()));
        return out;
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
        String otherId = other.last("room").path("me").asText();
        startFight(strong, weak, other);
        plaza.onMessage(weak, "{\"t\":\"hit\",\"by\":\"" + strongId + "\"}");    // 쏜 적이 없는 사람에게 맞았다는 알림은 버린다
        assertThat(other.count("knock")).isZero();
        now.addAndGet(5000);
        plaza.onMessage(strong, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":580,\"ty\":500,\"d\":1}");
        assertThat(strong.last("skill")).isNotNull();                              // 쓴 사람도 받는다
        assertThat(weak.last("skill").path("id").asText()).isEqualTo(strongId);
        assertThat(weak.last("skill").path("d").asInt()).isEqualTo(1);             // 바라보는 쪽
        plaza.onMessage(strong, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":580,\"ty\":500,\"d\":1}");   // 쉬는 시간 안에는 못 쓴다
        assertThat(weak.count("skill")).isEqualTo(1);
        assertThat(weak.count("knock")).isZero();                                  // 쏜 것만으로는 튕기지 않는다 (피할 수 있다)

        plaza.onMessage(weak, "{\"t\":\"hit\",\"by\":\"" + strongId + "\"}");    // 맞은 본인이 알린다
        assertThat(other.last("knock").path("id").asText()).isEqualTo(weakId);
        assertThat(other.last("knock").path("by").asText()).isEqualTo(strongId);
        assertThat(other.last("knock").path("kb").asDouble()).isEqualTo(1.32);     // 16레벨 위 → 1 + 16×0.02
        assertThat(other.last("knock").path("s").asInt()).isPositive();            // 벽에 튕길 때 모두 같은 길로 튕기게 하는 씨앗
        plaza.onMessage(weak, "{\"t\":\"hit\",\"by\":\"" + strongId + "\"}");    // 같은 기술에 두 번 맞지 않는다
        assertThat(other.count("knock")).isEqualTo(1);
        plaza.onMessage(other, "{\"t\":\"hit\",\"by\":\"" + strongId + "\"}");   // 같은 길에 있던 다른 끄적이도 맞는다
        assertThat(weak.last("knock").path("id").asText()).isEqualTo(otherId);
        plaza.onMessage(strong, "{\"t\":\"hit\",\"by\":\"" + strongId + "\",\"id\":\"" + weakId + "\"}");   // 쏜 사람이 남을 대신해 알릴 수는 없다
        assertThat(other.count("knock")).isEqualTo(2);

        now.addAndGet(5000);
        plaza.onMessage(weak, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":420,\"ty\":500,\"d\":-1}");
        now.addAndGet(PlazaService.HIT_WINDOW_MS + 1);
        plaza.onMessage(strong, "{\"t\":\"hit\",\"by\":\"" + weakId + "\"}");    // 너무 늦은 알림은 버린다
        assertThat(other.count("knock")).isEqualTo(2);
        plaza.onMessage(weak, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":420,\"ty\":500,\"d\":-1}");
        plaza.onMessage(strong, "{\"t\":\"hit\",\"by\":\"" + weakId + "\"}");
        assertThat(other.last("knock").path("kb").asDouble()).isEqualTo(0.7);      // 낮은 레벨이 쳐도 하한만큼은 밀린다
        assertThat(PlazaService.knockback(10, 10)).isEqualTo(1.0);
        assertThat(PlazaService.knockback(60, 1)).isEqualTo(1.4);                  // 상한
    }

    @Test
    void 다녀간_끄적이는_쏜_사람이_알리면_튕기고_그_사람이_다시_들어오면_지우라고_알린다() {
        FakePeer gone = enter(null);
        plaza.disconnect(gone);                                                    // 닉1이 다녀갔다
        FakePeer a = enter(null), b = enter(null);
        assertThat(a.last("room").path("ghosts")).hasSize(1);
        String ghostId = a.last("room").path("ghosts").get(0).path("id").asText();
        assertThat(ghostId).startsWith(PlazaService.GHOST_PREFIX);
        String aId = a.last("room").path("me").asText();

        startFight(a, b);
        now.addAndGet(5000);
        plaza.onMessage(a, "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":580,\"ty\":500,\"d\":1}");
        plaza.onMessage(b, "{\"t\":\"hit\",\"by\":\"" + aId + "\",\"id\":\"" + ghostId + "\"}");   // 쏜 사람이 아니면 자기만 맞았다고 알릴 수 있다
        assertThat(a.last("knock").path("id").asText()).isEqualTo(b.last("room").path("me").asText());
        plaza.onMessage(a, "{\"t\":\"hit\",\"by\":\"" + aId + "\",\"id\":\"" + ghostId + "\"}");   // 쏜 사람이 다녀간 끄적이를 맞혔다고 알린다
        assertThat(b.last("knock").path("id").asText()).isEqualTo(ghostId);
        assertThat(b.last("knock").path("kb").asDouble()).isEqualTo(1.0);

        // 다녀간 사람이 다시 들어온다: 방 사람들에게 그 기록을 지우라고 알리고, 새로 들어오는 사람에게는 그 기록을 보내지 않는다
        FakePeer back = new FakePeer("back");
        plaza.connect(back, new PlazaService.Profile(1L, "닉1", "끄적1", 3, "CHILD", "https://cdn.example/pet1.png", "pub1", null), null);
        plaza.onMessage(back, "{\"t\":\"hello\"}");
        assertThat(a.last("enter").path("gone").asText()).isEqualTo(ghostId);
        assertThat(back.last("room").path("ghosts")).isEmpty();
        FakePeer late = enter(null);
        assertThat(late.last("room").path("ghosts")).isEmpty();

        plaza.disconnect(back);                                                    // 다시 나가면 같은 id의 기록으로 남는다
        plaza.disconnect(late);
        FakePeer next = enter(null);
        assertThat(next.last("room").path("ghosts")).extracting(g -> g.path("id").asText()).contains(ghostId);
    }

    private static final String SKILL = "{\"t\":\"skill\",\"x\":500,\"y\":500,\"tx\":580,\"ty\":500,\"d\":1}";

    @Test
    void 기술은_정각_밀어내기_동안에만_쓸_수_있고_모이기_준비_밀어내기_순서로_열린다() {
        FakePeer a = enter(null), b = enter(null), watcher = enter(null);
        String aId = a.last("room").path("me").asText(), bId = b.last("room").path("me").asText();
        JsonNode idle = a.last("room").path("ev");
        assertThat(idle.path("phase").asText()).isEqualTo("idle");
        assertThat(idle.path("next").asLong()).isEqualTo((now.get() / T.period() + 1) * T.period());   // 다음 정각
        now.addAndGet(5000);
        plaza.onMessage(a, SKILL);                                                 // 정각이 아니면 기술이 나가지 않는다
        assertThat(b.count("skill")).isZero();

        now.set((now.get() / T.period() + 1) * T.period());
        plaza.tick();
        assertThat(b.last("ev").path("phase").asText()).isEqualTo("gather");       // 정각: 모이기
        assertThat(b.last("ev").path("start").asLong()).isEqualTo(now.get());
        plaza.onMessage(a, SKILL);
        plaza.onMessage(a, "{\"t\":\"join\"}");                                    // 모이는 동안에는 참가를 받지 않는다 (준비 때 놀이터 안에 있어야 한다)
        assertThat(b.count("skill")).isZero();

        now.addAndGet(T.gather());
        plaza.tick();
        assertThat(b.last("ev").path("phase").asText()).isEqualTo("ready");
        plaza.onMessage(a, "{\"t\":\"join\"}");
        plaza.onMessage(b, "{\"t\":\"join\"}");
        now.addAndGet(T.ready());
        plaza.tick();
        JsonNode fight = watcher.last("ev");
        assertThat(fight.path("phase").asText()).isEqualTo("fight");
        assertThat(fight.path("solo").asBoolean()).isFalse();
        assertThat(ids(fight.path("players"))).containsExactly(aId, bId);
        assertThat(ids(fight.path("alive"))).containsExactly(aId, bId);

        plaza.onMessage(watcher, SKILL);                                           // 참가하지 않은 사람은 구경만 한다
        assertThat(a.count("skill")).isZero();
        plaza.onMessage(a, SKILL);
        assertThat(b.count("skill")).isEqualTo(1);
        plaza.onMessage(watcher, "{\"t\":\"hit\",\"by\":\"" + aId + "\"}");        // 구경하는 끄적이는 맞지 않는다
        assertThat(b.count("knock")).isZero();

        plaza.onMessage(watcher, "{\"t\":\"out\"}");                               // 참가자가 아니면 탈락도 없다
        assertThat(a.count("out")).isZero();
        plaza.onMessage(b, "{\"t\":\"out\"}");                                     // b가 놀이터 밖으로 나갔다
        assertThat(a.last("out").path("id").asText()).isEqualTo(bId);
        assertThat(a.last("out").path("left").asInt()).isEqualTo(1);
        JsonNode done = watcher.last("ev");
        assertThat(done.path("phase").asText()).isEqualTo("done");                 // 한 마리만 남으면 끝
        assertThat(ids(done.path("winners"))).containsExactly(aId);
        assertThat(done.path("winners").get(0).path("name").asText()).isEqualTo("닉1");

        now.addAndGet(5000);
        plaza.onMessage(a, SKILL);                                                 // 끝난 뒤에는 다시 못 쓴다
        assertThat(b.count("skill")).isEqualTo(1);
        FakePeer late = enter(null);                                               // 뒤에 들어온 사람도 우승자를 본다
        assertThat(ids(late.last("room").path("ev").path("winners"))).containsExactly(aId);

        now.set((now.get() / T.period() + 1) * T.period());                        // 다음 정각에 새 판
        plaza.tick();
        assertThat(a.last("ev").path("phase").asText()).isEqualTo("gather");
        assertThat(a.last("ev").path("winners")).isEmpty();
    }

    @Test
    void 밀어내는_중에_나가면_탈락이고_시간이_끝나면_남은_끄적이가_함께_우승한다() {
        FakePeer a = enter(null), b = enter(null), c = enter(null);
        String aId = a.last("room").path("me").asText(), bId = b.last("room").path("me").asText();
        startFight(a, b, c);
        plaza.disconnect(c);                                                       // 셋 중 하나가 나가도 둘이 남아 이어진다
        assertThat(a.last("ev").path("phase").asText()).isEqualTo("fight");
        now.addAndGet(T.length());                                                 // 10분이 다 됐다
        plaza.tick();
        assertThat(a.last("ev").path("phase").asText()).isEqualTo("done");
        assertThat(ids(a.last("ev").path("winners"))).containsExactlyInAnyOrder(aId, bId);

        startFight(a, b);                                                          // 다음 판: 둘 중 하나가 앱을 끄면 남은 쪽이 우승
        plaza.disconnect(b);
        assertThat(a.last("ev").path("phase").asText()).isEqualTo("done");
        assertThat(ids(a.last("ev").path("winners"))).containsExactly(aId);
    }

    @Test
    void 참가자가_둘이_안_되면_혼자_연습이고_탈락도_우승도_없다() {
        FakePeer a = enter(null), b = enter(null);
        startFight(a);                                                             // b는 놀이터 밖에 있었다
        JsonNode fight = b.last("ev");
        assertThat(fight.path("phase").asText()).isEqualTo("fight");
        assertThat(fight.path("solo").asBoolean()).isTrue();
        plaza.onMessage(b, SKILL);                                                 // 연습 중에는 누구나 기술을 쓴다
        assertThat(a.count("skill")).isEqualTo(1);
        plaza.onMessage(a, "{\"t\":\"out\"}");
        assertThat(b.count("out")).isZero();
        now.addAndGet(T.length());
        plaza.tick();
        assertThat(b.last("ev").path("phase").asText()).isEqualTo("done");
        assertThat(b.last("ev").path("winners")).isEmpty();
    }

    @Test
    void 모이기가_지난_뒤에_생긴_방은_혼자_연습으로_열린다() {
        now.set((now.get() / T.period() + 1) * T.period() + T.gather() + 120_000);  // 정각 3분 뒤
        FakePeer a = enter(null);
        plaza.tick();
        assertThat(a.last("ev").path("phase").asText()).isEqualTo("fight");
        assertThat(a.last("ev").path("solo").asBoolean()).isTrue();
        now.addAndGet(5000);
        plaza.onMessage(a, SKILL);
        assertThat(a.count("skill")).isEqualTo(1);

        now.set((now.get() / T.period()) * T.period() + T.length() + 60_000);       // 밀어내기 시간이 끝난 뒤에 생긴 방은 열리지 않는다
        plaza.tick();
        FakePeer b = enter(null);
        assertThat(b.last("room").path("ev").path("phase").asText()).isEqualTo("done");
    }

    @Test
    void 서버가_그림을_못_줄였으면_앱이_보낸_그림을_먼저_쓰고_없을_때만_원본_주소를_쓴다() {
        FakePeer a = new FakePeer("spare-a");
        plaza.connect(a, new PlazaService.Profile(901L, "닉901", "끄적", 3, "BABY", null, "pub901", null), null, "https://cdn.example/original.png");
        plaza.onMessage(a, "{\"t\":\"hello\",\"image\":\"data:image/png;base64,AAAA\"}");
        assertThat(a.last("room").path("members").get(0).path("image").asText()).isEqualTo("data:image/png;base64,AAAA");

        FakePeer b = new FakePeer("spare-b");
        plaza.connect(b, new PlazaService.Profile(902L, "닉902", "끄적", 3, "BABY", null, "pub902", null), null, "https://cdn.example/original.png");
        plaza.onMessage(b, "{\"t\":\"hello\"}");
        assertThat(b.last("room").path("members").get(1).path("image").asText()).isEqualTo("https://cdn.example/original.png");

        // 서버가 줄인 그림이 있으면 앱이 무엇을 보내든 그것을 쓴다
        FakePeer c = new FakePeer("spare-c");
        plaza.connect(c, new PlazaService.Profile(903L, "닉903", "끄적", 3, "BABY", "data:image/png;base64,SERVER", "pub903", null), null, null);
        plaza.onMessage(c, "{\"t\":\"hello\",\"image\":\"data:image/png;base64,AAAA\"}");
        assertThat(c.last("room").path("members").get(2).path("image").asText()).isEqualTo("data:image/png;base64,SERVER");
    }

    @Test
    void 앱이_빈_그림을_보내면_쓰지_않는다() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(160, 160, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", out);
        String blank = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());

        // 서버에 그림이 없고 앱이 빈 그림을 보냈다: 이름표만 떠다니게 들이지 않는다
        FakePeer a = new FakePeer("blank-a");
        plaza.connect(a, new PlazaService.Profile(911L, "닉911", "끄적", 3, "BABY", null, "pub911", null), null);
        plaza.onMessage(a, "{\"t\":\"hello\",\"image\":\"" + blank + "\"}");
        assertThat(a.last("error")).isNotNull();
        assertThat(a.last("room")).isNull();

        // 원본 주소가 있으면 그것을 쓴다
        FakePeer b = new FakePeer("blank-b");
        plaza.connect(b, new PlazaService.Profile(912L, "닉912", "끄적", 3, "BABY", null, "pub912", null), null, "https://cdn.example/original.png");
        plaza.onMessage(b, "{\"t\":\"hello\",\"image\":\"" + blank + "\"}");
        assertThat(b.last("room").path("members").get(0).path("image").asText()).isEqualTo("https://cdn.example/original.png");
    }
}
