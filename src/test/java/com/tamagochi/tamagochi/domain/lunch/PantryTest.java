package com.tamagochi.tamagochi.domain.lunch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 찬장·가방: 가방 용량과, 예전 세 종류를 지금 도시락 개수로 바꾸는 환산 */
class PantryTest {

    private static final int ROOMY = 99; // 가방 넘침을 보지 않는 테스트용 용량

    private static Pantry pantry() {
        return new Pantry(null);
    }

    @Test
    @DisplayName("가방 용량: 기본 2, 보너스를 더하고, 상한 4를 넘지 않는다")
    void bagCapacity() {
        Pantry pantry = pantry();
        assertThat(pantry.getBagSlots()).isEqualTo(2);
        assertThat(pantry.bagCapacity(0)).isEqualTo(2);
        assertThat(pantry.bagCapacity(1)).isEqualTo(3);
        assertThat(pantry.bagCapacity(2)).isEqualTo(4);
        assertThat(pantry.bagCapacity(5)).isEqualTo(4);
        assertThat(pantry.bagCapacity(-1)).isEqualTo(2);
    }

    @Test
    @DisplayName("환산: 주먹밥 1 → 1개, 든든 1 → 2개, 잔치 1 → 3개 (개수만큼 곱한다)")
    void migratesEachKind() {
        Pantry riceball = pantry();
        riceball.getCupboard().put("riceball", 3);
        assertThat(riceball.migrateLegacy(ROOMY)).isTrue();
        assertThat(riceball.getCupboard()).containsOnly(entry(3));

        Pantry hearty = pantry();
        hearty.getCupboard().put("hearty", 2);
        assertThat(hearty.migrateLegacy(ROOMY)).isTrue();
        assertThat(hearty.getCupboard()).containsOnly(entry(4));

        Pantry feast = pantry();
        feast.getCupboard().put("feast", 2);
        assertThat(feast.migrateLegacy(ROOMY)).isTrue();
        assertThat(feast.getCupboard()).containsOnly(entry(6));
    }

    @Test
    @DisplayName("같은 칸에 여러 종류(지금 도시락 포함)가 있으면 합치고, 예전 키는 남기지 않는다. 찬장과 가방은 따로 센다")
    void migratesMixed() {
        Pantry pantry = pantry();
        pantry.getCupboard().put("riceball", 2); // 2
        pantry.getCupboard().put("hearty", 1);   // 2
        pantry.getCupboard().put("feast", 1);    // 3
        pantry.getCupboard().put("lunchbox", 4); // 4
        pantry.getBag().put("riceball", 1);      // 1
        pantry.getBag().put("hearty", 1);        // 2

        assertThat(pantry.cupboardCount()).as("바꾸기 전에도 지금 개수로 센다").isEqualTo(11);
        assertThat(pantry.bagUsed()).isEqualTo(3);

        assertThat(pantry.migrateLegacy(ROOMY)).isTrue();
        assertThat(pantry.getCupboard()).containsOnly(entry(11));
        assertThat(pantry.getBag()).containsOnly(entry(3));
        assertThat(pantry.hasLegacy()).isFalse();
    }

    @Test
    @DisplayName("빈 찬장·가방은 바꿀 것이 없고 그대로 비어 있다")
    void emptyPantryIsUntouched() {
        Pantry pantry = pantry();
        assertThat(pantry.hasLegacy()).isFalse();
        assertThat(pantry.migrateLegacy(2)).isFalse();
        assertThat(pantry.getCupboard()).isEmpty();
        assertThat(pantry.getBag()).isEmpty();
    }

    @Test
    @DisplayName("이미 지금 도시락만 있으면 다시 바꾸지 않는다 (여러 번 불러도 개수가 그대로)")
    void doesNotMigrateTwice() {
        Pantry pantry = pantry();
        pantry.getCupboard().put("feast", 1);
        pantry.getBag().put("hearty", 1);
        assertThat(pantry.migrateLegacy(2)).isTrue();
        assertThat(pantry.migrateLegacy(2)).isFalse();
        assertThat(pantry.migrateLegacy(2)).isFalse();
        assertThat(pantry.getCupboard()).containsOnly(entry(3));
        assertThat(pantry.getBag()).containsOnly(entry(2));

        Pantry fresh = pantry();
        fresh.addToCupboard(5);
        fresh.pack(2, 2);
        assertThat(fresh.migrateLegacy(2)).isFalse();
        assertThat(fresh.getCupboard()).containsOnly(entry(3));
        assertThat(fresh.getBag()).containsOnly(entry(2));
    }

    @Test
    @DisplayName("바꾼 뒤 가방이 용량을 넘으면 넘는 만큼은 찬장으로 간다: 합계는 줄지 않는다")
    void bagOverflowGoesToCupboard() {
        Pantry pantry = pantry();
        pantry.getBag().put("feast", 2);        // 6개 몫, 가방 용량은 2
        pantry.getCupboard().put("riceball", 1);

        assertThat(pantry.migrateLegacy(pantry.bagCapacity(0))).isTrue();
        assertThat(pantry.getBag()).containsOnly(entry(2));
        assertThat(pantry.getCupboard()).containsOnly(entry(5));
        assertThat(pantry.bagUsed() + pantry.cupboardCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("개수가 0이거나 음수인 이상한 예전 행은 0개로 보고, 음수가 되지 않는다")
    void badCountsNeverGoNegative() {
        Pantry pantry = pantry();
        pantry.getCupboard().put("riceball", -2);
        pantry.getCupboard().put("hearty", 0);
        pantry.getBag().put("feast", 1);
        assertThat(pantry.migrateLegacy(ROOMY)).isTrue();
        assertThat(pantry.getCupboard()).isEmpty();
        assertThat(pantry.getBag()).containsOnly(entry(3));
    }

    private static java.util.Map.Entry<String, Integer> entry(int count) {
        return java.util.Map.entry("lunchbox", count);
    }
}
