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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class TamagotchiServiceResetTest {

    private TamagotchiRepository repository;
    private TamagotchiService service;
    private PointService pointService;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(TamagotchiRepository.class);
        pointService = mock(PointService.class);
        service = new TamagotchiService(repository, pointService, mock(TrainingSessionService.class), mock(com.tamagochi.tamagochi.service.spar.SparDropService.class), mock(PromotionService.class), mock(com.tamagochi.tamagochi.service.membership.PainterMembershipService.class), mock(com.tamagochi.tamagochi.service.farm.FarmService.class), mock(com.tamagochi.tamagochi.service.tower.ExpeditionGuard.class), mock(com.tamagochi.tamagochi.service.quest.StarterQuestService.class), mock(com.tamagochi.tamagochi.service.usage.UsageRecorder.class));
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

    private Tamagotchi egg() {
        return Tamagotchi.builder().evolutionStage(EvolutionStage.EGG).hunger(50).strength(50).build();
    }

    @Test
    @DisplayName("첫 알은 붉은 잉크 없이 깨운다")
    void firstEgg_hatchesFree() {
        Tamagotchi pet = egg();
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        assertThat(pet.hatchFree()).isTrue();

        service.hatch(user, "k", true);

        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
        assertThat(pet.hatchCount()).isEqualTo(1);
        verify(pointService, never()).useRedInk(any(), anyInt(), any());
    }

    @Test
    @DisplayName("새 알로 다시 시작하면 그 알부터는 붉은 잉크 1개를 받는다 (알마다 한 번)")
    void secondEgg_costsOneRedInk() {
        Tamagotchi pet = egg();
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        service.hatch(user, "k1", true);
        service.reset(user);
        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        assertThat(pet.hatchFree()).isFalse();

        service.hatch(user, "k2", true);

        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
        assertThat(pet.hatchCount()).isEqualTo(2);
        verify(pointService, times(1)).useRedInk(user, Tamagotchi.HATCH_RED_INK, "hatch-" + pet.getPublicId());
    }

    @Test
    @DisplayName("붉은 잉크가 모자라면 깨우지 않는다")
    void secondEgg_withoutRedInk_staysEgg() {
        Tamagotchi pet = egg();
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        service.hatch(user, "k1", true);
        service.reset(user);
        doThrow(new IllegalStateException("붉은 잉크가 부족합니다.")).when(pointService).useRedInk(any(), anyInt(), any());

        assertThatThrownBy(() -> service.hatch(user, "k2", true)).isInstanceOf(IllegalStateException.class);

        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        assertThat(pet.hatchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("예전 앱의 요청(붉은 잉크를 모른다)은 두 번째 알이어도 받지 않고 깨운다")
    void oldApp_isNotCharged() {
        Tamagotchi pet = egg();
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        service.hatch(user, "k1", false);
        service.reset(user);

        service.hatch(user, "k2", false);

        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
        verify(pointService, never()).useRedInk(any(), anyInt(), any());
    }

    @Test
    @DisplayName("깨운 횟수 열이 생기기 전의 행: 이미 깨어난 끄적이가 다시 시작하면 다음 알은 붉은 잉크가 든다")
    void legacyRow_alreadyHatched_nextEggCosts() {
        Tamagotchi pet = deadPet();   // 스케치 단계, hatchCount 없음
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        assertThat(pet.hatchCount()).isEqualTo(1);

        service.reset(user);

        assertThat(pet.hatchFree()).isFalse();
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
