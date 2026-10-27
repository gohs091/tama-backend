package com.tamagochi.tamagochi.service.point;

import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VisitRewardServiceTest {

    PointWalletRepository repo = mock(PointWalletRepository.class);
    PointService pointService = mock(PointService.class);
    InkBathServiceTest.MutableClock clock = new InkBathServiceTest.MutableClock(Instant.parse("2026-10-28T01:00:00Z"));
    VisitRewardService service = new VisitRewardService(repo, pointService, clock);
    User user = mock(User.class);
    PointWallet wallet = PointWallet.builder().balance(0).build();

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(1L);
        when(repo.findByUserId(1L)).thenReturn(Optional.of(wallet));
    }

    @Test
    @DisplayName("처음 접속하면 바로 100을 받는다")
    void firstVisit_grants() {
        VisitRewardService.Status s = service.claim(user);

        assertThat(s.granted()).isTrue();
        verify(pointService).earnPoint(user, 100, "visit-1-0");
    }

    @Test
    @DisplayName("4시간이 안 지나면 받지 않고 남은 시간을 알려준다")
    void within4h_notGranted() {
        service.claim(user);
        clock.advanceMinutes(3 * 60);

        VisitRewardService.Status s = service.claim(user);

        assertThat(s.granted()).isFalse();
        assertThat(s.nextInSeconds()).isEqualTo(60 * 60);
        verify(pointService, times(1)).earnPoint(eq(user), anyInt(), anyString());
    }

    @Test
    @DisplayName("4시간이 지나 다시 접속하면 또 받는다 (키는 직전 보상 시각으로 만든다)")
    void after4h_grantsAgain() {
        service.claim(user);
        Instant first = clock.instant();
        clock.advanceMinutes(4 * 60);

        VisitRewardService.Status s = service.claim(user);

        assertThat(s.granted()).isTrue();
        verify(pointService).earnPoint(user, 100, "visit-1-" + first.toEpochMilli());
    }
}
