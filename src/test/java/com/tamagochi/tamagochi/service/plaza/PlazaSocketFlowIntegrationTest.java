package com.tamagochi.tamagochi.service.plaza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 WebSocket(/ws/plaza)으로 3D 입장 → 몬스터 때리기 → 죽이면 지급 → 채팅까지 한 바퀴를 돌린다.
 * 메모리 DB, Redis 는 흉내. 2D(hello 에 v 없음) 연결의 room 에는 새 필드가 없어야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlazaSocketFlowIntegrationTest {

    @MockitoBean StringRedisTemplate redisTemplate;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PlazaModerationService moderationSpy;

    @LocalServerPort int port;
    @Autowired AuthTokenService tokens;
    @Autowired UserService userService;
    @Autowired TamagotchiRepository tamagotchiRepository;
    private final ObjectMapper om = new ObjectMapper();

    /** 받은 메시지를 쌓아 두는 연결 */
    private static final class Client implements WebSocket.Listener {
        final List<JsonNode> got = new CopyOnWriteArrayList<>();
        final ObjectMapper om = new ObjectMapper();
        final StringBuilder buf = new StringBuilder();
        WebSocket ws;

        @Override public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                try { got.add(om.readTree(buf.toString())); } catch (Exception e) { throw new RuntimeException(e); }
                buf.setLength(0);
            }
            w.request(1);
            return null;
        }

        @Override public void onOpen(WebSocket w) { w.request(1); }

        void send(String json) { ws.sendText(json, true).join(); }

        JsonNode await(String t, int millis) throws Exception {
            long end = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < end) {
                List<JsonNode> snap = new ArrayList<>(got);
                for (JsonNode n : snap) if (t.equals(n.path("t").asText())) return n;
                Thread.sleep(25);
            }
            throw new AssertionError(t + " 메시지가 오지 않음. 받은 것: " + got.stream().map(n -> n.path("t").asText()).toList());
        }
    }

    private Client connect(String token) {
        Client c = new Client();
        c.ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/plaza?token=" + token), c).join();
        return c;
    }

    private Tamagotchi newPet(String tag) {
        User user = userService.createUser("toss_sock_" + tag + "_" + System.nanoTime(), "소켓" + tag);
        return tamagotchiRepository.save(Tamagotchi.builder().user(user).name("끄적이").evolutionStage(EvolutionStage.BABY)
                .hunger(40).strength(60).build());
    }

    private String token(Tamagotchi pet) {
        return tokens.issue(pet.getUser().getTossUserId());
    }

    /** 앱이 보내는 작은 그림 대신 쓰는 한 점 찍힌 PNG */
    private static String pngData() throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) img.setRGB(x, y, 0xFF3366CC);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 삼디_입장_사냥_지급_채팅_그리고_이디_연결은_그대로() throws Exception {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        Tamagotchi pet = newPet("a");
        int hungerBefore = pet.getHunger(), strengthBefore = pet.getStrength();
        Client a = connect(token(pet));
        Client b = null, two = null;
        try {
            a.send("{\"t\":\"hello\",\"v\":3,\"image\":\"" + pngData() + "\",\"facing\":\"right\"}");
            JsonNode room = a.await("room", 5000);
            System.out.println("[흐름] room 키: " + toKeys(room) + ", mobs=" + room.path("mobs").size()
                    + ", hunt=" + room.path("hunt") + ", chat=" + room.path("chat") + ", blocked=" + room.path("blocked"));
            assertThat(room.path("v").asInt()).isEqualTo(3);
            assertThat(room.path("mobs")).hasSize(24);
            assertThat(room.path("hunt").path("ok").asBoolean()).isTrue();
            assertThat(room.path("chat").asBoolean()).isTrue();
            assertThat(room.has("blocked")).isTrue();
            assertThat(room.has("now")).isTrue();

            // 가장 약한(체력이 적은) 몬스터 자리로 가서 때린다
            List<PlazaMobs.Spawn> spawns = PlazaMobs.load();
            int target = 0;
            for (int i = 0; i < spawns.size(); i++)
                if (PlazaMobs.kind(spawns.get(i).enemy()).hp() < PlazaMobs.kind(spawns.get(target).enemy()).hp()) target = i;
            PlazaMobs.Spawn s = spawns.get(target);
            a.send("{\"t\":\"move\",\"x\":" + s.x() + ",\"y\":" + s.y() + "}");
            Thread.sleep(150);

            JsonNode loot = null;
            for (int n = 0; n < 12 && loot == null; n++) {
                a.send("{\"t\":\"atk\",\"d\":\"r\",\"m\":[" + target + "]}");
                Thread.sleep(520);
                for (JsonNode m : a.got) if ("loot".equals(m.path("t").asText())) loot = m;
            }
            if (loot == null) loot = a.await("loot", 3000);
            List<JsonNode> mobMsgs = a.got.stream().filter(m -> "mob".equals(m.path("t").asText())).toList();
            System.out.println("[흐름] mob 메시지 " + mobMsgs.size() + "개, 마지막: " + mobMsgs.get(mobMsgs.size() - 1));
            System.out.println("[흐름] loot: " + loot);
            assertThat(mobMsgs).isNotEmpty();
            assertThat(mobMsgs.get(mobMsgs.size() - 1).path("hp").asInt()).isZero();
            assertThat(loot.path("i").asInt()).isEqualTo(target);
            assertThat(loot.path("ok").asBoolean()).isTrue();
            assertThat(loot.path("exp").asInt()).isEqualTo(2);
            assertThat(loot.path("ink").asInt()).isEqualTo(1);
            assertThat(loot.path("hunger").asInt()).isEqualTo(100 - hungerBefore - 1);   // 포만감 하나 줄어듦
            assertThat(loot.path("power").asInt()).isEqualTo(strengthBefore - 1);

            Tamagotchi after = tamagotchiRepository.findActiveByUserId(pet.getUser().getId()).orElseThrow();
            assertThat(after.getHunger()).isEqualTo(hungerBefore + 1);
            assertThat(after.getStrength()).isEqualTo(strengthBefore - 1);
            assertThat(after.exp()).isEqualTo(2);

            // 채팅: 보낸 사람 포함 방 모두에게 돌아온다
            Tamagotchi pet2 = newPet("b");
            b = connect(token(pet2));
            b.send("{\"t\":\"hello\",\"v\":3,\"image\":\"" + pngData() + "\",\"facing\":\"left\"}");
            b.await("room", 5000);
            a.send("{\"t\":\"chat\",\"text\":\"안녕하세요\"}");
            JsonNode chatA = a.await("chat", 3000), chatB = b.await("chat", 3000);
            System.out.println("[흐름] chat(보낸 사람): " + chatA + " / chat(다른 사람): " + chatB);
            assertThat(chatA.path("text").asText()).isEqualTo("안녕하세요");
            assertThat(chatB.path("text").asText()).isEqualTo("안녕하세요");
            assertThat(chatB.path("id").asText()).isEqualTo(chatA.path("id").asText());

            // 2D 연결: room 에 새 필드가 없고 방도 3D 와 섞이지 않는다
            Tamagotchi pet3 = newPet("c");
            two = connect(token(pet3));
            two.send("{\"t\":\"hello\",\"image\":\"" + pngData() + "\",\"facing\":\"right\"}");
            JsonNode room2d = two.await("room", 5000);
            System.out.println("[흐름] 2D room 키: " + toKeys(room2d) + ", 멤버 키: " + toKeys(room2d.path("members").get(0)));
            for (String k : List.of("v", "now", "chat", "blocked", "mobs", "hunt")) assertThat(room2d.has(k)).as("2D room." + k).isFalse();
            for (JsonNode mem : room2d.path("members")) assertThat(mem.has("u")).isFalse();
            assertThat(room2d.path("key").asText()).isNotEqualTo(room.path("key").asText());
            assertThat(toKeys(room2d)).containsExactlyInAnyOrder("t", "no", "key", "cap", "me", "members", "ghosts", "ev");
        } finally {
            for (Client c : new Client[]{a, b, two}) if (c != null) c.ws.sendClose(WebSocket.NORMAL_CLOSURE, "끝").join();
        }
    }

    @Test
    void 차단_읽기가_터져도_입장은_된다() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("DB 터짐")).when(moderationSpy).blockedCodes(anyLong());
        Client c = connect(token(newPet("m")));
        try {
            c.send("{\"t\":\"hello\",\"image\":\"" + pngData() + "\",\"facing\":\"right\"}");
            assertThat(c.await("room", 5000).path("members")).hasSize(1);
        } finally {
            org.mockito.Mockito.reset(moderationSpy);
            c.ws.sendClose(WebSocket.NORMAL_CLOSURE, "끝").join();
        }
    }

    private static List<String> toKeys(JsonNode n) {
        List<String> keys = new ArrayList<>();
        Iterator<String> it = n.fieldNames();
        while (it.hasNext()) keys.add(it.next());
        return keys;
    }
}
