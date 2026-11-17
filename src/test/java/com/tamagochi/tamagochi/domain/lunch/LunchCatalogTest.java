package com.tamagochi.tamagochi.domain.lunch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LunchCatalogTest {

    @Test
    @DisplayName("도시락 데이터: id가 겹치지 않고, 열매 값·포만감은 양수이며 포만감은 100을 넘지 않는다")
    void dataIsValid() {
        List<LunchCatalog.Lunchbox> all = LunchCatalog.all();
        assertThat(all).isNotEmpty();
        assertThat(all).extracting(LunchCatalog.Lunchbox::id).doesNotHaveDuplicates();
        for (LunchCatalog.Lunchbox box : all) {
            assertThat(box.id()).as("id는 영어 소문자").matches("[a-z][a-z0-9-]*");
            assertThat(box.id().length()).as("lunch_id 칸 길이 40").isLessThanOrEqualTo(40);
            assertThat(box.name()).as(box.id()).isNotBlank();
            assertThat(box.emoji()).as(box.id()).isNotBlank();
            assertThat(box.fruitCost()).as(box.id()).isPositive();
            assertThat(box.satiety()).as(box.id()).isPositive().isLessThanOrEqualTo(100);
            if (box.unlock() != null) {
                assertThat(box.unlock().type()).as(box.id()).isNotBlank();
                assertThat(box.unlock().id()).as(box.id()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("확정된 도시락 세 가지: 주먹밥 2열매 +20, 든든 5열매 +45, 잔치 12열매 +100(잠김)")
    void confirmedLunchboxes() {
        assertThat(LunchCatalog.all()).extracting(LunchCatalog.Lunchbox::id).containsExactly("riceball", "hearty", "feast");

        LunchCatalog.Lunchbox riceball = LunchCatalog.find("riceball").orElseThrow();
        assertThat(riceball.name()).isEqualTo("주먹밥 도시락");
        assertThat(riceball.fruitCost()).isEqualTo(2);
        assertThat(riceball.satiety()).isEqualTo(20);
        assertThat(riceball.unlock()).isNull();

        LunchCatalog.Lunchbox hearty = LunchCatalog.find("hearty").orElseThrow();
        assertThat(hearty.fruitCost()).isEqualTo(5);
        assertThat(hearty.satiety()).isEqualTo(45);
        assertThat(hearty.unlock()).isNull();

        LunchCatalog.Lunchbox feast = LunchCatalog.find("feast").orElseThrow();
        assertThat(feast.fruitCost()).isEqualTo(12);
        assertThat(feast.satiety()).isEqualTo(100);
        assertThat(feast.unlock()).isEqualTo(new LunchCatalog.Unlock("NPC", "hamster"));

        assertThat(LunchCatalog.find("pizza")).isEmpty();
        assertThat(LunchCatalog.find(null)).isEmpty();
    }
}
