package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChampionTierTest {

    @Test
    void 판수와_승률로_등급을_정한다() {
        assertThat(BattleRules.championTier(0, 0)).isZero();
        assertThat(BattleRules.championTier(29, 0)).isZero();       // 판수 모자람
        assertThat(BattleRules.championTier(18, 12)).isEqualTo(1);  // 30판 60%
        assertThat(BattleRules.championTier(17, 13)).isZero();      // 30판 56%
        assertThat(BattleRules.championTier(80, 20)).isEqualTo(2);  // 100판 80%
        assertThat(BattleRules.championTier(70, 30)).isEqualTo(1);  // 100판 70%
    }

    @Test
    void 진화하면_등급만큼_위력을_더_받고_전적은_0부터() {
        Tamagotchi pet = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).level(5).might(20)
                .exp(0).stageStartLevel(1).pendingExp(0).weightDg(100).hunger(50).strength(50).build();
        for (int i = 0; i < 24; i++) pet.recordBattle(1);
        for (int i = 0; i < 6; i++) pet.recordBattle(-1);           // 30판 80% → 챔피언(100판 미만)
        pet.evolve(EvolutionStage.CHILD);
        assertThat(pet.might()).isEqualTo(20 + BattleRules.CHAMPION_MIGHT_BONUS);
        assertThat(pet.battleWins()).isZero();
        assertThat(pet.battleLosses()).isZero();
    }

    @Test
    void 전적이_없으면_위력은_그대로() {
        Tamagotchi pet = Tamagotchi.builder().evolutionStage(EvolutionStage.CHILD).level(12).might(55)
                .exp(0).stageStartLevel(8).pendingExp(0).weightDg(100).hunger(50).strength(50).build();
        pet.evolve(EvolutionStage.ADULT);
        assertThat(pet.might()).isEqualTo(55);
    }
}
