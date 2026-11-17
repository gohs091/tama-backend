package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TamagotchiSatietyTest {

    private static Tamagotchi petWithSatiety(int satiety) {
        return Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(100 - satiety).strength(50).build();
    }

    @Test
    @DisplayName("포만감은 100 − hunger다")
    void satietyMirrorsHunger() {
        assertThat(petWithSatiety(0).satiety()).isZero();
        assertThat(petWithSatiety(37).satiety()).isEqualTo(37);
        assertThat(petWithSatiety(100).satiety()).isEqualTo(100);
    }

    @Test
    @DisplayName("refill: 포만감이 차고 실제로 찬 양을 돌려준다. 100을 넘지 않는다")
    void refill() {
        Tamagotchi pet = petWithSatiety(50);
        assertThat(pet.refill(20)).isEqualTo(20);
        assertThat(pet.satiety()).isEqualTo(70);
        assertThat(pet.getHunger()).isEqualTo(30);

        assertThat(pet.refill(45)).isEqualTo(30); // 100에서 멈춘다
        assertThat(pet.satiety()).isEqualTo(100);
        assertThat(pet.getHunger()).isZero();

        assertThat(pet.refill(100)).isZero();     // 이미 가득
        assertThat(pet.satiety()).isEqualTo(100);

        Tamagotchi empty = petWithSatiety(0);
        assertThat(empty.refill(100)).isEqualTo(100); // 0 → 100
        assertThat(empty.satiety()).isEqualTo(100);

        assertThat(petWithSatiety(40).refill(0)).isZero();
        assertThat(petWithSatiety(40).refill(-5)).isZero(); // 음수로 포만감을 깎지 않는다
    }

    @Test
    @DisplayName("spendSatiety: 포만감이 줄고 실제로 쓴 양을 돌려준다. 0 아래로 내려가지 않는다")
    void spendSatiety() {
        Tamagotchi pet = petWithSatiety(100);
        assertThat(pet.spendSatiety(35)).isEqualTo(35);
        assertThat(pet.satiety()).isEqualTo(65);
        assertThat(pet.getHunger()).isEqualTo(35);

        assertThat(pet.spendSatiety(100)).isEqualTo(65); // 0에서 멈춘다
        assertThat(pet.satiety()).isZero();
        assertThat(pet.getHunger()).isEqualTo(100);

        assertThat(pet.spendSatiety(35)).isZero();       // 이미 비었다
        assertThat(pet.getHunger()).isEqualTo(100);

        assertThat(petWithSatiety(40).spendSatiety(0)).isZero();
        assertThat(petWithSatiety(40).spendSatiety(-5)).isZero(); // 음수로 포만감을 채우지 않는다
    }

    @Test
    @DisplayName("refill·spendSatiety는 집의 먹이기 기록(먹은 횟수·먹은 시각·피로)을 바꾸지 않는다")
    void doesNotTouchHomeFeedingState() {
        Tamagotchi pet = petWithSatiety(50);
        pet.refill(20);
        pet.spendSatiety(35);
        assertThat(pet.getFeedCount()).isZero();
        assertThat(pet.getLastFedAt()).isNull();
        assertThat(pet.fatigue()).isZero();
        assertThat(pet.isSleeping()).isFalse();
    }

    @Test
    @DisplayName("굶주림 타이머가 돌던 끄적이가 도시락을 먹으면 타이머가 풀린다")
    void refillClearsStarvationTimer() {
        VitalsRule rule = new VitalsRule(8, 16, Duration.ofHours(6), 60, 13, Duration.ofMinutes(5));
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 17, 10, 0);
        Tamagotchi pet = petWithSatiety(0);
        pet.applyTime(t0, rule);
        assertThat(pet.getHungryStartedAt()).isEqualTo(t0);

        pet.refill(20);
        assertThat(pet.getHungryStartedAt()).isNull();
        pet.applyTime(t0.plusMinutes(1), rule);
        assertThat(pet.isDead()).isFalse();
    }
}
