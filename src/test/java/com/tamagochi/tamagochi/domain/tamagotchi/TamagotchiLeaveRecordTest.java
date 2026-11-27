package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi.LeaveCause;
import com.tamagochi.tamagochi.domain.usage.PetEventType;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.usage.UsageRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 떠남의 까닭은 떠남을 판정한 그 조건에서 나오고, 떠난 그 틱에 사건 기록으로 한 번 남는다 */
class TamagotchiLeaveRecordTest {

    // 포만감 6분/1, 파워 12분/1, 0인 채 4시간이면 떠남, 똥 60분/1, 8개면 떠남
    private static final VitalsRule RULE = new VitalsRule(6, 12, Duration.ofHours(4), 60, 8, Duration.ofDays(365));
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 27, 12, 0);

    private static Tamagotchi.TamagotchiBuilder pet(EvolutionStage stage) {
        return Tamagotchi.builder().evolutionStage(stage).hunger(50).strength(50);
    }

    @Test
    @DisplayName("굶주림으로 떠나면 까닭은 STARVE 다")
    void starve() {
        Tamagotchi pet = pet(EvolutionStage.BABY).hunger(100).hungryStartedAt(T0.minusHours(4)).build();
        pet.applyTime(T0, RULE);
        assertThat(pet.isDead()).isTrue();
        assertThat(pet.leftBecause()).containsExactly(LeaveCause.STARVE);
    }

    @Test
    @DisplayName("파워가 0인 채로 떠나면 까닭은 EXHAUST 다")
    void exhaust() {
        Tamagotchi pet = pet(EvolutionStage.CHILD).strength(0).strengthEmptySince(T0.minusHours(5)).build();
        pet.applyTime(T0, RULE);
        assertThat(pet.isDead()).isTrue();
        assertThat(pet.leftBecause()).containsExactly(LeaveCause.EXHAUST);
    }

    @Test
    @DisplayName("응가가 가득 차 떠나면 까닭은 POOP 이다")
    void poop() {
        Tamagotchi pet = pet(EvolutionStage.BABY).poopCount(8).build();
        pet.applyTime(T0, RULE);
        assertThat(pet.isDead()).isTrue();
        assertThat(pet.leftBecause()).containsExactly(LeaveCause.POOP);
    }

    @Test
    @DisplayName("같은 틱에 둘 이상이 맞으면 모두 남는다")
    void several() {
        Tamagotchi pet = pet(EvolutionStage.BABY).hunger(100).hungryStartedAt(T0.minusHours(6)).poopCount(8).build();
        pet.applyTime(T0, RULE);
        assertThat(pet.leftBecause()).containsExactly(LeaveCause.STARVE, LeaveCause.POOP);
    }

    @Test
    @DisplayName("떠나지 않았으면 까닭이 없고, 완성작은 떠나지 않는다")
    void alive_hasNoCause() {
        Tamagotchi baby = pet(EvolutionStage.BABY).build();
        baby.applyTime(T0, RULE);
        assertThat(baby.leftBecause()).isEmpty();

        Tamagotchi adult = pet(EvolutionStage.ADULT).hunger(100).hungryStartedAt(T0.minusHours(9)).poopCount(8).build();
        adult.applyTime(T0, RULE);
        assertThat(adult.isDead()).isFalse();
        assertThat(adult.leftBecause()).isEmpty();
    }

    @Test
    @DisplayName("다시 시작하면 까닭은 지워진다")
    void reset_clearsCause() {
        Tamagotchi pet = pet(EvolutionStage.BABY).poopCount(8).build();
        pet.applyTime(T0, RULE);
        pet.reset();
        assertThat(pet.leftBecause()).isEmpty();
    }

    // ── 스케줄러

    private static User user(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    @Test
    @DisplayName("스케줄러는 떠난 그 틱에 단계와 까닭을 사건 기록으로 남긴다")
    void scheduler_recordsLeaveOnce() {
        TamagotchiRepository repository = mock(TamagotchiRepository.class);
        UsageRecorder recorder = mock(UsageRecorder.class);
        TamagotchiScheduler scheduler = new TamagotchiScheduler(repository, RULE, recorder);
        LocalDateTime now = LocalDateTime.now();
        Tamagotchi starving = pet(EvolutionStage.CHILD).user(user(5L)).hunger(100).hungryStartedAt(now.minusHours(5)).build();
        Tamagotchi both = pet(EvolutionStage.BABY).user(user(6L)).strength(0).strengthEmptySince(now.minusHours(5)).poopCount(8).build();
        Tamagotchi fine = pet(EvolutionStage.BABY).user(user(7L)).build();
        when(repository.findAllByStatus(PetStatus.ACTIVE)).thenReturn(List.of(starving, both, fine));

        scheduler.tick();

        verify(recorder).petEvent(5L, starving.getPublicId(), PetEventType.LEAVE, EvolutionStage.CHILD, "STARVE");
        verify(recorder).petEvent(6L, both.getPublicId(), PetEventType.LEAVE, EvolutionStage.BABY, "EXHAUST+POOP");
        verify(recorder, never()).petEvent(org.mockito.ArgumentMatchers.eq(7L), any(), any(), any(), any());

        // 이미 떠난 끄적이는 다음 틱에 다시 남기지 않는다
        scheduler.tick();
        verify(recorder).petEvent(5L, starving.getPublicId(), PetEventType.LEAVE, EvolutionStage.CHILD, "STARVE");
        verify(recorder).petEvent(6L, both.getPublicId(), PetEventType.LEAVE, EvolutionStage.BABY, "EXHAUST+POOP");
    }
}
