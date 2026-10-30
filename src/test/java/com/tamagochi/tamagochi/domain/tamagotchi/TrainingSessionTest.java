package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrainingSessionTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 26, 12, 0, 0);
    private static final User USER = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();

    private static TrainingSession session() {
        return new TrainingSession("s1", USER, T0);
    }

    @Test
    @DisplayName("정확히 5초가 지난 시점부터 제출할 수 있다")
    void boundaryAtFiveSeconds() {
        TrainingSession early = session();
        assertThatThrownBy(() -> early.use(T0.plusSeconds(5).minusNanos(1))).isInstanceOf(IllegalStateException.class);
        assertThat(early.isUsed()).isFalse();

        TrainingSession ok = session();
        ok.use(T0.plusSeconds(5));
        assertThat(ok.isUsed()).isTrue();
    }

    @Test
    @DisplayName("2분까지는 제출할 수 있고 넘으면 만료다")
    void boundaryAtTwoMinutes() {
        TrainingSession ok = session();
        ok.use(T0.plusMinutes(2));
        assertThat(ok.isUsed()).isTrue();

        TrainingSession late = session();
        assertThatThrownBy(() -> late.use(T0.plusMinutes(2).plusNanos(1))).isInstanceOf(IllegalStateException.class);
        assertThat(late.isExpired(T0.plusMinutes(2).plusSeconds(1))).isTrue();
        assertThat(late.isExpired(T0.plusMinutes(1))).isFalse();
    }

    @Test
    @DisplayName("한 번 쓴 세션은 다시 쓸 수 없다")
    void singleUse() {
        TrainingSession s = session();
        s.use(T0.plusSeconds(15));
        assertThatThrownBy(() -> s.use(T0.plusSeconds(20))).isInstanceOf(IllegalStateException.class);
    }
}
