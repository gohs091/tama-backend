package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import com.tamagochi.tamagochi.service.promotion.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TamagotchiServiceResetTest {

    private TamagotchiRepository repository;
    private TamagotchiService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(TamagotchiRepository.class);
        service = new TamagotchiService(repository, mock(PointService.class), mock(TrainingSessionService.class), mock(com.tamagochi.tamagochi.service.spar.SparDropService.class), mock(PromotionService.class), mock(com.tamagochi.tamagochi.service.membership.PainterMembershipService.class), mock(com.tamagochi.tamagochi.service.farm.FarmService.class), mock(com.tamagochi.tamagochi.service.tower.ExpeditionGuard.class), mock(com.tamagochi.tamagochi.service.quest.StarterQuestService.class));
        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
    }

    private Tamagotchi deadPet() {
        Tamagotchi pet = Tamagotchi.builder()
                .evolutionStage(EvolutionStage.CHILD)
                .faction("dragons-roar")
                .hunger(64).strength(38)
                .babyImageBase64("baby").childImageBase64("child")
                .poopCount(8)
                .deadAt(LocalDateTime.of(2026, 10, 23, 21, 0)) // 예전 규칙으로 떠난 행 (이제 새로 생기지 않는다)
                .build();
        return pet;
    }

    @Test
    @DisplayName("reset은 삭제 후 삽입하지 않고 기존 행을 제자리에서 초기화한다 (유니크 제약 충돌 방지)")
    void reset_resetsInPlace_noDeleteInsert() {
        Tamagotchi pet = deadPet();
        assertThat(pet.isDead()).isTrue();
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.of(pet));

        Tamagotchi result = service.reset(user);

        assertThat(result).isSameAs(pet);
        assertThat(result.isDead()).isFalse();
        assertThat(result.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        assertThat(result.getPoopCount()).isZero();
        assertThat(result.getHunger()).isEqualTo(Tamagotchi.INITIAL_HUNGER);
        assertThat(result.getStrength()).isEqualTo(Tamagotchi.INITIAL_STRENGTH);
        assertThat(result.getFaction()).isEqualTo("dragons-roar"); // 팩션 유지
        assertThat(result.getBabyImageBase64()).isNull();          // 이전 이미지 제거
        assertThat(result.getChildImageBase64()).isNull();
        verify(repository, never()).delete(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("다마고치가 없으면 새로 만든다")
    void reset_withoutExisting_creates() {
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(Tamagotchi.class))).thenAnswer(inv -> inv.getArgument(0));

        Tamagotchi result = service.reset(user);

        assertThat(result.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        verify(repository).save(any(Tamagotchi.class));
    }
}
