package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 전설 잉크로 진화하면 반짝이가 켜지고, 그 끄적이는 계속 켜져 있다 */
class LegendGlowTest {

    private Tamagotchi baby() {
        return Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50).build();
    }

    @Test
    void 전설_잉크로_진화하면_반짝이가_켜지고_다음_진화에도_남는다() {
        Tamagotchi pet = baby();
        pet.setInkLoadout("phoenix", null, null); // 불사조의 잉크 (전설)
        pet.evolve(EvolutionStage.CHILD);
        assertThat(pet.legendGlow()).isTrue();
        pet.evolve(EvolutionStage.ADULT); // 잉크 없이 진화해도 꺼지지 않는다
        assertThat(pet.legendGlow()).isTrue();
    }

    @Test
    void 전설이_아닌_잉크나_잉크_없이는_켜지지_않는다() {
        Tamagotchi pet = baby();
        pet.setInkLoadout("lava", null, null); // 용암의 잉크 (흔함)
        pet.evolve(EvolutionStage.CHILD);
        assertThat(pet.legendGlow()).isFalse();
        pet.evolve(EvolutionStage.ADULT);
        assertThat(pet.legendGlow()).isFalse();
    }
}
