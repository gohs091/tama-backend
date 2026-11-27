package com.tamagochi.tamagochi.service.usage;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.usage.PetEvent;
import com.tamagochi.tamagochi.domain.usage.PetEventType;
import com.tamagochi.tamagochi.domain.usage.PlazaVisit;
import com.tamagochi.tamagochi.domain.usage.UserDailyVisit;
import com.tamagochi.tamagochi.repository.usage.PetEventRepository;
import com.tamagochi.tamagochi.repository.usage.PlazaVisitRepository;
import com.tamagochi.tamagochi.repository.usage.UserDailyVisitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 사용 기록은 원래 동작에 끼어들지 않는다: 예외를 던지지 않고, 커밋된 일만, 하루 접속은 한 줄만 남긴다 */
class UsageRecorderTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 테스트에서 시간을 움직이는 시계 (한국 시간) */
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-27T03:00:00Z");   // 한국 시간 11/27 12:00
        @Override public ZoneId getZone() { return KST; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final PetEventRepository petEvents = mock(PetEventRepository.class);
    private final PlazaVisitRepository plazaVisits = mock(PlazaVisitRepository.class);
    private final UserDailyVisitRepository dailyVisits = mock(UserDailyVisitRepository.class);
    private final MutableClock clock = new MutableClock();
    /** 넣자마자 그 자리에서 실행하는 실행기 */
    private final UsageRecorder recorder = new UsageRecorder(petEvents, plazaVisits, dailyVisits, clock, Runnable::run);

    @AfterEach
    void clearTx() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    private PetEvent savedEvent() {
        ArgumentCaptor<PetEvent> captor = ArgumentCaptor.forClass(PetEvent.class);
        verify(petEvents).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("끄적이 사건은 사용자 · 끄적이 ID · 종류 · 단계 · 까닭 · 한국 시간으로 남는다")
    void petEvent_savesFields() {
        recorder.petEvent(7L, "pub7", PetEventType.LEAVE, EvolutionStage.CHILD, "STARVE");

        PetEvent e = savedEvent();
        assertThat(e.getUserId()).isEqualTo(7L);
        assertThat(e.getPetPublicId()).isEqualTo("pub7");
        assertThat(e.getType()).isEqualTo("LEAVE");
        assertThat(e.getStage()).isEqualTo("CHILD");
        assertThat(e.getCause()).isEqualTo("STARVE");
        assertThat(e.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 27, 12, 0));
    }

    @Test
    @DisplayName("저장이 실패해도 예외가 밖으로 나가지 않는다")
    void saveFailure_isSwallowed() {
        when(petEvents.save(any())).thenThrow(new IllegalStateException("DB 가 죽었다"));
        when(plazaVisits.save(any())).thenThrow(new IllegalStateException("DB 가 죽었다"));
        when(dailyVisits.existsByUserIdAndVisitDate(any(), any())).thenThrow(new IllegalStateException("DB 가 죽었다"));

        assertThatCode(() -> {
            recorder.petEvent(1L, "p", PetEventType.HATCH, EvolutionStage.BABY, null);
            recorder.plazaVisit(1L, "3d", 0, 1000, 0, 0);
            recorder.visited(1L);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("기록 저장이 실패해도 부화 · 다시 시작은 그대로 된다")
    void saveFailure_doesNotBreakHatchOrReset() {
        when(petEvents.save(any())).thenThrow(new IllegalStateException("DB 가 죽었다"));
        com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository pets = mock(com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository.class);
        com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService service = new com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService(
                pets, mock(com.tamagochi.tamagochi.service.point.PointService.class),
                mock(com.tamagochi.tamagochi.service.tamagotchi.TrainingSessionService.class),
                mock(com.tamagochi.tamagochi.service.spar.SparDropService.class),
                mock(com.tamagochi.tamagochi.service.promotion.PromotionService.class),
                mock(com.tamagochi.tamagochi.service.membership.PainterMembershipService.class),
                mock(com.tamagochi.tamagochi.service.farm.FarmService.class),
                mock(com.tamagochi.tamagochi.service.tower.ExpeditionGuard.class),
                mock(com.tamagochi.tamagochi.service.quest.StarterQuestService.class), recorder);
        com.tamagochi.tamagochi.domain.user.User user = mock(com.tamagochi.tamagochi.domain.user.User.class);
        when(user.getId()).thenReturn(1L);
        Tamagotchi pet = Tamagotchi.builder().evolutionStage(EvolutionStage.EGG).hunger(50).strength(50).build();
        when(pets.findActiveByUserId(1L)).thenReturn(java.util.Optional.of(pet));

        service.hatch(user, "k", true);
        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);

        service.reset(user);
        assertThat(pet.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        verify(petEvents, times(2)).save(any());   // 두 번 다 저장을 해 봤고, 두 번 다 실패했다
    }

    @Test
    @DisplayName("실행기가 일을 받지 않아도(큐가 가득 참) 예외가 밖으로 나가지 않는다")
    void rejected_isSwallowed() {
        Executor full = r -> { throw new RejectedExecutionException("가득 참"); };
        UsageRecorder busy = new UsageRecorder(petEvents, plazaVisits, dailyVisits, clock, full);

        assertThatCode(() -> {
            busy.petEvent(1L, "p", PetEventType.HATCH, EvolutionStage.BABY, null);
            busy.plazaVisit(1L, "2d", 0, 1000, 0, 0);
            busy.visited(1L);
        }).doesNotThrowAnyException();
        verifyNoInteractions(petEvents, plazaVisits, dailyVisits);
    }

    @Test
    @DisplayName("부른 쪽은 저장을 기다리지 않는다: 일을 실행기에 넣기만 한다")
    void caller_doesNotWaitForSave() {
        List<Runnable> queued = new ArrayList<>();
        UsageRecorder async = new UsageRecorder(petEvents, plazaVisits, dailyVisits, clock, queued::add);

        async.petEvent(1L, "p", PetEventType.HATCH, EvolutionStage.BABY, null);
        async.visited(1L);

        verifyNoInteractions(petEvents, dailyVisits);
        assertThat(queued).hasSize(2);
        queued.forEach(Runnable::run);
        verify(petEvents).save(any());
        verify(dailyVisits).saveAndFlush(any());
    }

    @Test
    @DisplayName("트랜잭션 안에서 불리면 커밋된 뒤에 남긴다")
    void inTransaction_savesAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        recorder.petEvent(1L, "p", PetEventType.HATCH, EvolutionStage.BABY, null);

        verify(petEvents, never()).save(any());
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) s.afterCommit();
        verify(petEvents).save(any());
    }

    @Test
    @DisplayName("트랜잭션이 되돌려지면 남기지 않는다")
    void rolledBack_savesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        recorder.petEvent(1L, "p", PetEventType.HATCH, EvolutionStage.BABY, null);

        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(petEvents, never()).save(any());
    }

    @Test
    @DisplayName("사용자가 없는 끄적이의 사건은 남기지 않는다")
    void noUser_savesNothing() {
        recorder.petEvent(null, "p", PetEventType.LEAVE, EvolutionStage.BABY, "POOP");
        verifyNoInteractions(petEvents);
    }

    @Test
    @DisplayName("단계가 달라졌을 때만 진화를 남긴다. 새 단계가 적힌다")
    void stageChanged_recordsEvolveOnlyWhenChanged() {
        Tamagotchi pet = Tamagotchi.builder().evolutionStage(EvolutionStage.CHILD).hunger(50).strength(50).build();

        recorder.stageChanged(1L, pet, EvolutionStage.CHILD);
        verifyNoInteractions(petEvents);

        recorder.stageChanged(1L, pet, EvolutionStage.BABY);
        PetEvent e = savedEvent();
        assertThat(e.getType()).isEqualTo("EVOLVE");
        assertThat(e.getStage()).isEqualTo("CHILD");
        assertThat(e.getPetPublicId()).isEqualTo(pet.getPublicId());
        assertThat(e.getCause()).isNull();
    }

    @Test
    @DisplayName("광장 방문은 종류 · 들어온 때 · 나간 때(한국 시간) · 마릿수 · 말한 횟수로 남는다")
    void plazaVisit_savesFields() {
        long entered = Instant.parse("2026-11-27T03:00:00Z").toEpochMilli();

        recorder.plazaVisit(9L, "3d", entered, entered + 90_000, 4, 2);

        ArgumentCaptor<PlazaVisit> captor = ArgumentCaptor.forClass(PlazaVisit.class);
        verify(plazaVisits).save(captor.capture());
        PlazaVisit v = captor.getValue();
        assertThat(v.getUserId()).isEqualTo(9L);
        assertThat(v.getKind()).isEqualTo("3d");
        assertThat(v.getEnteredAt()).isEqualTo(LocalDateTime.of(2026, 11, 27, 12, 0));
        assertThat(v.getLeftAt()).isEqualTo(LocalDateTime.of(2026, 11, 27, 12, 1, 30));
        assertThat(v.getKills()).isEqualTo(4);
        assertThat(v.getChats()).isEqualTo(2);
    }

    @Test
    @DisplayName("하루에 여러 번 와도 한 줄만 남기고, 두 번째부터는 DB 를 부르지 않는다")
    void visited_oncePerDay() {
        recorder.visited(3L);
        recorder.visited(3L);
        recorder.visited(3L);

        ArgumentCaptor<UserDailyVisit> captor = ArgumentCaptor.forClass(UserDailyVisit.class);
        verify(dailyVisits, times(1)).existsByUserIdAndVisitDate(3L, LocalDate.of(2026, 11, 27));
        verify(dailyVisits, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(3L);
        assertThat(captor.getValue().getVisitDate()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 27, 12, 0));
    }

    @Test
    @DisplayName("날짜는 한국 시간으로 넘어간다: 자정이 지나면 그날의 줄을 새로 남긴다")
    void visited_newKoreanDay() {
        clock.now = Instant.parse("2026-11-27T14:59:00Z");   // 한국 시간 11/27 23:59
        recorder.visited(3L);
        clock.now = Instant.parse("2026-11-27T15:01:00Z");   // 한국 시간 11/28 00:01
        recorder.visited(3L);

        ArgumentCaptor<UserDailyVisit> captor = ArgumentCaptor.forClass(UserDailyVisit.class);
        verify(dailyVisits, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserDailyVisit::getVisitDate)
                .containsExactly(LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 28));
    }

    @Test
    @DisplayName("이미 오늘 줄이 있으면(서버가 다시 켜진 뒤) 또 넣지 않는다")
    void visited_alreadyInDb() {
        when(dailyVisits.existsByUserIdAndVisitDate(3L, LocalDate.of(2026, 11, 27))).thenReturn(true);

        recorder.visited(3L);

        verify(dailyVisits, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("유니크 제약에 걸려도(다른 곳에서 먼저 넣음) 조용히 넘어가고, 오늘은 다시 넣지 않는다")
    void visited_duplicateIsFine() {
        when(dailyVisits.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatCode(() -> recorder.visited(3L)).doesNotThrowAnyException();
        recorder.visited(3L);

        verify(dailyVisits, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("오늘 줄을 못 적었으면 다음 조회 때 다시 해 본다")
    void visited_retriesAfterFailure() {
        when(dailyVisits.existsByUserIdAndVisitDate(any(), any()))
                .thenThrow(new IllegalStateException("DB 가 죽었다"))
                .thenReturn(false);

        recorder.visited(3L);
        verify(dailyVisits, never()).saveAndFlush(any());

        recorder.visited(3L);
        verify(dailyVisits, times(1)).saveAndFlush(any());
    }
}
