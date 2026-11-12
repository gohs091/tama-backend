package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 서버가 미리 만든 단계 그림: 그 단계가 되는 순간 현재 그림이 된다 */
class PendingImageTest {

    private static Tamagotchi pet(EvolutionStage stage) {
        return Tamagotchi.builder().name("t").evolutionStage(stage).build();
    }

    @Test
    @DisplayName("알일 때 만든 낙서 그림은 맡아 두었다가 부화하는 순간 현재 그림이 된다")
    void babyImageIsAdoptedOnHatch() {
        Tamagotchi egg = pet(EvolutionStage.EGG);

        egg.keepGeneratedImage("baby", "public/pets/x/baby.aaaa1111.png");
        assertThat(egg.getCurrentImageKey()).isNull();   // 알은 아직 그림이 없다 (앱이 알을 낙서로 보여 주지 않게)

        egg.hatch();
        assertThat(egg.getCurrentImageKey()).isEqualTo("public/pets/x/baby.aaaa1111.png");
    }

    @Test
    @DisplayName("진화 후보는 진화하기 전까지 지금 그림을 바꾸지 않고, 다시 그리면 나중 것이 쓰인다")
    void candidateIsAdoptedOnEvolve() {
        Tamagotchi baby = pet(EvolutionStage.BABY);
        baby.saveStageImageKey("baby", "public/pets/x/baby.aaaa1111.png");

        baby.keepGeneratedImage("child", "public/pets/x/child.bbbb2222.png");
        baby.keepGeneratedImage("child", "public/pets/x/child.cccc3333.png");   // 다시 그리기
        assertThat(baby.getCurrentImageKey()).isEqualTo("public/pets/x/baby.aaaa1111.png");

        baby.evolve(EvolutionStage.CHILD);
        assertThat(baby.getCurrentImageKey()).isEqualTo("public/pets/x/child.cccc3333.png");
    }

    @Test
    @DisplayName("이미 그 단계인 끄적이의 그림을 만들면 바로 현재 그림이 된다")
    void sameStageImageReplacesCurrent() {
        Tamagotchi child = pet(EvolutionStage.CHILD);

        child.keepGeneratedImage("child", "public/pets/x/child.bbbb2222.png");

        assertThat(child.getCurrentImageKey()).isEqualTo("public/pets/x/child.bbbb2222.png");
    }

    @Test
    @DisplayName("다른 단계 몫으로 맡아 둔 그림은 쓰지 않고, 한 번 진화하면 비운다")
    void otherStageImageIsNotAdopted() {
        Tamagotchi baby = pet(EvolutionStage.BABY);
        baby.keepGeneratedImage("adult", "public/pets/x/adult.dddd4444.png");

        baby.evolve(EvolutionStage.CHILD);
        assertThat(baby.getCurrentImageKey()).isNull();

        baby.evolve(EvolutionStage.ADULT);
        assertThat(baby.getCurrentImageKey()).isNull();   // 앞에서 비웠다
    }

    @Test
    @DisplayName("새로 그리면(알로 돌아가면) 지난 그림으로 만들어 둔 것은 넘기지 않는다")
    void resetDropsPending() {
        Tamagotchi baby = pet(EvolutionStage.BABY);
        baby.keepGeneratedImage("child", "public/pets/x/child.bbbb2222.png");

        baby.reset();
        baby.hatch();
        baby.evolve(EvolutionStage.CHILD);

        assertThat(baby.getCurrentImageKey()).isNotEqualTo("public/pets/x/child.bbbb2222.png");
    }
}
