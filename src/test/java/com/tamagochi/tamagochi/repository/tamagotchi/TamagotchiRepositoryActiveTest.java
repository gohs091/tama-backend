package com.tamagochi.tamagochi.repository.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.PetStatus;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TamagotchiRepositoryActiveTest {

    @Autowired TamagotchiRepository pets;
    @Autowired UserRepository users;

    private Tamagotchi pet(User u, String name) {
        return Tamagotchi.builder().user(u).name(name).evolutionStage(EvolutionStage.EGG).hunger(50).strength(50).build();
    }

    @Test
    @DisplayName("한 유저가 여러 마리를 가질 수 있고, 지금 키우는 끄적이는 ACTIVE인 가장 최근 끄적이다")
    void multiplePets() {
        User u = users.save(User.builder().tossUserId("toss_multi").nickname("n").build());
        Tamagotchi first = pets.save(pet(u, "첫째"));
        Tamagotchi second = pets.save(pet(u, "둘째"));

        assertThat(pets.findActiveByUserId(u.getId())).get().extracting(Tamagotchi::getName).isEqualTo("둘째");

        ReflectionTestUtils.setField(second, "status", PetStatus.GONE);
        pets.save(second);
        assertThat(pets.findActiveByUserId(u.getId())).get().extracting(Tamagotchi::getId).isEqualTo(first.getId());
        assertThat(pets.findByPublicId(first.getPublicId())).isPresent();
    }
}
