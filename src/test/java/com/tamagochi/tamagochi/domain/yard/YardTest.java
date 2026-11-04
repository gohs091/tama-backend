package com.tamagochi.tamagochi.domain.yard;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YardTest {

    private final Instant t0 = Instant.parse("2026-11-04T01:00:00Z");
    private final LocalDate today = LocalDate.of(2026, 11, 4);

    @Test
    void firstYardIsRipeThenGrowsFourHours() {
        Yard yard = new Yard(null, t0);
        assertThat(yard.ripe(t0)).isTrue();

        assertThat(yard.harvest(t0)).isEqualTo(YardCatalog.HARVEST);
        assertThat(yard.getFruit()).isEqualTo(YardCatalog.HARVEST);
        assertThat(yard.ripe(t0)).isFalse();
        assertThat(yard.remaining(t0)).isEqualTo(YardCatalog.GROW);
        assertThatThrownBy(() -> yard.harvest(t0.plus(Duration.ofHours(3)))).isInstanceOf(IllegalStateException.class);

        Instant later = t0.plus(YardCatalog.GROW);
        assertThat(yard.ripe(later)).isTrue();
        yard.harvest(later);
        assertThat(yard.getFruit()).isEqualTo(YardCatalog.HARVEST * 2);
    }

    @Test
    void boostIsLimitedPerDay() {
        Yard yard = new Yard(null, t0);
        assertThatThrownBy(() -> yard.boost(today, t0)).hasMessageContaining("이미 다 자랐어요");

        Instant now = t0;
        for (int i = 0; i < YardCatalog.BOOST_PER_DAY; i++) {
            yard.harvest(now);
            yard.boost(today, now);
            assertThat(yard.ripe(now)).isTrue();
        }
        yard.harvest(now);
        assertThat(yard.boostsLeft(today)).isZero();
        Instant fixed = now;
        assertThatThrownBy(() -> yard.boost(today, fixed)).hasMessageContaining("오늘은");
        assertThat(yard.boostsLeft(today.plusDays(1))).isEqualTo(YardCatalog.BOOST_PER_DAY);
    }

    @Test
    void buyAndPlaceProps() {
        Yard yard = new Yard(null, t0);
        assertThatThrownBy(() -> yard.buyProp("bush")).hasMessageContaining("열매");
        yard.harvest(t0);                         // 열매 3
        yard.buyProp("bush");                     // 3 → 0
        assertThat(yard.getFruit()).isZero();
        assertThat(yard.stored("bush")).isEqualTo(1);

        yard.place("s0", "bush");
        assertThat(yard.stored("bush")).isZero();
        assertThatThrownBy(() -> yard.place("s1", "bush")).hasMessageContaining("보관 중");
        yard.place("s0", "bush");                 // 같은 자리 같은 소품: 그대로
        yard.place("s1", null);                   // 빈 자리 비우기: 그대로

        // 옮기기: 비우고 다른 자리에
        yard.place("s0", null);
        yard.place("s2", "bush");
        assertThat(yard.getSlots()).containsOnlyKeys("s2");

        assertThatThrownBy(() -> yard.place("s9", "bush")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> yard.buyProp("rocket")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placingOverAPropReturnsItToStorage() {
        Yard yard = new Yard(null, t0);
        Instant now = t0;
        for (int i = 0; i < 4; i++) { yard.harvest(now); now = now.plus(YardCatalog.GROW); } // 열매 12
        yard.buyProp("bush");
        yard.buyProp("mailbox");
        yard.place("s0", "bush");
        yard.place("s0", "mailbox");
        assertThat(yard.getSlots()).containsEntry("s0", "mailbox");
        assertThat(yard.stored("bush")).isEqualTo(1);
    }

    @Test
    void backgrounds() {
        Yard yard = new Yard(null, t0);
        assertThat(yard.ownsBackground("nature-spirits", "nature-spirits")).isTrue();
        assertThat(yard.ownsBackground("dark-area", "nature-spirits")).isFalse();
        assertThatThrownBy(() -> yard.useBackground("dark-area", "nature-spirits")).hasMessageContaining("갖고 있지");

        yard.addBackground("dark-area");
        yard.useBackground("dark-area", "nature-spirits");
        assertThat(yard.getBackground()).isEqualTo("dark-area");

        yard.useBackground("nature-spirits", "nature-spirits"); // 내 진영으로 돌아가면 비운다
        assertThat(yard.getBackground()).isNull();
        assertThatThrownBy(() -> yard.addBackground("moon")).isInstanceOf(IllegalArgumentException.class);
    }
}
