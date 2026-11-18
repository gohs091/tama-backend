package com.tamagochi.tamagochi.domain.gear;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 기본 무기 카탈로그: 나무 붓·깃 펜촉 정가, 맨손 피해, 잉크 가격·전투력 필드는 로딩 실패 */
class GearCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("gear.json: 기본 무기 두 종, 열매 10, 나무 붓·깃 펜촉 열매 10, 맨손(시안 자이로) 근접 25·원거리 11. 기본 무기는 추가 피해가 없어 껴도 같다. 전투력 숫자는 없다")
    void loadsStarterWeapons() throws Exception {
        assertThat(GearCatalog.provisional()).isTrue();
        assertThat(GearCatalog.note()).isNotBlank();
        assertThat(GearCatalog.weapons()).hasSize(2);

        GearCatalog.Unarmed bare = GearCatalog.unarmed();
        assertThat(bare.meleeDamage()).isEqualTo(25);
        assertThat(bare.rangedDamage()).isEqualTo(11);

        GearCatalog.Weapon brush = GearCatalog.weapon("wooden-brush").orElseThrow();
        assertThat(brush.name()).isEqualTo("나무 붓");
        assertThat(brush.slot()).isEqualTo(GearCatalog.Slot.WEAPON);
        assertThat(brush.style()).isEqualTo(GearCatalog.Style.MELEE);
        assertThat(brush.tier()).isEqualTo(GearCatalog.Tier.T1);
        assertThat(brush.price().fruit()).isEqualTo(10);
        assertThat(brush.unlockFloor()).isEqualTo(1);
        assertThat(brush.unlockNpc()).isEqualTo("ns-woodpecker");
        assertThat(brush.meleeDamage()).isNull();
        assertThat(brush.rangedDamage()).isNull();

        GearCatalog.Weapon nib = GearCatalog.weapon("feather-nib").orElseThrow();
        assertThat(nib.name()).isEqualTo("깃 펜촉");
        assertThat(nib.style()).isEqualTo(GearCatalog.Style.RANGED);
        assertThat(nib.price().fruit()).isEqualTo(10);
        assertThat(nib.rangedDamage()).isNull();
        assertThat(nib.meleeDamage()).isNull();

        assertThat(GearCatalog.weapon("no-such")).isEmpty();
        assertThat(GearCatalog.attacks(null)).isEqualTo(new GearCatalog.Attacks(25, 11));
        assertThat(GearCatalog.attacks("wooden-brush")).isEqualTo(new GearCatalog.Attacks(25, 11));
        assertThat(GearCatalog.attacks("feather-nib")).isEqualTo(new GearCatalog.Attacks(25, 11));
        assertThat(GearCatalog.attacks("no-such")).isEqualTo(new GearCatalog.Attacks(25, 11));

        // 데이터에도 전투력 키는 없다
        try (InputStream in = GearCatalog.class.getResourceAsStream(GearCatalog.RESOURCE)) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String lower = raw.toLowerCase();
            assertThat(lower).doesNotContain("\"bp\"").doesNotContain("battlepower").doesNotContain("recommendedbp");
        }
    }

    @Test
    @DisplayName("전투 수치 품목에 잉크 가격이 있으면 카탈로그 로딩이 실패한다")
    void inkPriceFailsLoad() throws Exception {
        ObjectNode root = copyOfFile();
        ((ObjectNode) root.get("weapons").get(0).get("price")).put("ink", 1);
        assertThatThrownBy(() -> GearCatalog.parse(root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잉크");
    }

    @Test
    @DisplayName("가격에 열매가 아닌 필드(조각 등)가 있어도 로딩이 실패한다")
    void nonFruitPriceFailsLoad() throws Exception {
        ObjectNode root = copyOfFile();
        ((ObjectNode) root.get("weapons").get(1).get("price")).put("fragments", 2);
        assertThatThrownBy(() -> GearCatalog.parse(root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("열매가 아닌");
    }

    @Test
    @DisplayName("전투력 필드가 있으면 로딩이 실패한다")
    void battlePowerFieldFailsLoad() throws Exception {
        ObjectNode root = copyOfFile();
        root.put("bp", 100);
        assertThatThrownBy(() -> GearCatalog.parse(root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("전투력");
    }

    @Test
    @DisplayName("기본 무기 id가 빠지거나 가격이 0이면 로딩이 실패한다")
    void missingStarterOrZeroPriceFailsLoad() throws Exception {
        ObjectNode missing = copyOfFile();
        ((ObjectNode) missing.get("weapons").get(0)).put("id", "other-brush");
        assertThatThrownBy(() -> GearCatalog.parse(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wooden-brush");

        ObjectNode zero = copyOfFile();
        ((ObjectNode) zero.get("weapons").get(0).get("price")).put("fruit", 0);
        assertThatThrownBy(() -> GearCatalog.parse(zero))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    @DisplayName("파일에 있는 무기 id는 알려진 두 종뿐이다")
    void onlyStarterIds() {
        assertThat(GearCatalog.weapons().stream().map(GearCatalog.Weapon::id).toList())
                .containsExactly("wooden-brush", "feather-nib");
        assertThat(Set.copyOf(GearCatalog.weapons().stream().map(GearCatalog.Weapon::id).toList()))
                .hasSize(2);
    }

    private static ObjectNode copyOfFile() throws Exception {
        try (InputStream in = GearCatalog.class.getResourceAsStream(GearCatalog.RESOURCE)) {
            JsonNode n = MAPPER.readTree(in);
            return n.deepCopy();
        }
    }
}
