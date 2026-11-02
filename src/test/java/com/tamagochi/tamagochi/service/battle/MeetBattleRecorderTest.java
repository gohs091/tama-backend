package com.tamagochi.tamagochi.service.battle;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.battle.MeetBattleRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetBattleRecorderTest {

    MeetBattleRepository repo = mock(MeetBattleRepository.class);
    UserRepository users = mock(UserRepository.class);
    PointService points = mock(PointService.class);
    MeetBattleRecorder recorder = new MeetBattleRecorder(repo, users, points);

    @Test
    void 같은_친구와_하루_보상_횟수를_넘기면_잉크를_주지_않는다() {
        when(repo.countPairRewarded(eq(1L), eq(2L), any())).thenReturn((long) MeetBattleRules.PAIR_DAILY_REWARDED);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MeetBattleRecorder.Rewards r = recorder.record(1L, 2L, 1, null, 40, 20);

        assertThat(r.host()).isZero();
        assertThat(r.guest()).isZero();
        verify(points, never()).earnPoint(any(), anyInt(), anyString());
    }

    @Test
    void 이기면_30_지면_10_나간_사람은_0() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(users.findById(anyLong())).thenReturn(Optional.of(mock(User.class)));

        MeetBattleRecorder.Rewards win = recorder.record(1L, 2L, 1, null, 40, 20);
        assertThat(win).isEqualTo(new MeetBattleRecorder.Rewards(MeetBattleRules.WIN_INK, MeetBattleRules.LOSE_INK));

        MeetBattleRecorder.Rewards quit = recorder.record(1L, 2L, 1, 2L, 10, 5);
        assertThat(quit).isEqualTo(new MeetBattleRecorder.Rewards(MeetBattleRules.WIN_INK, 0));
    }
}
