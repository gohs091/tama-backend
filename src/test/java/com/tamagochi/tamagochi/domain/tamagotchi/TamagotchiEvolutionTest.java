package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TamagotchiEvolutionTest {

    // hunger는 0=포만감 최대. 포만감 = 100 - hunger
    private static Tamagotchi pet(EvolutionStage stage, int satiation, int strength, int poop) {
        return Tamagotchi.builder()
                .evolutionStage(stage)
                .hunger(100 - satiation)
                .strength(strength)
                .poopCount(poop)
                .build();
    }

    private static Tamagotchi full(EvolutionStage stage) {
        Tamagotchi p = pet(stage, 80, 80, 0);
        p.addEvoGauge(EvolutionRules.gaugeMax(stage));
        return p;
    }

    @Test
    @DisplayName("탭 수에 따라 게이지가 1~3칸 오른다 (30 미만 1, 30~65 2, 66 이상 3)")
    void gainForTaps() {
        assertThat(EvolutionRules.gainForTaps(0)).isEqualTo(1);
        assertThat(EvolutionRules.gainForTaps(29)).isEqualTo(1);
        assertThat(EvolutionRules.gainForTaps(30)).isEqualTo(2);
        assertThat(EvolutionRules.gainForTaps(65)).isEqualTo(2);
        assertThat(EvolutionRules.gainForTaps(66)).isEqualTo(3);
        assertThat(EvolutionRules.gainForTaps(120)).isEqualTo(3);
    }

    @Test
    @DisplayName("게이지 칸 수는 낙서 10, 스케치 60이고 알·완성작은 0이다")
    void gaugeMax() {
        assertThat(EvolutionRules.gaugeMax(EvolutionStage.EGG)).isZero();
        assertThat(EvolutionRules.gaugeMax(EvolutionStage.BABY)).isEqualTo(10);
        assertThat(EvolutionRules.gaugeMax(EvolutionStage.CHILD)).isEqualTo(60);
        assertThat(EvolutionRules.gaugeMax(EvolutionStage.ADULT)).isZero();
    }

    @Test
    @DisplayName("게이지는 최대 칸 수를 넘지 않는다")
    void gaugeIsClamped() {
        Tamagotchi p = pet(EvolutionStage.BABY, 80, 80, 0);
        p.addEvoGauge(8);
        p.addEvoGauge(3);
        assertThat(p.getEvoGauge()).isEqualTo(10);
    }

    @Test
    @DisplayName("알과 완성작은 게이지가 오르지 않는다")
    void noGaugeForEggAndAdult() {
        Tamagotchi egg = pet(EvolutionStage.EGG, 80, 80, 0);
        Tamagotchi adult = pet(EvolutionStage.ADULT, 80, 80, 0);
        egg.addEvoGauge(3);
        adult.addEvoGauge(3);
        assertThat(egg.getEvoGauge()).isZero();
        assertThat(adult.getEvoGauge()).isZero();
    }

    @Test
    @DisplayName("게이지가 가득 차고 컨디션을 맞추면 진화하고 게이지가 비워진다")
    void evolvesWhenReady() {
        Tamagotchi p = full(EvolutionStage.BABY);
        assertThat(p.isEvolutionReady()).isTrue();

        p.evolveIfReady();

        assertThat(p.getEvolutionStage()).isEqualTo(EvolutionStage.CHILD);
        assertThat(p.getEvoGauge()).isZero();
    }

    @Test
    @DisplayName("게이지가 가득 차지 않았으면 진화하지 않는다")
    void notReadyWithoutFullGauge() {
        Tamagotchi p = pet(EvolutionStage.BABY, 80, 80, 0);
        p.addEvoGauge(9);
        assertThat(p.isEvolutionReady()).isFalse();
        assertThatThrownBy(p::evolveIfReady).isInstanceOf(IllegalStateException.class);
        assertThat(p.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
    }

    @Test
    @DisplayName("포만감·파워·응가 조건을 하나라도 못 맞추면 진화하지 않는다")
    void conditionsMustAllPass() {
        for (Tamagotchi p : new Tamagotchi[]{
                pet(EvolutionStage.BABY, 49, 80, 0),   // 포만감 부족
                pet(EvolutionStage.BABY, 80, 49, 0),   // 파워 부족
                pet(EvolutionStage.BABY, 80, 80, 1)}) { // 응가 있음
            p.addEvoGauge(10);
            assertThat(p.isEvolutionReady()).isFalse();
        }
        Tamagotchi border = pet(EvolutionStage.BABY, 50, 50, 0); // 경계값은 통과
        border.addEvoGauge(10);
        assertThat(border.isEvolutionReady()).isTrue();
    }

    @Test
    @DisplayName("스케치 → 완성작은 60칸을 채워야 한다")
    void childNeedsSixtyCells() {
        Tamagotchi p = pet(EvolutionStage.CHILD, 80, 80, 0);
        p.addEvoGauge(59);
        assertThat(p.isEvolutionReady()).isFalse();
        p.addEvoGauge(1);
        p.evolveIfReady();
        assertThat(p.getEvolutionStage()).isEqualTo(EvolutionStage.ADULT);
    }

    @Test
    @DisplayName("완성작과 사망한 캐릭터는 진화할 수 없다")
    void adultAndDeadCannotEvolve() {
        assertThat(full(EvolutionStage.ADULT).isEvolutionReady()).isFalse();
        Tamagotchi dead = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(20).strength(80)
                .evoGauge(10).deadAt(java.time.LocalDateTime.now()).build();
        assertThat(dead.isEvolutionReady()).isFalse();
    }

    @Test
    @DisplayName("부화하면 알이 낙서가 되고, 알이 아니면 부화할 수 없다")
    void hatch() {
        Tamagotchi egg = pet(EvolutionStage.EGG, 80, 80, 0);
        egg.hatch();
        assertThat(egg.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
        assertThatThrownBy(egg::hatch).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("훈련으로 받을 게이지는 대기만 하고, 받기 전에는 게이지가 오르지 않는다")
    void offerDoesNotRaiseGauge() {
        Tamagotchi p = pet(EvolutionStage.BABY, 80, 80, 0);
        p.addEvoGauge(4);

        p.offerEvoGain(2);

        assertThat(p.getPendingEvoGain()).isEqualTo(2);
        assertThat(p.getEvoGauge()).isEqualTo(4);
    }

    @Test
    @DisplayName("받으면 대기한 만큼만 오르고 대기는 비워진다 (광고를 봤다고 더 얹어 주지 않는다)")
    void claimAddsExactlyPending() {
        Tamagotchi p = pet(EvolutionStage.BABY, 80, 80, 0);
        p.addEvoGauge(4);
        p.offerEvoGain(2);

        int gained = p.claimEvoGain();

        assertThat(gained).isEqualTo(2);
        assertThat(p.getEvoGauge()).isEqualTo(6);
        assertThat(p.getPendingEvoGain()).isZero();
        assertThat(p.claimEvoGain()).isZero(); // 한 번 받으면 다시 받을 것이 없다
        assertThat(p.getEvoGauge()).isEqualTo(6);
    }

    @Test
    @DisplayName("최대 칸 수에 가까우면 실제로 오른 칸 수만 돌려준다")
    void claimIsClamped() {
        Tamagotchi p = pet(EvolutionStage.BABY, 80, 80, 0);
        p.addEvoGauge(9);
        p.offerEvoGain(3);

        assertThat(p.claimEvoGain()).isEqualTo(1);
        assertThat(p.getEvoGauge()).isEqualTo(10);
    }

    @Test
    @DisplayName("새 훈련을 하면 받지 않은 이전 대기분은 사라진다 (훈련 1번 = 광고 1번)")
    void newTrainingReplacesPending() {
        Tamagotchi p = pet(EvolutionStage.BABY, 80, 80, 0);
        p.offerEvoGain(3);
        p.offerEvoGain(1);

        assertThat(p.getPendingEvoGain()).isEqualTo(1);
    }

    @Test
    @DisplayName("진화할 수 없는 단계이거나 게이지가 가득 차 있으면 받을 것이 없다")
    void nothingToOfferWhenNotApplicable() {
        Tamagotchi egg = pet(EvolutionStage.EGG, 80, 80, 0);
        Tamagotchi adult = pet(EvolutionStage.ADULT, 80, 80, 0);
        Tamagotchi full = full(EvolutionStage.BABY);

        egg.offerEvoGain(3);
        adult.offerEvoGain(3);
        full.offerEvoGain(3);

        assertThat(egg.getPendingEvoGain()).isZero();
        assertThat(adult.getPendingEvoGain()).isZero();
        assertThat(full.getPendingEvoGain()).isZero();
    }

    @Test
    @DisplayName("진화하거나 새로 시작하면 대기 중이던 게이지도 비워진다")
    void evolveAndResetClearPending() {
        Tamagotchi p = full(EvolutionStage.BABY);
        p.evolveIfReady();
        assertThat(p.getPendingEvoGain()).isZero();

        Tamagotchi q = pet(EvolutionStage.BABY, 80, 80, 0);
        q.offerEvoGain(2);
        q.reset();
        assertThat(q.getPendingEvoGain()).isZero();
    }

    @Test
    @DisplayName("새로 시작하면 게이지도 비워진다")
    void resetClearsGauge() {
        Tamagotchi p = full(EvolutionStage.BABY);
        p.reset();
        assertThat(p.getEvoGauge()).isZero();
        assertThat(p.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
    }
}
