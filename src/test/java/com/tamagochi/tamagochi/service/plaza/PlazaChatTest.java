package com.tamagochi.tamagochi.service.plaza;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.service.plaza.PlazaServiceTest.FakePeer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** 3D 방의 채팅 · 차단 · 신고. 2D 방은 이 메시지를 받지 않는다 */
class PlazaChatTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final PlazaService plaza = new PlazaService(now::get);
    private long seq = 0;

    private record Block(long userId, String target, boolean on) {}
    private record Report(long reporter, String target, String why, List<String> lines, String roomKey) {}
    private final List<Block> blocks = new ArrayList<>();
    private final List<Report> reports = new ArrayList<>();

    {
        plaza.setModeration(new PlazaService.Moderation() {
            @Override public void block(long userId, String targetCode, boolean on) { blocks.add(new Block(userId, targetCode, on)); }
            @Override public void report(long reporterId, String targetCode, String why, List<String> lines, String roomKey) {
                reports.add(new Report(reporterId, targetCode, why, lines, roomKey));
            }
        });
    }

    private FakePeer enter(boolean v3, String stage, Set<String> blocked, long mutedUntil) {
        long n = ++seq;
        FakePeer p = new FakePeer("p" + n);
        plaza.connect(p, new PlazaService.Profile(n, "닉" + n, "끄적" + n, 3, stage, "https://cdn.example/pet" + n + ".png", "pub" + n, null),
                null, null, new PlazaService.Extras(null, "CODE000" + n, true, null, blocked, mutedUntil));
        plaza.onMessage(p, v3 ? "{\"t\":\"hello\",\"facing\":\"left\",\"v\":3}" : "{\"t\":\"hello\",\"facing\":\"left\"}");
        return p;
    }

    private FakePeer enter3() { return enter(true, "CHILD", Set.of(), 0); }

    private String code(FakePeer p) { return "CODE000" + p.id.substring(1); }

    private String meId(FakePeer p) { return p.last("room").path("me").asText(); }

    /** 간격(1.5초)을 넘겨 말한다 */
    private void say(FakePeer p, String text) {
        now.addAndGet(1600);
        plaza.onMessage(p, "{\"t\":\"chat\",\"text\":\"" + text + "\"}");
    }

    private long chats(FakePeer p) { return p.count("chat"); }

    @Test
    void 말하면_방_모두가_받는다() {
        FakePeer a = enter3(), b = enter3();
        say(a, "안녕");
        for (FakePeer p : List.of(a, b)) {
            JsonNode c = p.last("chat");
            assertThat(c.path("text").asText()).isEqualTo("안녕");
            assertThat(c.path("id").asText()).isEqualTo(meId(a));
            assertThat(c.path("u").asText()).isEqualTo(code(a));
        }
    }

    @Test
    void 이디_방에서는_말할_수_없다() {
        FakePeer two = enter(false, "CHILD", Set.of(), 0);
        FakePeer two2 = enter(false, "CHILD", Set.of(), 0);
        int before = two.got.size(), before2 = two2.got.size();
        say(two, "안녕");
        plaza.onMessage(two, "{\"t\":\"block\",\"u\":\"" + code(two2) + "\",\"on\":true}");
        plaza.onMessage(two, "{\"t\":\"report\",\"u\":\"" + code(two2) + "\",\"why\":\"chat\"}");
        assertThat(two.got).hasSize(before);
        assertThat(two2.got).hasSize(before2);
        assertThat(blocks).isEmpty();
        assertThat(reports).isEmpty();
        assertThat(two.last("room").has("chat")).isFalse();
        assertThat(two.last("room").has("blocked")).isFalse();
    }

    @Test
    void 알은_말할_수_없다() {
        FakePeer egg = enter(true, "EGG", Set.of(), 0);
        FakePeer b = enter3();
        say(egg, "안녕");
        assertThat(chats(egg)).isZero();
        assertThat(chats(b)).isZero();
    }

    @Test
    void 천오백_밀리초_안에_또_말하면_fast() {
        FakePeer a = enter3(), b = enter3();
        say(a, "하나");
        now.addAndGet(1000);
        plaza.onMessage(a, "{\"t\":\"chat\",\"text\":\"둘\"}");
        assertThat(a.last("chatNo").path("why").asText()).isEqualTo("fast");
        assertThat(chats(b)).isEqualTo(1);
        assertThat(b.count("chatNo")).isZero();
    }

    @Test
    void 십_초에_여섯_번째는_fast() {
        FakePeer a = enter3(), b = enter3();
        now.addAndGet(5000);
        for (int i = 0; i < 5; i++) { plaza.onMessage(a, "{\"t\":\"chat\",\"text\":\"말" + i + "\"}"); now.addAndGet(1600); }
        assertThat(chats(b)).isEqualTo(5);
        plaza.onMessage(a, "{\"t\":\"chat\",\"text\":\"여섯\"}");
        assertThat(a.last("chatNo").path("why").asText()).isEqualTo("fast");
        assertThat(chats(b)).isEqualTo(5);
        now.addAndGet(5000);   // 열 초가 지나면 다시 말할 수 있다
        plaza.onMessage(a, "{\"t\":\"chat\",\"text\":\"일곱\"}");
        assertThat(chats(b)).isEqualTo(6);
    }

    @Test
    void 금칙어는_방에_안_가고_word() {
        FakePeer a = enter3(), b = enter3();
        say(a, "너 미친놈이야");
        assertThat(a.last("chatNo").path("why").asText()).isEqualTo("word");
        assertThat(chats(a)).isZero();
        assertThat(chats(b)).isZero();
    }

    @Test
    void 말_못_하게_된_사람은_muted() {
        FakePeer a = enter(true, "CHILD", Set.of(), now.get() + 60_000);
        FakePeer b = enter3();
        say(a, "안녕");
        assertThat(a.last("chatNo").path("why").asText()).isEqualTo("muted");
        assertThat(chats(b)).isZero();
        now.addAndGet(120_000);   // 때가 지나면 풀린다
        say(a, "안녕");
        assertThat(chats(b)).isEqualTo(1);
    }

    @Test
    void 꺼_두면_off_이고_room_chat_이_false() {
        PlazaService off = new PlazaService(now::get, PlazaService.EventTimes.HOURLY, PlazaMobs.load(), false);
        FakePeer a = new FakePeer("o1"), b = new FakePeer("o2");
        off.connect(a, new PlazaService.Profile(91L, "닉", "끄적", 3, "CHILD", "https://cdn.example/a.png", "pa", null), null, null,
                new PlazaService.Extras(null, "CODE00091", true, null, Set.of(), 0));
        off.onMessage(a, "{\"t\":\"hello\",\"v\":3}");
        off.connect(b, new PlazaService.Profile(92L, "닉2", "끄적2", 3, "CHILD", "https://cdn.example/b.png", "pb", null), null, null,
                new PlazaService.Extras(null, "CODE00092", true, null, Set.of(), 0));
        off.onMessage(b, "{\"t\":\"hello\",\"v\":3}");
        assertThat(a.last("room").path("chat").asBoolean()).isFalse();
        now.addAndGet(1600);
        off.onMessage(a, "{\"t\":\"chat\",\"text\":\"안녕\"}");
        assertThat(a.last("chatNo").path("why").asText()).isEqualTo("off");
        assertThat(b.count("chat")).isZero();
        assertThat(enter3().last("room").path("chat").asBoolean()).isTrue();
    }

    @Test
    void 차단한_사람의_말은_나에게_안_온다() {
        FakePeer a = enter3(), b = enter3(), c = enter3();
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":true}");
        say(a, "안녕");
        assertThat(chats(a)).isEqualTo(1);
        assertThat(chats(b)).isZero();
        assertThat(chats(c)).isEqualTo(1);
    }

    @Test
    void 차단한_사람이_나에게_한_인사도_안_온다() {
        FakePeer a = enter3(), b = enter3(), c = enter3();
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":true}");
        now.addAndGet(600);
        plaza.onMessage(a, "{\"t\":\"emote\",\"k\":\"hi\",\"to\":\"" + meId(b) + "\"}");
        assertThat(b.count("emote")).isZero();
        now.addAndGet(600);
        plaza.onMessage(a, "{\"t\":\"emote\",\"k\":\"hi\",\"to\":\"" + meId(c) + "\"}");
        assertThat(c.count("emote")).isEqualTo(2);   // 처음 인사(b 에게)도 방 사람인 c 는 본다
    }

    @Test
    void 차단을_풀면_다시_온다() {
        FakePeer a = enter3(), b = enter3();
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":true}");
        say(a, "하나");
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":false}");
        say(a, "둘");
        assertThat(chats(b)).isEqualTo(1);
        assertThat(b.last("chat").path("text").asText()).isEqualTo("둘");
    }

    @Test
    void 차단하면_저장을_부른다() {
        FakePeer a = enter3(), b = enter3();
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":true}");
        now.addAndGet(600);
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":false}");
        now.addAndGet(600);
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(b) + "\",\"on\":true}");   // 자기 자신은 무시
        assertThat(blocks).containsExactly(new Block(2, code(a), true), new Block(2, code(a), false));
    }

    @Test
    void 같은_상태를_다시_보내면_저장을_부르지_않는다() {
        FakePeer a = enter3(), b = enter3();
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":false}");   // 차단한 적 없는데 해제
        now.addAndGet(600);
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":true}");
        now.addAndGet(600);
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":true}");   // 이미 차단
        assertThat(blocks).containsExactly(new Block(2, code(a), true));
    }

    @Test
    void 오백_밀리초_안의_두_번째_차단은_무시한다() {
        FakePeer a = enter3(), b = enter3();
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":true}");
        now.addAndGet(400);
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":false}");
        assertThat(blocks).hasSize(1);
        say(a, "아직 차단 중");
        assertThat(chats(b)).isZero();   // 해제도 받아들여지지 않았다
        now.addAndGet(600);
        plaza.onMessage(b, "{\"t\":\"block\",\"u\":\"" + code(a) + "\",\"on\":false}");
        assertThat(blocks).hasSize(2);
    }

    @Test
    void 간격에_걸린_말은_다듬지_않고_아주_긴_글은_자른다() {
        FakePeer a = enter3();
        say(a, "하나");
        now.addAndGet(100);
        plaza.onMessage(a, "{\"t\":\"chat\",\"text\":\"둘\"}");
        assertThat(a.last("chatNo").path("why").asText()).isEqualTo("fast");
        say(a, "가".repeat(100_000));
        assertThat(a.last("chat").path("text").asText()).isEqualTo("가".repeat(30));
    }

    @Test
    void 들어올_때_차단_목록을_room_blocked_로_받는다() {
        FakePeer a = enter(true, "CHILD", Set.of("ZZZZ0001", "ZZZZ0002"), 0);
        JsonNode blocked = a.last("room").path("blocked");
        assertThat(blocked).hasSize(2);
        assertThat(blocked.get(0).asText()).isIn("ZZZZ0001", "ZZZZ0002");
    }

    @Test
    void 신고하면_그_사람의_최근_말_열_줄이_간다() {
        FakePeer a = enter3(), b = enter3();
        for (int i = 1; i <= 12; i++) {
            say(a, "말" + i);
            if (i % 5 == 0) now.addAndGet(10_000);   // 열 초에 다섯 번 제한을 넘지 않게
        }
        now.addAndGet(20_000);
        plaza.onMessage(b, "{\"t\":\"report\",\"u\":\"" + code(a) + "\",\"why\":\"chat\"}");
        assertThat(reports).hasSize(1);
        Report r = reports.get(0);
        assertThat(r.reporter()).isEqualTo(2);
        assertThat(r.target()).isEqualTo(code(a));
        assertThat(r.why()).isEqualTo("chat");
        assertThat(r.lines()).hasSize(10);
        assertThat(r.lines().get(0)).isEqualTo("말3");
        assertThat(r.lines().get(9)).isEqualTo("말12");
        assertThat(r.roomKey()).isEqualTo(b.last("room").path("key").asText());
    }

    @Test
    void 신고_이유는_셋_가운데_하나로_맞춘다() {
        FakePeer a = enter3(), b = enter3();
        plaza.onMessage(b, "{\"t\":\"report\",\"u\":\"" + code(a) + "\",\"why\":\"엉뚱\"}");
        assertThat(reports.get(0).why()).isEqualTo("etc");
    }

    @Test
    void 신고는_십_초에_한_번() {
        FakePeer a = enter3(), b = enter3();
        plaza.onMessage(b, "{\"t\":\"report\",\"u\":\"" + code(a) + "\",\"why\":\"name\"}");
        now.addAndGet(5000);
        plaza.onMessage(b, "{\"t\":\"report\",\"u\":\"" + code(a) + "\",\"why\":\"name\"}");
        assertThat(reports).hasSize(1);
        now.addAndGet(6000);
        plaza.onMessage(b, "{\"t\":\"report\",\"u\":\"" + code(a) + "\",\"why\":\"name\"}");
        assertThat(reports).hasSize(2);
        now.addAndGet(11_000);
        plaza.onMessage(b, "{\"t\":\"report\",\"u\":\"" + code(b) + "\",\"why\":\"name\"}");   // 자기 자신은 무시
        assertThat(reports).hasSize(2);
    }

    @Test
    void 말하면_잠에서_깬다() {
        FakePeer a = enter3();
        now.addAndGet(600);
        plaza.onMessage(a, "{\"t\":\"sleep\",\"on\":true}");
        FakePeer late = enter3();
        assertThat(late.last("room").path("members").get(0).path("sleep").asBoolean()).isTrue();
        say(a, "안녕");
        FakePeer later = enter3();
        assertThat(later.last("room").path("members").get(0).has("sleep")).isFalse();
    }
}
