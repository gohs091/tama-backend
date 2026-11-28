package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.service.plaza.PlazaService.HuntResult;
import com.tamagochi.tamagochi.service.plaza.PlazaServiceTest.FakePeer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** 광장 방문 기록: 방에 들어왔던 사람이 나갈 때 한 줄 (머문 시간 · 방 종류 · 잡은 마릿수 · 말한 횟수) */
class PlazaVisitTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final PlazaService plaza = new PlazaService(now::get);
    private final List<PlazaMobs.Spawn> spawns = PlazaMobs.load();
    private long seq = 0;

    private record Visit(long userId, String kind, long enteredAt, long leftAt, int kills, int chats) {}
    private final List<Visit> visits = new ArrayList<>();

    {
        plaza.setVisits((userId, kind, enteredAt, leftAt, kills, chats) -> visits.add(new Visit(userId, kind, enteredAt, leftAt, kills, chats)));
        plaza.setHunts((userId, enemy, tick, done) -> done.accept(new HuntResult(true, null, 2, 1, 7, "CHILD", false, null, 9, 9)));
    }

    private FakePeer connect() {
        long n = ++seq;
        FakePeer p = new FakePeer("p" + n);
        plaza.connect(p, new PlazaService.Profile(n, "닉" + n, "끄적" + n, 0, "CHILD", "https://cdn.example/pet" + n + ".png", "pub" + n, null),
                null, null, new PlazaService.Extras(null, "CODE000" + n, true, null, Set.of(), 0));
        return p;
    }

    private FakePeer enter(boolean v3) {
        FakePeer p = connect();
        plaza.onMessage(p, v3 ? "{\"t\":\"hello\",\"facing\":\"left\",\"v\":3}" : "{\"t\":\"hello\",\"facing\":\"left\"}");
        return p;
    }

    private long userId(FakePeer p) { return Long.parseLong(p.id.substring(1)); }

    /** i 번 몬스터 자리로 가서 죽을 때까지 때린다 (레벨 0: 한 번에 피해 1) */
    private void kill(FakePeer p, int i) {
        now.addAndGet(200);
        plaza.onMessage(p, "{\"t\":\"move\",\"x\":" + spawns.get(i).x() + ",\"y\":" + spawns.get(i).y() + "}");
        int hp = PlazaMobs.kind(spawns.get(i).enemy()).hp();
        for (int n = 0; n < hp; n++) {
            now.addAndGet(500);
            plaza.onMessage(p, "{\"t\":\"atk\",\"d\":\"r\",\"m\":[" + i + "]}");
        }
    }

    private void say(FakePeer p, String text) {
        now.addAndGet(1600);
        plaza.onMessage(p, "{\"t\":\"chat\",\"text\":\"" + text + "\"}");
    }

    @Test
    void 한_마리도_안_잡고_나가도_방문이_남는다() {
        FakePeer p = enter(true);
        long entered = now.get();
        now.addAndGet(30_000);

        plaza.onMessage(p, "{\"t\":\"leave\"}");

        assertThat(visits).containsExactly(new Visit(userId(p), "3d", entered, entered + 30_000, 0, 0));
    }

    @Test
    void 잡은_마릿수와_말한_횟수가_남는다() {
        FakePeer p = enter(true);
        kill(p, 0);
        kill(p, 1);
        say(p, "안녕");
        say(p, "반가워");
        say(p, "또 봐");

        plaza.disconnect(p);

        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).kills()).isEqualTo(2);
        assertThat(visits.get(0).chats()).isEqualTo(3);
        assertThat(visits.get(0).kind()).isEqualTo("3d");
    }

    @Test
    void 막힌_말은_세지_않는다() {
        FakePeer p = enter(true);
        say(p, "안녕");
        now.addAndGet(100);
        plaza.onMessage(p, "{\"t\":\"chat\",\"text\":\"너무 빨리 또\"}");   // 1.5초 안: 막힌다
        assertThat(p.last("chatNo").path("why").asText()).isEqualTo("fast");

        plaza.disconnect(p);

        assertThat(visits.get(0).chats()).isEqualTo(1);
    }

    @Test
    void 이디_방의_방문은_2d_로_남는다() {
        FakePeer p = enter(false);

        plaza.disconnect(p);

        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).kind()).isEqualTo("2d");
        assertThat(visits.get(0).kills()).isZero();
        assertThat(visits.get(0).chats()).isZero();
    }

    @Test
    void 연결만_하고_방에_들어오지_않았으면_남기지_않는다() {
        FakePeer p = connect();   // hello 를 보내지 않았다

        plaza.disconnect(p);

        assertThat(visits).isEmpty();
    }

    @Test
    void 같은_사람이_다시_연결해_밀려난_연결도_한_번의_방문이다() {
        FakePeer first = enter(true);
        long n = userId(first);
        FakePeer second = new FakePeer("again");
        plaza.connect(second, new PlazaService.Profile(n, "닉", "끄적", 0, "CHILD", "https://cdn.example/pet.png", "pub" + n, null),
                null, null, new PlazaService.Extras(null, null, true, null, Set.of(), 0));

        assertThat(first.closed).isTrue();
        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).userId()).isEqualTo(n);

        plaza.disconnect(first);   // 소켓이 닫히며 한 번 더 불려도 또 남지 않는다
        assertThat(visits).hasSize(1);
    }

    @Test
    void 기록이_실패해도_나가기는_그대로_된다() {
        plaza.setVisits((userId, kind, enteredAt, leftAt, kills, chats) -> { throw new IllegalStateException("기록 실패"); });
        FakePeer a = enter(true), b = enter(true);
        String aId = a.last("room").path("me").asText();

        plaza.onMessage(a, "{\"t\":\"leave\"}");

        assertThat(a.closed).isTrue();
        assertThat(b.last("leave").path("id").asText()).isEqualTo(aId);   // 남은 사람은 나갔다는 알림을 받는다
        // 나간 자리가 비어 같은 사람이 다시 들어올 수 있다
        FakePeer again = new FakePeer("again");
        plaza.connect(again, new PlazaService.Profile(userId(a), "닉", "끄적", 0, "CHILD", "https://cdn.example/pet.png", "pubA", null), null);
        plaza.onMessage(again, "{\"t\":\"hello\",\"v\":3}");
        assertThat(again.last("room")).isNotNull();
    }
}
