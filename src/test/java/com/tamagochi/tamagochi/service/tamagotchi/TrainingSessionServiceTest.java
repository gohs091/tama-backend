package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TrainingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingSessionServiceTest {

    /** 테스트에서 시간을 직접 움직이는 시계 */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-26T03:00:00Z");

        void advanceSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private TrainingSessionRepository repository;
    private MutableClock clock;
    private TrainingSessionService service;
    private User me;
    private User other;
    private TrainingSession stored;

    @BeforeEach
    void setUp() {
        repository = mock(TrainingSessionRepository.class);
        clock = new MutableClock();
        service = new TrainingSessionService(repository, clock);
        me = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();
        other = User.builder().id(2L).tossUserId("toss_other").nickname("o").build();

        when(repository.save(any(TrainingSession.class))).thenAnswer(i -> {
            stored = i.getArgument(0);
            return stored;
        });
        when(repository.findFirstByUserIdAndUsedAtIsNullOrderByStartedAtDesc(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(stored).filter(s -> !s.isUsed()));
        when(repository.findById(any(String.class))).thenAnswer(i -> Optional.ofNullable(stored)
                .filter(s -> s.getId().equals(i.getArgument(0))));
    }

    @Test
    @DisplayName("시작 후 5초가 지나기 전에 결과를 내면 거절한다")
    void tooEarly() {
        TrainingSession s = service.start(me);
        clock.advanceSeconds(4);
        assertThatThrownBy(() -> service.consume(me, s.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("끝나지");
    }

    @Test
    @DisplayName("시작 후 5초가 지나면 한 번 제출할 수 있고, 같은 세션을 다시 내면 거절한다")
    void consumeOnce() {
        TrainingSession s = service.start(me);
        clock.advanceSeconds(10);
        assertThat(service.consume(me, s.getId()).isUsed()).isTrue();
        assertThatThrownBy(() -> service.consume(me, s.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("시작 후 2분이 넘으면 만료돼서 거절한다")
    void expired() {
        TrainingSession s = service.start(me);
        clock.advanceSeconds(121);
        assertThatThrownBy(() -> service.consume(me, s.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("남의 세션이나 없는 세션, 세션 없이 낸 결과는 거절한다")
    void wrongOwnerOrMissing() {
        TrainingSession s = service.start(me);
        clock.advanceSeconds(10);
        assertThatThrownBy(() -> service.consume(other, s.getId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.consume(me, "no-such-id")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.consume(me, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.consume(me, " ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("안 쓴 세션이 있으면 시작을 다시 눌러도 같은 세션을 돌려준다 (시작 시각을 늦추지 못한다)")
    void startReusesOpenSession() {
        TrainingSession first = service.start(me);
        clock.advanceSeconds(30);
        TrainingSession second = service.start(me);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("만료된 세션은 재사용하지 않고 새로 만든다")
    void startCreatesNewAfterExpiry() {
        TrainingSession first = service.start(me);
        clock.advanceSeconds(200);
        TrainingSession second = service.start(me);
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }
}
