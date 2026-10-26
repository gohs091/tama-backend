package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class EvolutionAdBonusServiceTest {

    private TamagotchiRepository repository;
    private RedisTemplate<String, String> redis;
    private ValueOperations<String, String> ops;
    private EvolutionAdBonusService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(TamagotchiRepository.class);
        redis = mock(RedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(ops.increment(anyString())).thenReturn(1L);
        service = new EvolutionAdBonusService(repository, redis,
                Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        user = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();
    }

    private Tamagotchi pet(EvolutionStage stage, int gauge) {
        Tamagotchi t = Tamagotchi.builder().user(user).evolutionStage(stage).hunger(20).strength(80).build();
        t.addEvoGauge(gauge);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("광고 1번에 진화 게이지가 3칸 오른다")
    void grantsThreeCells() {
        Tamagotchi t = pet(EvolutionStage.BABY, 2);
        service.grant(user);
        assertThat(t.getEvoGauge()).isEqualTo(2 + EvolutionAdBonusService.GAUGE_PER_AD);
    }

    @Test
    @DisplayName("최대 칸 수를 넘지 않는다")
    void clampedAtMax() {
        Tamagotchi t = pet(EvolutionStage.BABY, 9);
        service.grant(user);
        assertThat(t.getEvoGauge()).isEqualTo(10);
    }

    @Test
    @DisplayName("이미 가득 찼거나 진화할 수 없는 단계, 죽은 캐릭터는 거절하고 하루 횟수도 쓰지 않는다")
    void invalidStatesDoNotConsumeLimit() {
        pet(EvolutionStage.BABY, 10);
        assertThatThrownBy(() -> service.grant(user)).isInstanceOf(IllegalStateException.class).hasMessageContaining("가득");

        pet(EvolutionStage.EGG, 0);
        assertThatThrownBy(() -> service.grant(user)).isInstanceOf(IllegalStateException.class);

        pet(EvolutionStage.ADULT, 0);
        assertThatThrownBy(() -> service.grant(user)).isInstanceOf(IllegalStateException.class);

        Tamagotchi dead = Tamagotchi.builder().user(user).evolutionStage(EvolutionStage.BABY).hunger(20).strength(80)
                .deadAt(LocalDateTime.now()).build();
        when(repository.findByUserId(1L)).thenReturn(Optional.of(dead));
        assertThatThrownBy(() -> service.grant(user)).isInstanceOf(IllegalStateException.class);

        verify(ops, never()).increment(anyString());
    }

    @Test
    @DisplayName("연속 호출(쿨다운 중)은 거절한다")
    void cooldown() {
        Tamagotchi t = pet(EvolutionStage.BABY, 0);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.grant(user)).isInstanceOf(IllegalStateException.class).hasMessageContaining("잠시");
        assertThat(t.getEvoGauge()).isZero();
    }

    @Test
    @DisplayName("하루 10번을 넘으면 거절한다")
    void dailyLimit() {
        Tamagotchi t = pet(EvolutionStage.CHILD, 0);
        when(ops.increment(anyString())).thenReturn((long) EvolutionAdBonusService.DAILY_LIMIT + 1);

        assertThatThrownBy(() -> service.grant(user)).isInstanceOf(IllegalStateException.class).hasMessageContaining("모두 받았");
        assertThat(t.getEvoGauge()).isZero();
    }

    @Test
    @DisplayName("하루 마지막 10번째까지는 받을 수 있다")
    void tenthIsAllowed() {
        Tamagotchi t = pet(EvolutionStage.CHILD, 0);
        when(ops.increment(anyString())).thenReturn((long) EvolutionAdBonusService.DAILY_LIMIT);

        service.grant(user);

        assertThat(t.getEvoGauge()).isEqualTo(EvolutionAdBonusService.GAUGE_PER_AD);
    }
}
