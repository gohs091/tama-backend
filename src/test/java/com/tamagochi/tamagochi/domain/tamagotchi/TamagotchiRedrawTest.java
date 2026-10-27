package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TamagotchiRedrawTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private Tamagotchi baby() {
        return Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50).build();
    }

    @Test
    @DisplayName("유체는 하루 2번까지 다시 그릴 수 있고, 다시 그리면 알로 돌아간다")
    void redraw_twicePerDay() {
        Tamagotchi pet = baby();
        assertThat(pet.redrawsLeft(TODAY)).isEqualTo(2);

        pet.redraw(TODAY);
        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        assertThat(pet.redrawsLeft(TODAY)).isEqualTo(1);

        pet.hatch();
        pet.redraw(TODAY);
        assertThat(pet.redrawsLeft(TODAY)).isZero();

        pet.hatch();
        assertThatThrownBy(() -> pet.redraw(TODAY)).isInstanceOf(IllegalStateException.class);
        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
    }

    @Test
    @DisplayName("다음 날이 되면 횟수가 다시 2번이 된다")
    void redraw_resetsNextDay() {
        Tamagotchi pet = baby();
        pet.redraw(TODAY);
        pet.hatch();
        pet.redraw(TODAY);
        pet.hatch();

        assertThat(pet.redrawsLeft(TODAY.plusDays(1))).isEqualTo(2);
        pet.redraw(TODAY.plusDays(1));
        assertThat(pet.redrawsLeft(TODAY.plusDays(1))).isEqualTo(1);
    }

    @Test
    @DisplayName("알이나 아이 단계는 다시 그릴 수 없다")
    void redraw_onlyBaby() {
        Tamagotchi egg = Tamagotchi.builder().evolutionStage(EvolutionStage.EGG).hunger(50).strength(50).build();
        Tamagotchi child = Tamagotchi.builder().evolutionStage(EvolutionStage.CHILD).hunger(50).strength(50).build();

        assertThatThrownBy(() -> egg.redraw(TODAY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> child.redraw(TODAY)).isInstanceOf(IllegalStateException.class);
        assertThat(child.redrawsLeft(TODAY)).isEqualTo(2);
    }
}
