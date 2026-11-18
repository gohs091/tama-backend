package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.tower.ExpeditionGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 잘 그려진 진화 그림은 다시 그릴 수 없다 (이미지 비용). 예전 앱의 "다시 그리기 5/5" 버튼도 서버에서 막힌다 */
class RedrawServiceCandidateTest {

    private final TamagotchiRepository repository = mock(TamagotchiRepository.class);
    private final RedrawService service = new RedrawService(repository, Clock.systemUTC(), mock(ExpeditionGuard.class));
    private final User user = mock(User.class);

    @Test
    @DisplayName("진화 후보 다시 그리기 남은 횟수는 누구에게나 0/0이다")
    void candidateStatusIsAlwaysZero() {
        RedrawService.Status status = service.candidateStatus(user);
        assertThat(status.left()).isZero();
        assertThat(status.max()).isZero();
    }

    @Test
    @DisplayName("진화 후보 다시 그리기 요청은 거절하고 아무것도 바꾸지 않는다")
    void candidateRedrawIsRejected() {
        assertThatThrownBy(() -> service.candidateRedraw(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("다시 그릴 수 없어요");
        verifyNoInteractions(repository);
    }
}
