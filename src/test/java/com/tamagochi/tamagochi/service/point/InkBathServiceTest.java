package com.tamagochi.tamagochi.service.point;

import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InkBathServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    PointWalletRepository repo = mock(PointWalletRepository.class);
    PointService pointService = mock(PointService.class);
    TamagotchiRepository petRepo = mock(TamagotchiRepository.class);
    Tamagotchi pet = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50).build();
    MutableClock clock = new MutableClock(Instant.parse("2026-10-27T01:00:00Z")); // KST 10:00
    InkBathService service = new InkBathService(repo, pointService, petRepo, clock);
    User user = mock(User.class);
    PointWallet wallet = PointWallet.builder().balance(100).build();

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(1L);
        when(repo.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(petRepo.findByUserId(1L)).thenReturn(Optional.of(pet));
    }

    @Test
    void startsOnceAndAccruesPerMinute() {
        InkBathService.Status s = service.start(user);
        assertThat(s.running()).isTrue();
        assertThat(s.remainingMinutes()).isEqualTo(240);

        clock.advanceMinutes(90);
        s = service.status(user);
        assertThat(s.elapsedMinutes()).isEqualTo(90);
        assertThat(s.accruedInk()).isEqualTo(45); // 30/hour * 1.5h
    }

    @Test
    void stoppingEarlyPaysAccruedAndEndsTheDay() {
        service.start(user);
        clock.advanceMinutes(120);
        InkBathService.Status s = service.stop(user);

        verify(pointService).earnPoint(eq(user), eq(60), anyString());
        assertThat(s.running()).isFalse();
        assertThat(s.canStart()).isFalse();
        assertThatThrownBy(() -> service.start(user)).hasMessageContaining("하루에 한 번");
    }

    @Test
    void paysAtMostFourHoursEvenIfCollectedLate() {
        service.start(user);
        clock.advanceMinutes(60 * 10);
        InkBathService.Status s = service.stop(user);

        verify(pointService).earnPoint(eq(user), eq(120), anyString());
        assertThat(s.paidInk()).isEqualTo(120);
    }

    @Test
    void forgottenBathIsSettledAndCanStartAgainNextDay() {
        service.start(user);
        clock.advanceMinutes(60 * 24); // 다음 날 같은 시각
        InkBathService.Status s = service.status(user);

        verify(pointService).earnPoint(eq(user), eq(120), anyString());
        assertThat(s.canStart()).isTrue();
        assertThat(service.start(user).running()).isTrue();
    }

    @Test
    void petIsFrozenWhileBathingAndReleasedOnStop() {
        service.start(user);
        assertThat(pet.isBathing()).isTrue();
        service.stop(user);
        assertThat(pet.isBathing()).isFalse();
    }

    @Test
    void cannotBathWhileSleeping() {
        pet.sleep(java.time.LocalDateTime.of(2026, 10, 23, 3, 0)); // 새벽 3시는 어느 끄적이에게나 졸린 시간
        assertThatThrownBy(() -> service.start(user)).hasMessageContaining("자고");
        assertThat(pet.isBathing()).isFalse();
    }

    @Test
    void cannotStartTwiceWhileRunning() {
        service.start(user);
        assertThatThrownBy(() -> service.start(user)).hasMessageContaining("이미");
    }

    @Test
    void stopWithoutBathPaysNothing() {
        service.stop(user);
        verify(pointService, never()).earnPoint(eq(user), anyInt(), anyString());
    }

    static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advanceMinutes(long m) { now = now.plusSeconds(m * 60); }
        @Override public ZoneId getZone() { return KST; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
