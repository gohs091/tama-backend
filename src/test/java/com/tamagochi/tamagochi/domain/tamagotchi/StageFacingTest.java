package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 단계 그림이 바라보는 쪽: 그림과 같은 규칙으로 끄적이에 남는다 */
class StageFacingTest {

    private static Tamagotchi pet(EvolutionStage stage) {
        return Tamagotchi.builder().name("t").evolutionStage(stage).build();
    }

    @Test
    @DisplayName("알일 때 만든 낙서 그림의 방향은 맡아 두었다가 부화하는 순간 현재 방향이 된다")
    void babyFacingIsAdoptedOnHatch() {
        Tamagotchi egg = pet(EvolutionStage.EGG);

        egg.keepGeneratedFacing("baby", "left");
        assertThat(egg.getCurrentFacing()).isNull();

        egg.hatch();
        assertThat(egg.getCurrentFacing()).isEqualTo("left");
    }

    @Test
    @DisplayName("진화 후보의 방향은 진화하기 전까지 지금 방향을 바꾸지 않고, 다시 그리면 나중 것이 쓰인다")
    void candidateFacingIsAdoptedOnEvolve() {
        Tamagotchi baby = pet(EvolutionStage.BABY);
        baby.keepGeneratedFacing("baby", "left");

        baby.keepGeneratedFacing("child", "left");
        baby.keepGeneratedFacing("child", "right");   // 다시 그리기
        assertThat(baby.getCurrentFacing()).isEqualTo("left");

        baby.evolve(EvolutionStage.CHILD);
        assertThat(baby.getCurrentFacing()).isEqualTo("right");
    }

    @Test
    @DisplayName("지금 단계 그림을 다시 만들었는데 방향을 분석하지 못하면 앞 그림의 방향을 남기지 않는다")
    void unknownFacingClearsOldOne() {
        Tamagotchi child = pet(EvolutionStage.CHILD);
        child.keepGeneratedFacing("child", "left");

        child.keepGeneratedFacing("child", null);
        assertThat(child.getCurrentFacing()).isNull();

        child.keepGeneratedFacing("child", "up");   // 알 수 없는 값
        assertThat(child.getCurrentFacing()).isNull();
    }

    @Test
    @DisplayName("새로 그리면(초기화) 방향도 지워진다")
    void resetClearsFacing() {
        Tamagotchi baby = pet(EvolutionStage.BABY);
        baby.keepGeneratedFacing("baby", "right");
        baby.keepGeneratedFacing("child", "left");

        baby.reset();
        baby.hatch();

        assertThat(baby.getCurrentFacing()).isNull();
    }
}
