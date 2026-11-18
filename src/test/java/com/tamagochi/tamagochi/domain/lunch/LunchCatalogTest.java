package com.tamagochi.tamagochi.domain.lunch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LunchCatalogTest {

    @Test
    @DisplayName("도시락 데이터: 열매 값·포만감은 양수이고 포만감은 100을 넘지 않는다")
    void dataIsValid() {
        LunchCatalog.Lunchbox box = LunchCatalog.single();
        assertThat(box.id()).as("id는 영어 소문자").matches("[a-z][a-z0-9-]*");
        assertThat(box.id().length()).as("lunch_id 칸 길이 40").isLessThanOrEqualTo(40);
        assertThat(box.name()).isNotBlank();
        assertThat(box.emoji()).isNotBlank();
        assertThat(box.fruitCost()).isPositive();
        assertThat(box.satiety()).isPositive().isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("도시락은 한 종류뿐이다: 열매 3 → 포만감 +35")
    void singleLunchbox() {
        assertThat(LunchCatalog.all()).containsExactly(LunchCatalog.single());
        LunchCatalog.Lunchbox box = LunchCatalog.single();
        assertThat(box.id()).isEqualTo("lunchbox");
        assertThat(LunchCatalog.id()).isEqualTo("lunchbox");
        assertThat(box.name()).isEqualTo("도시락");
        assertThat(box.emoji()).isEqualTo("🍱");
        assertThat(box.fruitCost()).isEqualTo(3);
        assertThat(box.satiety()).isEqualTo(35);
    }

    @Test
    @DisplayName("가방 용량은 데이터에 있다: 기본 2, 상한 4")
    void bagSlots() {
        assertThat(LunchCatalog.defaultBagSlots()).isEqualTo(2);
        assertThat(LunchCatalog.maxBagSlots()).isEqualTo(4);
    }

    @Test
    @DisplayName("요청 id: 지금 id, 예전 id(riceball·hearty·feast), 빈 id는 모두 하나뿐인 도시락이고 모르는 id만 없다")
    void resolveMapsOldIdsToTheSingleLunchbox() {
        LunchCatalog.Lunchbox box = LunchCatalog.single();
        assertThat(LunchCatalog.resolve("lunchbox")).contains(box);
        assertThat(LunchCatalog.resolve("riceball")).contains(box);
        assertThat(LunchCatalog.resolve("hearty")).contains(box);
        assertThat(LunchCatalog.resolve("feast")).contains(box);
        assertThat(LunchCatalog.resolve(null)).contains(box);
        assertThat(LunchCatalog.resolve("")).contains(box);
        assertThat(LunchCatalog.resolve("  ")).contains(box);
        assertThat(LunchCatalog.resolve("pizza")).isEmpty();

        assertThat(LunchCatalog.isLegacy("riceball")).isTrue();
        assertThat(LunchCatalog.isLegacy("lunchbox")).isFalse();
        assertThat(LunchCatalog.isLegacy(null)).isFalse();
    }

    @Test
    @DisplayName("예전 도시락 환산은 올림이다: 주먹밥 20 → 1개, 든든 45 → 2개, 잔치 100 → 3개. 지금 도시락은 1개")
    void legacyUnits() {
        assertThat(LunchCatalog.unitsPer("riceball")).isEqualTo(1);
        assertThat(LunchCatalog.unitsPer("hearty")).isEqualTo(2);
        assertThat(LunchCatalog.unitsPer("feast")).isEqualTo(3);
        assertThat(LunchCatalog.unitsPer("lunchbox")).isEqualTo(1);
        assertThat(LunchCatalog.unitsPer("pizza")).as("모르는 id도 사라지지 않게 1개").isEqualTo(1);
    }
}
