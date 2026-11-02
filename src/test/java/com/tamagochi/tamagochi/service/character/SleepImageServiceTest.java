package com.tamagochi.tamagochi.service.character;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SleepImageServiceTest {

    TamagotchiRepository repo = mock(TamagotchiRepository.class);
    PointService points = mock(PointService.class);
    SleepImageService service = new SleepImageService(repo, points);
    User user = mock(User.class);
    Tamagotchi pet = mock(Tamagotchi.class);

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(1L);
        when(repo.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        when(pet.getEvolutionStage()).thenReturn(EvolutionStage.CHILD);
    }

    @Test
    void 처음_만들면_잉크_1000개를_낸다() {
        when(pet.isSleepImageUnlocked()).thenReturn(false);

        SleepImageService.Start start = service.begin(user, "child", "k1");

        assertThat(start.charged()).isTrue();
        verify(points).usePoint(user, 1000, "sleep-image-k1");
        verify(pet).markSleepImage(EvolutionStage.CHILD);
    }

    @Test
    void 이미_값을_냈으면_다른_단계는_무료다() {
        when(pet.isSleepImageUnlocked()).thenReturn(true);

        SleepImageService.Start start = service.begin(user, "child", "k2");

        assertThat(start.charged()).isFalse();
        verify(points, never()).usePoint(any(User.class), anyInt(), anyString());
    }

    @Test
    void 같은_단계는_두_번_만들_수_없다() {
        when(pet.hasSleepImage(EvolutionStage.CHILD)).thenReturn(true);

        assertThatThrownBy(() -> service.begin(user, "child", "k3")).isInstanceOf(IllegalStateException.class);
        verify(points, never()).usePoint(any(User.class), anyInt(), anyString());
    }

    @Test
    void 잃어버린_이미지는_단계마다_한_번_무료로_다시_만든다() {
        when(pet.hasSleepImage(EvolutionStage.CHILD)).thenReturn(true);

        SleepImageService.Start start = service.begin(user, "child", "r1", true);

        assertThat(start.charged()).isFalse();
        assertThat(start.redo()).isTrue();
        verify(pet).markSleepImageRedo(EvolutionStage.CHILD);
        verify(points, never()).usePoint(any(User.class), anyInt(), anyString());
    }

    @Test
    void 다시_만들기는_한_번뿐이다() {
        when(pet.hasSleepImage(EvolutionStage.CHILD)).thenReturn(true);
        when(pet.hasSleepImageRedo(EvolutionStage.CHILD)).thenReturn(true);

        assertThatThrownBy(() -> service.begin(user, "child", "r2", true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 다시_만들다_실패하면_다시_만들기_기록만_지운다() {
        service.abort(user, new SleepImageService.Start(EvolutionStage.CHILD, false, true), "r3");

        verify(pet).clearSleepImageRedo(EvolutionStage.CHILD);
        verify(pet, never()).clearSleepImage(any());
        verify(points, never()).earnPoint(any(User.class), anyInt(), anyString());
    }

    @Test
    void 지금_단계가_아니면_만들_수_없다() {
        assertThatThrownBy(() -> service.begin(user, "adult", "k4")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 죽었으면_만들_수_없다() {
        when(pet.isDead()).thenReturn(true);

        assertThatThrownBy(() -> service.begin(user, "child", "k5")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 실패하면_단계_기록을_지우고_낸_잉크를_돌려준다() {
        service.abort(user, new SleepImageService.Start(EvolutionStage.CHILD, true), "k6");

        verify(pet).clearSleepImage(EvolutionStage.CHILD);
        verify(points).earnPoint(eq(user), eq(1000), eq("sleep-image-refund-k6"));
    }

    @Test
    void 무료로_만들다_실패하면_잉크는_돌려주지_않는다() {
        service.abort(user, new SleepImageService.Start(EvolutionStage.CHILD, false), "k7");

        verify(pet).clearSleepImage(EvolutionStage.CHILD);
        verify(points, never()).earnPoint(any(User.class), anyInt(), anyString());
    }
}
