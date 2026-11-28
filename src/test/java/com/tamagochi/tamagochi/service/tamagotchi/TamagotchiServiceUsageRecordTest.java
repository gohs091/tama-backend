package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.LevelRules;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.usage.PetEventType;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import com.tamagochi.tamagochi.service.promotion.PromotionService;
import com.tamagochi.tamagochi.service.tower.ExpeditionGuard;
import com.tamagochi.tamagochi.service.usage.UsageRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 부화 · 진화 · 다시 시작 · 다시 그리기 때 끄적이 사건이 한 줄씩 남는다. 거절된 동작은 남기지 않는다 */
class TamagotchiServiceUsageRecordTest {

    private final TamagotchiRepository repository = mock(TamagotchiRepository.class);
    private final PointService pointService = mock(PointService.class);
    private final UsageRecorder recorder = mock(UsageRecorder.class);
    private final User user = mock(User.class);
    private TamagotchiService service;

    @BeforeEach
    void setUp() {
        service = service(recorder);
        when(user.getId()).thenReturn(1L);
    }

    private TamagotchiService service(UsageRecorder usageRecorder) {
        return new TamagotchiService(repository, pointService, mock(TrainingSessionService.class),
                mock(com.tamagochi.tamagochi.service.spar.SparDropService.class), mock(PromotionService.class),
                mock(com.tamagochi.tamagochi.service.membership.PainterMembershipService.class),
                mock(com.tamagochi.tamagochi.service.farm.FarmService.class), mock(ExpeditionGuard.class),
                mock(com.tamagochi.tamagochi.service.quest.StarterQuestService.class), usageRecorder);
    }

    private Tamagotchi keep(Tamagotchi pet) {
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        return pet;
    }

    private static Tamagotchi egg() {
        return Tamagotchi.builder().evolutionStage(EvolutionStage.EGG).hunger(50).strength(50).build();
    }

    /** 진화 가능 구간에 들어선 낙서 */
    private static Tamagotchi babyInZone() {
        int level = LevelRules.zoneStart(EvolutionStage.BABY);
        return Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50)
                .level(level).exp(0).stageStartLevel(LevelRules.START_LEVEL).pendingExp(0)
                .weightDg(LevelRules.START_WEIGHT_DG).might(0).build();
    }

    @Test
    @DisplayName("알을 깨우면 부화(HATCH)가 낙서 단계로 남는다")
    void hatch_records() {
        Tamagotchi pet = keep(egg());

        service.hatch(user, "k", true);

        verify(recorder).petEvent(1L, pet.getPublicId(), PetEventType.HATCH, EvolutionStage.BABY, null);
    }

    @Test
    @DisplayName("깨우지 못했으면(붉은 잉크 부족) 남기지 않는다")
    void hatch_rejected_recordsNothing() {
        Tamagotchi pet = keep(egg());
        service.hatch(user, "k1", true);
        service.reset(user);
        org.mockito.Mockito.clearInvocations(recorder);
        doThrow(new IllegalStateException("붉은 잉크가 부족합니다.")).when(pointService).useRedInk(any(), anyInt(), any());

        assertThatThrownBy(() -> service.hatch(user, "k2", true)).isInstanceOf(IllegalStateException.class);

        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("직접 진화시키면 진화(EVOLVE)가 새 단계로 남는다")
    void evolve_records() {
        Tamagotchi pet = keep(babyInZone());

        service.evolve(user);

        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.CHILD);
        verify(recorder).stageChanged(1L, pet, EvolutionStage.BABY);
    }

    @Test
    @DisplayName("진화할 수 없으면 남기지 않는다")
    void evolve_rejected_recordsNothing() {
        keep(Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50).build());

        assertThatThrownBy(() -> service.evolve(user)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("다시 시작하면 그만둔 끄적이의 ID 와 그때 단계로 RESTART 가 남는다 (새 ID 가 아니다)")
    void reset_recordsOldPet() {
        Tamagotchi pet = keep(Tamagotchi.builder().evolutionStage(EvolutionStage.CHILD).hunger(64).strength(38)
                .deadAt(LocalDateTime.of(2026, 11, 20, 9, 0)).build());
        String oldId = pet.getPublicId();

        service.reset(user);

        assertThat(pet.getPublicId()).isNotEqualTo(oldId);
        verify(recorder).petEvent(1L, oldId, PetEventType.RESTART, EvolutionStage.CHILD, null);
    }

    @Test
    @DisplayName("끄적이가 없어 새로 만들 때는 다시 시작이 아니다")
    void reset_withoutPet_recordsNothing() {
        when(repository.findActiveByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(Tamagotchi.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reset(user);

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("다시 그리면 다시 그린 그 낙서의 ID 로 REDRAW 가 남는다")
    void redraw_recordsOldPet() {
        Tamagotchi pet = keep(Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50).build());
        String oldId = pet.getPublicId();
        RedrawService redrawService = new RedrawService(repository, Clock.systemUTC(), mock(ExpeditionGuard.class), recorder);

        redrawService.redraw(user);

        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        verify(recorder).petEvent(1L, oldId, PetEventType.REDRAW, EvolutionStage.BABY, null);
    }

    @Test
    @DisplayName("다시 그릴 수 없으면(낙서가 아님) 남기지 않는다")
    void redraw_rejected_recordsNothing() {
        keep(Tamagotchi.builder().evolutionStage(EvolutionStage.CHILD).hunger(50).strength(50).build());
        RedrawService redrawService = new RedrawService(repository, Clock.systemUTC(), mock(ExpeditionGuard.class), recorder);

        assertThatThrownBy(() -> redrawService.redraw(user)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("경험치를 받다 구간 끝을 넘겨 저절로 진화하면 진화가 남는다")
    void claim_autoEvolve_records() {
        int end = LevelRules.zoneEnd(EvolutionStage.BABY);
        Tamagotchi pet = keep(Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50)
                .level(end).exp(0).stageStartLevel(LevelRules.START_LEVEL).pendingExp(LevelRules.ZONE_EXP_PER_LEVEL)
                .weightDg(LevelRules.START_WEIGHT_DG).might(0).build());
        EvolutionGaugeClaimService claimService = new EvolutionGaugeClaimService(repository, recorder);

        claimService.claim(user);

        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.CHILD);
        verify(recorder).stageChanged(1L, pet, EvolutionStage.BABY);
    }
}
