package com.tamagochi.tamagochi.service.plaza;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.service.plaza.PlazaService.HuntResult;
import com.tamagochi.tamagochi.service.plaza.PlazaServiceTest.FakePeer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class PlazaHuntTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final PlazaService plaza = new PlazaService(now::get);
    private final List<PlazaMobs.Spawn> spawns = PlazaMobs.load();
    private long seq = 0;

    /** 가짜 지급: 부른 것을 적어 두고 바로 결과를 돌려준다 */
    private record Kill(long userId, String enemy, boolean questTick) {}
    private final List<Kill> kills = new ArrayList<>();
    private HuntResult result = new HuntResult(true, null, 2, 1, 7, "CHILD", false, null, 9, 9);

    {
        plaza.setHunts((userId, enemy, tick, done) -> {
            kills.add(new Kill(userId, enemy, tick));
            done.accept(result);
        });
    }

    private FakePeer enter(int level, boolean huntOk, boolean v3) {
        long n = ++seq;
        FakePeer p = new FakePeer("p" + n);
        plaza.connect(p, new PlazaService.Profile(n, "닉" + n, "끄적" + n, level, "CHILD", "https://cdn.example/pet" + n + ".png", "pub" + n, null),
                null, null, new PlazaService.Extras(null, null, huntOk, huntOk ? null : "hungry", Set.of(), 0));
        plaza.onMessage(p, v3 ? "{\"t\":\"hello\",\"facing\":\"left\",\"v\":3}" : "{\"t\":\"hello\",\"facing\":\"left\"}");
        return p;
    }

    private FakePeer enter3(int level) { return enter(level, true, true); }

    private long userId(FakePeer p) { return Long.parseLong(p.id.substring(1)); }

    private String meId(FakePeer p) { return p.last("room").path("me").asText(); }

    /** n 번 몬스터 자리로 걸어간다 */
    private void goTo(FakePeer p, int i) {
        now.addAndGet(200);
        plaza.onMessage(p, "{\"t\":\"move\",\"x\":" + spawns.get(i).x() + ",\"y\":" + spawns.get(i).y() + "}");
    }

    /** 간격(450ms)을 넘겨 휘두른다 */
    private void atk(FakePeer p, String d, int... mobs) {
        now.addAndGet(500);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mobs.length; i++) sb.append(i == 0 ? "" : ",").append(mobs[i]);
        plaza.onMessage(p, "{\"t\":\"atk\",\"d\":\"" + d + "\",\"m\":[" + sb + "]}");
    }

    private List<JsonNode> mobMsgs(FakePeer p) { return p.got.stream().filter(n -> n.path("t").asText().equals("mob")).toList(); }

    @Test
    void 삼디_방에_들어오면_몬스터_스물넷을_받는다() {
        FakePeer p = enter3(3);
        JsonNode mobs = p.last("room").path("mobs");
        assertThat(mobs).hasSize(24);
        for (int i = 0; i < 24; i++) {
            assertThat(mobs.get(i).path("i").asInt()).isEqualTo(i);
            assertThat(mobs.get(i).path("e").asText()).isEqualTo(spawns.get(i).enemy());
            assertThat(mobs.get(i).path("hp").asInt()).isEqualTo(mobs.get(i).path("max").asInt());
            assertThat(mobs.get(i).has("at")).isFalse();
        }
        assertThat(p.last("room").path("hunt").path("ok").asBoolean()).isTrue();
        FakePeer two = enter(3, true, false);
        assertThat(two.last("room").has("mobs")).isFalse();
        assertThat(two.last("room").has("hunt")).isFalse();
    }

    @Test
    void 사냥할_수_없는_사람은_입장_때_이유를_받는다() {
        FakePeer p = enter(3, false, true);
        assertThat(p.last("room").path("hunt").path("ok").asBoolean()).isFalse();
        assertThat(p.last("room").path("hunt").path("why").asText()).isEqualTo("hungry");
    }

    @Test
    void 가까이서_때리면_체력이_깎이고_방_모두가_안다() {
        FakePeer a = enter3(0), b = enter3(0);   // 레벨 0: 한 번에 피해 1
        goTo(a, 0);
        atk(a, "r", 0);
        for (FakePeer p : List.of(a, b)) {
            JsonNode mob = p.last("mob");
            assertThat(mob.path("i").asInt()).isEqualTo(0);
            assertThat(mob.path("hp").asInt()).isEqualTo(2);
            assertThat(mob.path("by").asText()).isEqualTo(meId(a));
        }
    }

    @Test
    void 멀리서_때리면_아무_일도_없다() {
        FakePeer a = enter3(0);
        atk(a, "r", 0);
        assertThat(mobMsgs(a)).isEmpty();
    }

    @Test
    void 사백오십_밀리초_안에_또_때리면_무시한다() {
        FakePeer a = enter3(0);
        goTo(a, 0);
        atk(a, "r", 0);
        now.addAndGet(300);
        plaza.onMessage(a, "{\"t\":\"atk\",\"d\":\"r\",\"m\":[0]}");
        assertThat(mobMsgs(a)).hasSize(1);
        now.addAndGet(150);
        plaza.onMessage(a, "{\"t\":\"atk\",\"d\":\"r\",\"m\":[0]}");
        assertThat(mobMsgs(a)).hasSize(2);
    }

    @Test
    void 한_번에_네_마리까지만() {
        // 한 자리에 여섯 마리를 겹쳐 둔 방 (실제 자리는 서로 5 이상 떨어져 있어서 이렇게 만든다)
        List<PlazaMobs.Spawn> pile = new ArrayList<>();
        for (int i = 0; i < 6; i++) pile.add(new PlazaMobs.Spawn(500, 500, "ns-rabbit"));
        PlazaService crowd = new PlazaService(now::get, PlazaService.EventTimes.HOURLY, pile);
        crowd.setHunts((userId, enemy, tick, done) -> done.accept(result));
        FakePeer a = new FakePeer("p900");
        crowd.connect(a, new PlazaService.Profile(900L, "닉", "끄적", 0, "CHILD", "https://cdn.example/pet.png", "pub900", null),
                null, null, new PlazaService.Extras(null, null, true, null, Set.of(), 0));
        crowd.onMessage(a, "{\"t\":\"hello\",\"v\":3}");
        now.addAndGet(200);
        crowd.onMessage(a, "{\"t\":\"move\",\"x\":500,\"y\":500}");
        now.addAndGet(500);
        crowd.onMessage(a, "{\"t\":\"atk\",\"d\":\"r\",\"m\":[0,1,2,3,4,5]}");
        assertThat(mobMsgs(a)).hasSize(4);
        assertThat(mobMsgs(a)).extracting(n -> n.path("i").asInt()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void 같은_번호를_여럿_보내도_한_번만_맞는다() {
        FakePeer a = enter3(0);
        goTo(a, 0);
        atk(a, "r", 0, 0, 0, 0);
        assertThat(mobMsgs(a)).hasSize(1);
        assertThat(a.last("mob").path("hp").asInt()).isEqualTo(2);
    }

    @Test
    void 알은_못_때린다() {
        long n = ++seq;
        FakePeer egg = new FakePeer("p" + n);
        plaza.connect(egg, new PlazaService.Profile(n, "닉" + n, "알", 1, "EGG", "https://cdn.example/egg.png", "pub" + n, null),
                null, null, new PlazaService.Extras(null, null, true, null, Set.of(), 0));
        plaza.onMessage(egg, "{\"t\":\"hello\",\"v\":3}");
        FakePeer other = enter3(0);
        goTo(egg, 0);
        atk(egg, "r", 0);
        assertThat(mobMsgs(egg)).isEmpty();
        assertThat(other.last("atk")).isNull();
    }

    @Test
    void 사냥할_수_없는_사람은_못_때린다() {
        FakePeer a = enter(0, false, true);
        FakePeer other = enter3(0);
        goTo(a, 0);
        atk(a, "r", 0);
        assertThat(mobMsgs(a)).isEmpty();
        assertThat(other.last("atk").path("d").asText()).isEqualTo("r");   // 휘두르는 모습은 보인다
    }

    @Test
    void 체력이_다하면_죽고_때린_사람_모두가_한_마리_몫을_받는다() {
        FakePeer a = enter3(0), b = enter3(0);
        goTo(a, 0);
        goTo(b, 0);
        atk(a, "r", 0);
        atk(a, "r", 0);
        atk(b, "r", 0);   // 3 번째 피해로 죽는다
        long deadAt = now.get() + 6000;
        for (FakePeer p : List.of(a, b)) {
            List<JsonNode> dead = mobMsgs(p).stream().filter(n -> n.path("hp").asInt() == 0).toList();
            assertThat(dead).hasSize(1);
            assertThat(dead.get(0).path("at").asLong()).isEqualTo(deadAt);
            assertThat(dead.get(0).path("by").asText()).isEqualTo(meId(b));
            assertThat(p.count("loot")).isEqualTo(1);
        }
        assertThat(kills).extracting(Kill::userId).containsExactlyInAnyOrder(userId(a), userId(b));
        assertThat(kills).extracting(Kill::enemy).containsOnly("ns-rabbit");
        JsonNode loot = a.last("loot");
        assertThat(loot.path("i").asInt()).isEqualTo(0);
        assertThat(loot.path("exp").asInt()).isEqualTo(2);
        assertThat(loot.path("ink").asInt()).isEqualTo(1);
        assertThat(loot.path("level").asInt()).isEqualTo(7);
        assertThat(loot.path("stage").asText()).isEqualTo("CHILD");
        assertThat(loot.path("lvUp").asBoolean()).isFalse();
        assertThat(loot.path("hunger").asInt()).isEqualTo(9);
        assertThat(loot.path("power").asInt()).isEqualTo(9);
        assertThat(loot.path("ok").asBoolean()).isTrue();
        assertThat(loot.has("drop")).isFalse();
    }

    @Test
    void 죽은_몬스터는_못_때리고_육_초_뒤_다시_나온다() {
        FakePeer a = enter3(20);   // 한 방
        goTo(a, 0);
        atk(a, "r", 0);
        long deadAt = now.get() + 6000;
        assertThat(mobMsgs(a)).hasSize(1);
        atk(a, "r", 0);
        assertThat(mobMsgs(a)).hasSize(1);   // 죽은 것은 맞지 않는다
        // 6초가 되기 전엔 그대로
        now.set(deadAt - 100);
        plaza.tick();
        assertThat(mobMsgs(a).stream().filter(n -> n.has("e"))).isEmpty();
        now.set(deadAt);
        plaza.tick();
        JsonNode back = a.last("mob");
        assertThat(back.path("i").asInt()).isEqualTo(0);
        assertThat(back.path("hp").asInt()).isEqualTo(3);
        assertThat(back.path("max").asInt()).isEqualTo(3);
        assertThat(back.path("e").asText()).isEqualTo("ns-rabbit");
        // 다시 나온 뒤엔 새로 때릴 수 있고, 한 번 더 tick 해도 또 알리지 않는다
        plaza.tick();
        assertThat(mobMsgs(a)).hasSize(2);
        atk(a, "r", 0);
        assertThat(mobMsgs(a)).hasSize(3);
    }

    @Test
    void 때린_뒤_나간_사람은_몫을_받지_않는다() {
        FakePeer a = enter3(0), b = enter3(0);
        goTo(a, 0);
        goTo(b, 0);
        atk(a, "r", 0);
        plaza.onMessage(a, "{\"t\":\"leave\"}");
        atk(b, "r", 0);
        atk(b, "r", 0);
        assertThat(kills).extracting(Kill::userId).containsExactly(userId(b));
    }

    @Test
    void 열_마리째에_퀘스트를_센다() {
        result = new HuntResult(true, null, 2, 1, 30, "CHILD", false, null, 9, 9);   // 결과 레벨이 그대로 30 이어야 계속 한 방
        FakePeer a = enter3(30);   // 앞 열 곳은 모두 한 방
        for (int i = 0; i < 10; i++) {
            goTo(a, i);
            atk(a, "r", i);
        }
        assertThat(kills).hasSize(10);
        assertThat(kills).extracting(Kill::questTick).containsExactly(false, false, false, false, false, false, false, false, false, true);
    }

    @Test
    void 지급_결과가_못_한다면_그_사람은_더_못_때린다() {
        result = new HuntResult(false, "hungry", 0, 0, 0, null, false, null, 0, 0);
        FakePeer a = enter3(20);
        goTo(a, 0);
        atk(a, "r", 0);
        assertThat(a.last("loot").path("ok").asBoolean()).isFalse();
        assertThat(a.last("loot").path("why").asText()).isEqualTo("hungry");
        int before = mobMsgs(a).size();
        goTo(a, 1);
        atk(a, "r", 1);
        assertThat(mobMsgs(a)).hasSize(before);
    }

    @Test
    void 나간_사람의_지급_결과는_버린다() {
        plaza.onLoot("없는id", 0, result);
        FakePeer a = enter3(3);
        String id = meId(a);
        plaza.onMessage(a, "{\"t\":\"leave\"}");
        plaza.onLoot(id, 0, result);
        assertThat(a.count("loot")).isZero();
    }

    @Test
    void 지급_결과의_레벨이_그_사람의_레벨이_된다() {
        FakePeer a = enter3(20);
        FakePeer b = enter3(3);
        goTo(a, 0);
        atk(a, "r", 0);   // 결과 레벨 7
        FakePeer c = enter3(3);
        String aId = meId(a);
        JsonNode aJson = null;
        for (JsonNode m : c.last("room").path("members")) if (m.path("id").asText().equals(aId)) aJson = m;
        assertThat(aJson.path("level").asInt()).isEqualTo(7);
    }

    @Test
    void 휘두르면_남들에게_보인다() {
        FakePeer a = enter3(3), b = enter3(3);
        now.addAndGet(500);
        plaza.onMessage(a, "{\"t\":\"atk\",\"d\":\"u\",\"m\":[]}");
        assertThat(b.last("atk").path("id").asText()).isEqualTo(meId(a));
        assertThat(b.last("atk").path("d").asText()).isEqualTo("u");
        assertThat(a.last("atk")).isNull();
        // 방향이 아닌 값은 보내지 않는다
        plaza.onMessage(a, "{\"t\":\"atk\",\"d\":\"x\",\"m\":[]}");
        now.addAndGet(500);
        plaza.onMessage(a, "{\"t\":\"atk\",\"d\":\"x\",\"m\":[]}");
        assertThat(b.count("atk")).isEqualTo(1);
    }

    @Test
    void 점프도_남들에게_보인다() {
        FakePeer a = enter3(3), b = enter3(3);
        now.addAndGet(600);
        plaza.onMessage(a, "{\"t\":\"jump\"}");
        assertThat(b.last("jump").path("id").asText()).isEqualTo(meId(a));
        assertThat(a.last("jump")).isNull();
        now.addAndGet(300);
        plaza.onMessage(a, "{\"t\":\"jump\"}");   // 500ms 안
        assertThat(b.count("jump")).isEqualTo(1);
    }

    @Test
    void 이디_방에서_오는_atk과_jump는_무시한다() {
        FakePeer a = enter(3, true, false), b = enter(3, true, false);
        now.addAndGet(600);
        plaza.onMessage(a, "{\"t\":\"atk\",\"d\":\"u\",\"m\":[0]}");
        plaza.onMessage(a, "{\"t\":\"jump\"}");
        assertThat(b.last("atk")).isNull();
        assertThat(b.last("jump")).isNull();
    }

    @Test
    void 자는_중에_때리면_깬다() {
        FakePeer a = enter3(3), b = enter3(3);
        now.addAndGet(600);
        plaza.onMessage(a, "{\"t\":\"sleep\",\"on\":true}");
        now.addAndGet(600);
        plaza.onMessage(a, "{\"t\":\"atk\",\"d\":\"l\",\"m\":[]}");
        FakePeer c = enter3(3);
        String aId = meId(a);
        for (JsonNode m : c.last("room").path("members")) if (m.path("id").asText().equals(aId)) assertThat(m.has("sleep")).isFalse();
    }
}
