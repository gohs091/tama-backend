package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.tower.TowerInventory;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tower.TowerInventoryRepository;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.tower.TowerInventoryService.Bind;
import com.tamagochi.tamagochi.service.tower.TowerInventoryService.Data;
import com.tamagochi.tamagochi.service.tower.TowerInventoryService.Doodles;
import com.tamagochi.tamagochi.service.tower.TowerInventoryService.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TowerInventoryServiceTest extends TowerLunchTestSupport {

    @Autowired TowerInventoryService inventoryService;
    @Autowired TowerInventoryRepository inventoryRepository;
    @Autowired MockMvc mvc;
    @Autowired AuthTokenService tokens;

    /** 낙서 없이(낙서를 모르는 옛 앱처럼) 가방과 장비만 */
    private static Data data(List<Item> bag, Map<String, Item> equip) {
        return new Data(bag, equip, null);
    }

    private static Doodles doodles(List<String> own, Bind a, Bind b) {
        return new Doodles(own, a, b);
    }

    private static final Bind NONE = new Bind(null, null);

    private static Item item(String id, String slot, int rarity, Map<String, Double> stats) {
        return new Item(id, slot, rarity, "빠른 붓", "🖌️", "weapon".equals(slot) ? "melee" : null, stats, null, null);
    }

    private static Map<String, Item> equip(Item weapon) {
        Map<String, Item> equip = new HashMap<>();
        equip.put("weapon", weapon);
        return equip;
    }

    @Test
    @DisplayName("처음에는 빈 가방과 빈 다섯 칸이다")
    void emptyAtFirst() {
        Data d = inventoryService.get(newUser(0, 50));
        assertThat(d.bag()).isEmpty();
        assertThat(d.equip()).containsOnlyKeys("weapon", "head", "body", "charm1", "charm2");
        assertThat(d.equip().values()).containsOnlyNulls();
    }

    @Test
    @DisplayName("저장한 가방과 낀 장비가 그대로 읽힌다 (통째로 바뀐다)")
    void saveAndRead() {
        User user = newUser(0, 50);
        Item brush = item("it0001", "weapon", 1, Map.of("aspd", 9.0, "d_근접", 12.0));
        Item hat = item("it0002", "head", 0, Map.of("hp", 8.0));
        inventoryService.save(user, data(List.of(hat), equip(brush)));

        Data d = inventoryService.get(user);
        assertThat(d.equip().get("weapon").name()).isEqualTo("빠른 붓");
        assertThat(d.equip().get("weapon").stats()).containsEntry("aspd", 9.0);
        assertThat(d.bag()).extracting(Item::id).containsExactly("it0002");

        inventoryService.save(user, data(List.of(), equip(null)));
        assertThat(inventoryService.get(user).bag()).isEmpty();
        assertThat(inventoryService.get(user).equip().get("weapon")).isNull();
    }

    @Test
    @DisplayName("있을 수 없는 아이템은 통째로 거절하고 아무것도 바꾸지 않는다")
    void rejectsOutOfRange() {
        User user = newUser(0, 50);
        Item ok = item("it0001", "weapon", 1, Map.of("aspd", 9.0));
        inventoryService.save(user, data(List.of(), equip(ok)));

        // 상한을 넘는 값, 모르는 옵션, 전설이 아닌데 탄 수, 칸이 안 맞는 장비, 같은 id 둘, 모르는 효과
        assertCode(() -> inventoryService.save(user, data(List.of(item("it0009", "head", 1, Map.of("hp", 9999.0))), equip(ok))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, data(List.of(item("it0009", "head", 1, Map.of("god", 1.0))), equip(ok))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, data(List.of(item("it0009", "charm", 2, Map.of("proj", 1.0))), equip(ok))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, data(List.of(), equip(item("it0009", "head", 1, Map.of("hp", 8.0))))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, data(List.of(item("it0001", "head", 1, Map.of("hp", 8.0))), equip(ok))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, data(List.of(new Item("it0009", "charm", 3, "수상한 단추", "🔘", null, Map.of("proj", 1.0), "win", "다 이겨")), equip(ok))), "INVALID_INVENTORY");

        assertThat(inventoryService.get(user).bag()).isEmpty();
        assertThat(inventoryService.get(user).equip().get("weapon").id()).isEqualTo("it0001");
    }

    @Test
    @DisplayName("가방은 24개까지다. 전설은 탄 수와 아는 효과를 가질 수 있다")
    void bagLimitAndLegend() {
        User user = newUser(0, 50);
        List<Item> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) many.add(item("it%04d".formatted(i), "head", 0, Map.of("hp", 8.0)));
        assertCode(() -> inventoryService.save(user, data(many, equip(null))), "INVALID_INVENTORY");

        Item legend = new Item("it9001", "weapon", 3, "세 갈래 깃펜", "🪶", "ranged", Map.of("proj", 2.0, "d_탄", -15.0), null, "탄이 한 번에 두 발 더 나가");
        Item frost = new Item("it9002", "charm", 3, "깨뜨리기 스티커", "⭐", null, Map.of("crit", 4.0), "b8", "느려지거나 언 상대가 40% 더 아프게 맞아");
        Data saved = inventoryService.save(user, data(many.subList(0, 24), Map.of("weapon", legend, "charm1", frost)));
        assertThat(saved.bag()).hasSize(24);
        assertThat(inventoryService.get(user).equip().get("charm1").fx()).isEqualTo("b8");
    }

    @Test
    @DisplayName("낙서를 저장하고 읽는다 (가진 낙서와 버튼 A · B의 지정)")
    void savesDoodles() {
        User user = newUser(0, 50);
        inventoryService.save(user, new Data(List.of(), equip(null), doodles(List.of("b1", "b5"), new Bind("b1", "red"), null)));

        Doodles read = inventoryService.get(user).doodles();
        assertThat(read.own()).containsExactly("b1", "b5");
        assertThat(read.a()).isEqualTo(new Bind("b1", "red"));
        assertThat(read.b()).isEqualTo(NONE); // 안 보낸 버튼은 빈 지정(평타)이다
    }

    @Test
    @DisplayName("옛 저장값(낙서가 없는 JSON)은 낙서가 null이다")
    void oldSaveHasNoDoodles() {
        User user = newUser(0, 50);
        TowerInventory inv = new TowerInventory(user);
        inv.replace("{\"bag\":[],\"equip\":{}}", LocalDateTime.now());
        inventoryRepository.save(inv);

        assertThat(inventoryService.get(user).doodles()).isNull();
        assertThat(inventoryService.get(user).bag()).isEmpty();
    }

    @Test
    @DisplayName("낙서를 안 보내면(낙서를 모르는 옛 앱) 저장된 낙서는 그대로 남는다")
    void keepsDoodlesWhenNotSent() {
        User user = newUser(0, 50);
        inventoryService.save(user, new Data(List.of(), equip(null), doodles(List.of("b2"), new Bind("b2", null), NONE)));
        inventoryService.save(user, data(List.of(), equip(null)));

        assertThat(inventoryService.get(user).doodles().own()).containsExactly("b2");
        assertThat(inventoryService.get(user).doodles().a().id()).isEqualTo("b2");
    }

    @Test
    @DisplayName("모르는 색이면 거절한다")
    void rejectsUnknownColor() {
        User user = newUser(0, 50);
        assertCode(() -> inventoryService.save(user, new Data(List.of(), equip(null), doodles(List.of("b1"), new Bind("b1", "pink"), NONE))), "INVALID_INVENTORY");
        assertThat(inventoryService.get(user).doodles()).isNull();
    }

    @Test
    @DisplayName("가지지 않은 낙서를 지정하면 거절한다")
    void rejectsUnownedBind() {
        User user = newUser(0, 50);
        Doodles ok = doodles(List.of("b1"), new Bind("b1", null), NONE);
        inventoryService.save(user, new Data(List.of(), equip(null), ok));

        assertCode(() -> inventoryService.save(user, new Data(List.of(), equip(null), doodles(List.of("b1"), NONE, new Bind("b4", null)))), "INVALID_INVENTORY");
        assertThat(inventoryService.get(user).doodles()).isEqualTo(ok);
    }

    @Test
    @DisplayName("낙서가 65개이거나, id 모양이 틀리거나, 같은 낙서가 둘이면 거절한다")
    void rejectsTooManyOrBadIds() {
        User user = newUser(0, 50);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 65; i++) many.add("x" + i);
        assertCode(() -> inventoryService.save(user, new Data(List.of(), equip(null), doodles(many, NONE, NONE))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, new Data(List.of(), equip(null), doodles(List.of("B1"), NONE, NONE))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, new Data(List.of(), equip(null), doodles(List.of("b1", "b1"), NONE, NONE))), "INVALID_INVENTORY");

        Data saved = inventoryService.save(user, new Data(List.of(), equip(null), doodles(many.subList(0, 64), NONE, NONE)));
        assertThat(saved.doodles().own()).hasSize(64);
    }

    @Test
    @DisplayName("HTTP: PUT 본문의 doodles가 저장되고 GET에 그대로 나온다")
    void httpRoundTrip() throws Exception {
        User user = newUser(0, 50);
        String body = "{\"tossUserId\":\"" + user.getTossUserId() + "\",\"bag\":[],\"equip\":{},"
                + "\"doodles\":{\"own\":[\"b1\",\"b5\"],\"a\":{\"id\":\"b5\",\"color\":\"blue\"},\"b\":{\"id\":null,\"color\":null}}}";
        String auth = "Bearer " + tokens.issue(user.getTossUserId()); // 테스트도 로그인 토큰을 본다 (tamagotchi.auth.enabled)
        mvc.perform(put("/api/tower/inventory").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tower/inventory/" + user.getTossUserId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doodles.own[1]").value("b5"))
                .andExpect(jsonPath("$.doodles.a.id").value("b5"))
                .andExpect(jsonPath("$.doodles.a.color").value("blue"))
                .andExpect(jsonPath("$.equip.weapon").isEmpty());
    }
}
