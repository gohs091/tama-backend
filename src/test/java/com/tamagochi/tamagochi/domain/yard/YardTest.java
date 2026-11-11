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

    @Test
    void 방_가구는_종류가_맞는_방_자리에만_놓고_마당_소품과_섞이지_않는다() {
        Yard yard = new Yard(null, t0);
        yard.addFruit(40);
        yard.buyProp("bed-wood");                 // 12
        yard.buyProp("lamp");                     // 8
        yard.buyProp("bush");                     // 3
        assertThat(yard.getFruit()).isEqualTo(17);

        yard.place("r-bed", "bed-wood");
        yard.place("r-s1", "lamp");
        assertThat(yard.getSlots()).containsEntry("r-bed", "bed-wood").containsEntry("r-s1", "lamp");
        assertThatThrownBy(() -> yard.place("r-w0", "lamp")).hasMessageContaining("놓을 수 없어요");   // 스탠드는 벽에 못 건다
        assertThatThrownBy(() -> yard.place("s0", "lamp")).hasMessageContaining("놓을 수 없어요");     // 방 가구는 마당에 못 놓는다
        assertThatThrownBy(() -> yard.place("r-s0", "bush")).hasMessageContaining("놓을 수 없어요");   // 마당 소품은 방에 못 놓는다
        yard.place("r-bed", null);                // 침대를 빼면 기본 침대로 돌아간다 (자리만 비움)
        assertThat(yard.stored("bed-wood")).isEqualTo(1);
    }

    @Test
    void 방_가구는_벽은_벽_안에서_바닥은_바닥_안에서만_옮겨진다() {
        Yard yard = new Yard(null, t0);
        yard.addFruit(40);
        yard.buyProp("frame");
        yard.buyProp("lamp");
        yard.place("r-w4", "frame");                // 벽 장식 자리는 여섯 개
        yard.place("r-s5", "lamp");

        yard.moveRoom("r-w4", 500, 300);
        assertThat(yard.getRoomPos()).containsEntry("r-w4", "500,300");
        yard.moveRoom("r-w4", 500, 900);            // 벽 장식을 바닥으로 끌면 벽 아래 끝에 멈춘다
        assertThat(yard.getRoomPos()).containsEntry("r-w4", "500,600");
        yard.moveRoom("r-s5", -50, 100);            // 가구를 벽으로 끌면 바닥 위 끝·왼쪽 끝에 멈춘다
        assertThat(yard.getRoomPos()).containsEntry("r-s5", "60,715");

        yard.moveRoom("r-bed", 300, 800);           // 침대는 기본 이불이어도 옮길 수 있다
        assertThat(yard.getRoomPos()).containsEntry("r-bed", "300,800");
        assertThatThrownBy(() -> yard.moveRoom("r-s0", 500, 800)).hasMessageContaining("빈 자리");
        assertThatThrownBy(() -> yard.moveRoom("r-wallpaper", 500, 300)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> yard.moveRoom("s0", 500, 300)).isInstanceOf(IllegalArgumentException.class);

        yard.place("r-w4", null);                   // 빼면 둔 곳도 잊는다
        assertThat(yard.getRoomPos()).doesNotContainKey("r-w4");
    }
}
