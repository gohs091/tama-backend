package com.tamagochi.tamagochi.service.battle;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
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
    TamagotchiRepository pets = mock(TamagotchiRepository.class);
    MeetBattleRecorder recorder = new MeetBattleRecorder(repo, users, points, pets);

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
        assertThat(win.host()).isEqualTo(MeetBattleRules.WIN_INK);
        assertThat(win.guest()).isEqualTo(MeetBattleRules.LOSE_INK);

        MeetBattleRecorder.Rewards quit = recorder.record(1L, 2L, 1, 2L, 10, 5);
        assertThat(quit.host()).isEqualTo(MeetBattleRules.WIN_INK);
        assertThat(quit.guest()).isZero();
    }

    @Test
    void 하루_세_판까지는_포만감_파워를_쓰지_않고_전적만_남긴다() {
        Tamagotchi a = mock(Tamagotchi.class), b = mock(Tamagotchi.class);
        when(pets.findByUserId(1L)).thenReturn(Optional.of(a));
        when(pets.findByUserId(2L)).thenReturn(Optional.of(b));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.countBattles(anyLong(), any())).thenReturn(2L); // 이번이 세 번째 판

        MeetBattleRecorder.Rewards r = recorder.record(1L, 2L, 1, null, 40, 20);

        verify(a).recordBattle(1);
        verify(b).recordBattle(-1);
        verify(a, never()).consumeForTraining(anyInt(), anyInt());
        assertThat(r.hostCharged()).isFalse();
        assertThat(r.hostFreeLeft()).isZero();
    }

    @Test
    void 네_번째_판부터는_포만감_파워를_쓴다() {
        Tamagotchi a = mock(Tamagotchi.class);
        when(pets.findByUserId(1L)).thenReturn(Optional.of(a));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.countBattles(eq(1L), any())).thenReturn(3L);

        MeetBattleRecorder.Rewards r = recorder.record(1L, 2L, -1, null, 10, 20);

        verify(a).consumeForTraining(MeetBattleRules.BATTLE_COST, MeetBattleRules.BATTLE_COST);
        assertThat(r.hostCharged()).isTrue();
    }

    @Test
    void 무료_판을_다_쓰고_컨디션이_모자라면_막는다() {
        Tamagotchi a = mock(Tamagotchi.class);
        when(pets.findByUserId(1L)).thenReturn(Optional.of(a));
        when(a.battleShortage(MeetBattleRules.BATTLE_COST)).thenReturn("배가 고파서");
        when(repo.countBattles(eq(1L), any())).thenReturn(2L);
        assertThat(recorder.blockReason(1L)).isNull();          // 아직 무료
        when(repo.countBattles(eq(1L), any())).thenReturn(3L);
        assertThat(recorder.blockReason(1L)).isEqualTo("배가 고파서");
    }
}
