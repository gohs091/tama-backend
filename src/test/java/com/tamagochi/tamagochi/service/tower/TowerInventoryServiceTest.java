package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.tower.TowerInventoryService.Data;
import com.tamagochi.tamagochi.service.tower.TowerInventoryService.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TowerInventoryServiceTest extends TowerLunchTestSupport {

    @Autowired TowerInventoryService inventoryService;

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
        inventoryService.save(user, new Data(List.of(hat), equip(brush)));

        Data d = inventoryService.get(user);
        assertThat(d.equip().get("weapon").name()).isEqualTo("빠른 붓");
        assertThat(d.equip().get("weapon").stats()).containsEntry("aspd", 9.0);
        assertThat(d.bag()).extracting(Item::id).containsExactly("it0002");

        inventoryService.save(user, new Data(List.of(), equip(null)));
        assertThat(inventoryService.get(user).bag()).isEmpty();
        assertThat(inventoryService.get(user).equip().get("weapon")).isNull();
    }

    @Test
    @DisplayName("있을 수 없는 아이템은 통째로 거절하고 아무것도 바꾸지 않는다")
    void rejectsOutOfRange() {
        User user = newUser(0, 50);
        Item ok = item("it0001", "weapon", 1, Map.of("aspd", 9.0));
        inventoryService.save(user, new Data(List.of(), equip(ok)));

        // 상한을 넘는 값, 모르는 옵션, 전설이 아닌데 탄 수, 칸이 안 맞는 장비, 같은 id 둘, 모르는 효과
        assertCode(() -> inventoryService.save(user, new Data(List.of(item("it0009", "head", 1, Map.of("hp", 9999.0))), equip(ok))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, new Data(List.of(item("it0009", "head", 1, Map.of("god", 1.0))), equip(ok))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, new Data(List.of(item("it0009", "charm", 2, Map.of("proj", 1.0))), equip(ok))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, new Data(List.of(), equip(item("it0009", "head", 1, Map.of("hp", 8.0))))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, new Data(List.of(item("it0001", "head", 1, Map.of("hp", 8.0))), equip(ok))), "INVALID_INVENTORY");
        assertCode(() -> inventoryService.save(user, new Data(List.of(new Item("it0009", "charm", 3, "수상한 단추", "🔘", null, Map.of("proj", 1.0), "win", "다 이겨")), equip(ok))), "INVALID_INVENTORY");

        assertThat(inventoryService.get(user).bag()).isEmpty();
        assertThat(inventoryService.get(user).equip().get("weapon").id()).isEqualTo("it0001");
    }

    @Test
    @DisplayName("가방은 24개까지다. 전설은 탄 수와 아는 효과를 가질 수 있다")
    void bagLimitAndLegend() {
        User user = newUser(0, 50);
        List<Item> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) many.add(item("it%04d".formatted(i), "head", 0, Map.of("hp", 8.0)));
        assertCode(() -> inventoryService.save(user, new Data(many, equip(null))), "INVALID_INVENTORY");

        Item legend = new Item("it9001", "weapon", 3, "세 갈래 깃펜", "🪶", "ranged", Map.of("proj", 2.0, "d_탄", -15.0), null, "탄이 한 번에 두 발 더 나가");
        Item frost = new Item("it9002", "charm", 3, "깨뜨리기 스티커", "⭐", null, Map.of("crit", 4.0), "b8", "느려지거나 언 상대가 40% 더 아프게 맞아");
        Data saved = inventoryService.save(user, new Data(many.subList(0, 24), Map.of("weapon", legend, "charm1", frost)));
        assertThat(saved.bag()).hasSize(24);
        assertThat(inventoryService.get(user).equip().get("charm1").fx()).isEqualTo("b8");
    }
}
