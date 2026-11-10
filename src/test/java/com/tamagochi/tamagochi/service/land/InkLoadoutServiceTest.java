package com.tamagochi.tamagochi.service.land;

import com.tamagochi.tamagochi.domain.land.LandInk;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.land.LandInkRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InkLoadoutServiceTest {

    TamagotchiRepository petRepo = mock(TamagotchiRepository.class);
    LandInkRepository inkRepo = mock(LandInkRepository.class);
    InkLoadoutService service = new InkLoadoutService(petRepo, inkRepo);
    User user = mock(User.class);
    Tamagotchi pet = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50).build();

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(1L);
        when(petRepo.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        when(inkRepo.findByUserIdOrderByFoundAtAsc(1L)).thenReturn(List.of(
                owned("cat"), owned("family.spirit"), owned("family.flower"), owned("family.beast")));
    }

    private LandInk owned(String id) {
        return new LandInk(user, id, Instant.EPOCH);
    }

    @Test
    void everythingIsOptionalAndEvolvesWithoutInk() {
        InkLoadoutService.Loadout l = service.set(user, null, List.of());
        assertThat(l.main()).isNull();
        assertThat(l.families()).isEmpty();
        assertThat(service.promptFor(user, "child")).isNull();
    }

    @Test
    void oneSpecificInkGoesInTheSingleSlot() {
        InkLoadoutService.Loadout l = service.set(user, "cat", List.of());
        assertThat(l.main().id()).isEqualTo("cat");
        assertThat(l.families()).isEmpty();
        assertThat(service.promptFor(user, "child")).contains("a cat's pointed ears");
    }

    @Test
    void aFamilyInkAloneIsAGoodInkToo() {
        InkLoadoutService.Loadout l = service.set(user, "family.beast", List.of());
        assertThat(l.main().id()).isEqualTo("family.beast");
        assertThat(service.promptFor(user, "child")).contains("beast traits");
    }

    @Test
    void oldAppFamilySlotsBecomeTheSingleSlotAndExtrasAreIgnored() {
        service.set(user, null, List.of("family.spirit", "family.flower"));
        assertThat(service.get(user).main().id()).isEqualTo("family.spirit");
        assertThat(service.promptFor(user, "child")).contains("spirit traits").doesNotContain("flower traits");

        service.set(user, "cat", List.of("family.spirit"));
        assertThat(service.promptFor(user, "child")).contains("a cat's pointed ears").doesNotContain("spirit traits");
    }

    @Test
    void rejectsUnownedOrUnknownInk() {
        assertThatThrownBy(() -> service.set(user, "tiger", List.of())).hasMessageContaining("찾지 못한");
        assertThatThrownBy(() -> service.set(user, "no-such-ink", List.of())).hasMessageContaining("알 수 없는");
    }

    @Test
    void evolvingClearsTheLoadout() {
        service.set(user, "cat", List.of());
        pet.evolve(EvolutionStage.CHILD);
        assertThat(service.get(user).main()).isNull();
        assertThat(service.promptFor(user, "adult")).isNull();
    }
}
