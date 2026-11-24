package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.service.plaza.PlazaServiceTest.FakePeer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PlazaRoomKindTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final PlazaService plaza = new PlazaService(now::get);
    private long seq = 0;

    /** connect 만 한다 (hello 는 따로 보낸다) */
    private FakePeer connect(String inviteKey) {
        long n = ++seq;
        FakePeer p = new FakePeer("p" + n);
        plaza.connect(p, new PlazaService.Profile(n, "닉" + n, "끄적" + n, 3, "CHILD", "https://cdn.example/pet" + n + ".png", "pub" + n, null), inviteKey);
        return p;
    }

    private void hello(FakePeer p, boolean v3) {
        plaza.onMessage(p, v3 ? "{\"t\":\"hello\",\"facing\":\"left\",\"v\":3}" : "{\"t\":\"hello\",\"facing\":\"left\"}");
    }

    private FakePeer enter(String inviteKey, boolean v3) {
        FakePeer p = connect(inviteKey);
        hello(p, v3);
        return p;
    }

    @Test
    void 삼디로_들어온_사람은_이디_방과_섞이지_않는다() {
        FakePeer a = enter(null, false), b = enter(null, true);
        assertThat(b.last("room").path("no").asInt()).isNotEqualTo(a.last("room").path("no").asInt());
        assertThat(b.last("room").path("v").asInt()).isEqualTo(3);
        assertThat(b.last("room").path("now").asLong()).isEqualTo(now.get());
        assertThat(b.last("room").path("chat").asBoolean()).isTrue();
        assertThat(a.last("room").has("v")).isFalse();
        assertThat(a.last("room").has("now")).isFalse();
        assertThat(a.last("room").has("chat")).isFalse();
        assertThat(a.last("enter")).isNull();   // 서로의 입장이 건너가지 않는다
        assertThat(plaza.roomCount()).isEqualTo(2);
    }

    @Test
    void 같은_종류끼리는_같은_방에_들어간다() {
        FakePeer a = enter(null, true), b = enter(null, true);
        assertThat(b.last("room").path("key").asText()).isEqualTo(a.last("room").path("key").asText());
        assertThat(b.last("room").path("members")).hasSize(2);
        assertThat(plaza.roomCount()).isEqualTo(1);
    }

    @Test
    void 초대_열쇠의_방이_다른_종류면_자기_종류의_방으로_간다() {
        FakePeer a = enter(null, false);
        String key = a.last("room").path("key").asText();
        FakePeer b = enter(key, true);
        assertThat(b.last("room").path("key").asText()).isNotEqualTo(key);
        // 반대로 삼디 방의 열쇠로 이디 사람이 오면 이디 방으로 간다
        FakePeer c = enter(b.last("room").path("key").asText(), false);
        assertThat(c.last("room").path("key").asText()).isEqualTo(key);
    }

    @Test
    void 초대_열쇠의_방이_같은_종류면_그_방으로_간다() {
        FakePeer a = enter(null, true);
        enter(null, false);
        FakePeer c = enter(a.last("room").path("key").asText(), true);
        assertThat(c.last("room").path("key").asText()).isEqualTo(a.last("room").path("key").asText());
    }

    @Test
    void 삼디_방의_시작_자리는_서쪽_큰길_위다() {
        for (int i = 0; i < 30; i++) {
            FakePeer p = enter(null, true);
            String me = p.last("room").path("me").asText();
            int x = 0, y = 0;
            for (var m : p.last("room").path("members")) if (m.path("id").asText().equals(me)) { x = m.path("x").asInt(); y = m.path("y").asInt(); }
            assertThat(x).isBetween(167, 257);
            assertThat(y).isBetween(132, 217);
        }
    }

    @Test
    void 삼디는_이디_방에_잡아_둔_자리와_상관없이_삼디_방에_들어간다() {
        List<FakePeer> held = new ArrayList<>();
        for (int i = 0; i < PlazaService.CAP + 5; i++) held.add(connect(null));   // hello 안 보낸 2D 연결들이 자리를 잡아 둔다
        FakePeer three = enter(null, true);
        assertThat(three.last("room").path("v").asInt()).isEqualTo(3);
        assertThat(three.last("room").path("members")).hasSize(1);
        // 그 2D 연결들은 나중에 hello 를 보내면 원래대로 2D 방에 들어간다
        for (FakePeer p : held) hello(p, false);
        for (FakePeer p : held) {
            assertThat(p.last("room").has("v")).isFalse();
            assertThat(p.last("room").path("no").asInt()).isNotEqualTo(three.last("room").path("no").asInt());
        }
        assertThat(held.get(0).last("room").path("no").asInt()).isEqualTo(1);
    }

    @Test
    void 삼디가_놓은_이디_자리의_방이_비면_사라진다() {
        FakePeer three = connect(null);
        hello(three, true);
        assertThat(plaza.roomCount()).isEqualTo(1);   // 2D 방은 사라지고 3D 방만 남는다
        plaza.onMessage(three, "{\"t\":\"leave\"}");
        assertThat(plaza.roomCount()).isEqualTo(0);
    }

    private static final String DOT64 = "data:image/png;base64,AAA";

    private FakePeer connectWith(PlazaService.Extras extras) {
        long n = ++seq;
        FakePeer p = new FakePeer("p" + n);
        plaza.connect(p, new PlazaService.Profile(n, "닉" + n, "끄적" + n, 3, "CHILD", "https://cdn.example/pet" + n + ".png", "pub" + n, null),
                null, null, extras);
        return p;
    }

    private com.fasterxml.jackson.databind.JsonNode me(FakePeer p) {
        String me = p.last("room").path("me").asText();
        for (var m : p.last("room").path("members")) if (m.path("id").asText().equals(me)) return m;
        throw new AssertionError("내가 없다");
    }

    private static PlazaService.Extras extras(String image64, String userCode) {
        return new PlazaService.Extras(image64, userCode, false, null, java.util.Set.of(), 0);
    }

    @Test
    void 삼디_방에는_64칸_그림과_유저_코드가_간다() {
        var e = extras(DOT64, "ABCD2345");
        FakePeer three = connectWith(e);
        hello(three, true);
        assertThat(me(three).path("image").asText()).isEqualTo(DOT64);
        assertThat(me(three).path("u").asText()).isEqualTo("ABCD2345");

        FakePeer two = connectWith(e);
        hello(two, false);
        assertThat(me(two).path("image").asText()).startsWith("https://cdn.example/pet");
        assertThat(me(two).has("u")).isFalse();
    }

    @Test
    void 삼디인데_64칸_그림이_없으면_앱이_보낸_그림을_쓴다() {
        FakePeer p = connectWith(extras(null, "ABCD2345"));
        plaza.onMessage(p, "{\"t\":\"hello\",\"v\":3,\"image\":\"data:image/png;base64,APPIMG\"}");
        assertThat(me(p).path("image").asText()).isEqualTo("data:image/png;base64,APPIMG");
    }

    @Test
    void 유저_코드가_없으면_u를_생략한다() {
        FakePeer p = connectWith(extras(DOT64, null));
        hello(p, true);
        assertThat(me(p).has("u")).isFalse();
    }

    @Test
    void 다녀간_끄적이도_삼디_방에서는_64칸_그림과_유저_코드를_준다() {
        FakePeer gone = connectWith(extras(DOT64, "ABCD2345"));
        hello(gone, true);
        plaza.onMessage(gone, "{\"t\":\"leave\"}");
        FakePeer viewer = enter(null, true);
        var ghosts = viewer.last("room").path("ghosts");
        assertThat(ghosts).hasSize(1);
        assertThat(ghosts.get(0).path("image").asText()).isEqualTo(DOT64);
        assertThat(ghosts.get(0).path("u").asText()).isEqualTo("ABCD2345");
        FakePeer old = enter(null, false);
        assertThat(old.last("room").path("ghosts").get(0).has("u")).isFalse();
    }

    @Test
    void 삼디에서_나간_끄적이는_이디_방에는_예전_그림으로_간다() {
        FakePeer gone = connectWith(extras(DOT64, "ABCD2345"));
        hello(gone, true);
        plaza.onMessage(gone, "{\"t\":\"leave\"}");

        FakePeer two = enter(null, false);
        var ghost = two.last("room").path("ghosts").get(0);
        assertThat(ghost.path("image").asText()).startsWith("https://cdn.example/pet");
        assertThat(ghost.has("u")).isFalse();

        FakePeer three = enter(null, true);
        var ghost3 = three.last("room").path("ghosts").get(0);
        assertThat(ghost3.path("image").asText()).isEqualTo(DOT64);
        assertThat(ghost3.path("u").asText()).isEqualTo("ABCD2345");
    }
}
