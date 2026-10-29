package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TamagotchiPersonalityTest {

    private static final VitalsRule RULE = new VitalsRule(6, 12, Duration.ofHours(4), 60, 13, Duration.ofDays(365));
    // 졸린 시간대는 21시~새벽 5시 (시작 시각이 없고 id도 없으면 21시)
    private static final LocalDateTime NIGHT = LocalDateTime.of(2026, 10, 23, 22, 0);
    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 10, 24, 5, 0);

    private static Tamagotchi baby() {
        return Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(20).strength(80).build();
    }

    @Test
    @DisplayName("졸린 시간대가 끝났는데 불이 꺼져 있으면 10분마다 우울 +1")
    void oversleep_growsGloom() {
        Tamagotchi p = baby();
        p.applyTime(NIGHT, RULE);
        p.sleep(NIGHT);
        p.applyTime(MORNING.minusMinutes(1), RULE);
        assertThat(p.getTraitGloomy()).isZero(); // 졸린 시간 안에서는 오르지 않는다
        p.applyTime(MORNING, RULE);
        p.applyTime(MORNING.plusMinutes(35), RULE);
        assertThat(p.getTraitGloomy()).isEqualTo(3);
    }

    @Test
    @DisplayName("졸린 시간대에 깨우면 까칠 +5, 푹 자고 시간대가 끝난 뒤 깨면 활기 +5")
    void wakeUp_traits() {
        Tamagotchi woken = baby();
        woken.sleep(NIGHT);
        woken.wakeUp(NIGHT.plusHours(1));
        assertThat(woken.getTraitGrumpy()).isEqualTo(Tamagotchi.WOKEN_GRUMPY);

        Tamagotchi rested = baby();
        rested.sleep(NIGHT);
        rested.wakeUp(MORNING.plusMinutes(5));
        assertThat(rested.getTraitLively()).isEqualTo(Tamagotchi.SLEPT_WELL_LIVELY);
        assertThat(rested.getTraitGrumpy()).isZero();
    }

    @Test
    @DisplayName("트레이닝하면 활기가 오르고 우울은 0 아래로 내려가지 않는다")
    void training_growsLively() {
        Tamagotchi p = baby();
        p.consumeForTraining(20, 20);
        assertThat(p.getTraitLively()).isEqualTo(Tamagotchi.TRAINING_LIVELY);
        assertThat(p.getTraitGloomy()).isZero(); // 0 아래로 내려가지 않는다
    }

    @Test
    @DisplayName("응가가 10개를 넘어 쌓일 때마다 까칠 +2")
    void poopPile_growsGrumpy() {
        Tamagotchi p = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(0).strength(100).poopCount(8).build();
        p.applyTime(NIGHT.minusHours(10), RULE);
        p.applyTime(NIGHT.minusHours(7), RULE); // 8 → 11개: 10·11번째가 해당
        assertThat(p.getPoopCount()).isEqualTo(11);
        assertThat(p.getTraitGrumpy()).isEqualTo(2 * Tamagotchi.POOP_PILE_GRUMPY);
        p.cleanPoop();
        assertThat(p.getTraitGrumpy()).isEqualTo(2 * Tamagotchi.POOP_PILE_GRUMPY - Tamagotchi.CLEAN_CALM);
    }

    @Test
    @DisplayName("진화할 때 가장 높은 성향으로 성격이 정해지고 성향은 0으로 돌아간다")
    void evolve_decidesPersonality() {
        Tamagotchi p = baby();
        for (int i = 0; i < 11; i++) p.consumeForTraining(0, 0); // 활기 33
        p.evolve(EvolutionStage.CHILD);
        assertThat(Personality.LIVELY.keys()).contains(p.getPersonality());
        assertThat(p.getTraitLively()).isZero();

        p.evolve(EvolutionStage.ADULT);
        assertThat(Personality.GENTLE.keys()).contains(p.getPersonality()); // 아무 성향도 30에 못 미침
    }

    @Test
    @DisplayName("네 무리가 앱의 22가지 성격을 겹치지 않게 나눠 가진다")
    void groups_cover22() {
        java.util.Set<String> all = new java.util.HashSet<>();
        int total = 0;
        for (Personality g : Personality.values()) { all.addAll(g.keys()); total += g.keys().size(); }
        assertThat(total).isEqualTo(22);
        assertThat(all).hasSize(22);
    }

    @Test
    @DisplayName("알에서 깨어날 때는 성격을 정하지 않는다")
    void hatch_keepsNoPersonality() {
        Tamagotchi egg = Tamagotchi.builder().evolutionStage(EvolutionStage.EGG).build();
        egg.hatch();
        assertThat(egg.getPersonality()).isNull();
    }

    @Test
    @DisplayName("성격 고르기: 30 미만이면 순함, 같으면 활기 > 까칠 > 우울")
    void decide() {
        assertThat(Personality.decide(29, 29, 29)).isEqualTo(Personality.GENTLE);
        assertThat(Personality.decide(40, 40, 40)).isEqualTo(Personality.LIVELY);
        assertThat(Personality.decide(40, 40, 10)).isEqualTo(Personality.SENSITIVE);
        assertThat(Personality.decide(10, 50, 40)).isEqualTo(Personality.GLOOMY);
    }
}
