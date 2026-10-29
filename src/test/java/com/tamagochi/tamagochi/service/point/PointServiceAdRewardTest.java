package com.tamagochi.tamagochi.service.point;

import com.tamagochi.tamagochi.domain.point.PointTransaction;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.point.PointTransactionRepository;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class PointServiceAdRewardTest {

    private RedisTemplate<String, String> redis;
    private ValueOperations<String, String> ops;
    private PointService service;
    private PointWallet wallet;
    private User user;

    @BeforeEach
    void setUp() {
        PointWalletRepository walletRepository = mock(PointWalletRepository.class);
        PointTransactionRepository transactionRepository = mock(PointTransactionRepository.class);
        redis = mock(RedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        // 멱등 키 확인(처음 보는 요청)과 쿨다운(통과)은 기본으로 통과시킨다
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(ops.increment(anyString())).thenReturn(1L);

        service = new PointService(walletRepository, transactionRepository, redis, mock(IapOrderVerifier.class));

        user = User.builder().id(1L).tossUserId("toss_u1").nickname("u1").build();
        wallet = PointWallet.builder().user(user).balance(0).build();
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("기본 설정: 광고 한 번에 잉크 100, 다음 보상은 5분 뒤, 하루 5번")
    void defaults() {
        service.earnAdReward(user, "k1", 20_000L);

        assertThat(wallet.getBalance()).isEqualTo(100);
        verify(ops).setIfAbsent(eq("point:ad-cooldown:1"), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("쿨다운 중에 또 받으려 하면 거절한다 (광고를 연달아 보고 잉크를 쌓는 것 방지)")
    void cooldownBlocks() {
        when(ops.setIfAbsent(eq("point:ad-cooldown:1"), anyString(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.earnAdReward(user, "k2", 20_000L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("잠시 후");
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("하루 한도를 넘으면 거절하고, 쿨다운은 풀어 준다")
    void dailyLimitBlocks() {
        when(ops.increment(anyString())).thenReturn(6L);

        assertThatThrownBy(() -> service.earnAdReward(user, "k3", 20_000L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("모두 받았어요");
        assertThat(wallet.getBalance()).isZero();
        verify(redis).delete("point:ad-cooldown:1");
    }

    @Test
    @DisplayName("하루 마지막(5번째)까지는 받는다")
    void lastOneAllowed() {
        when(ops.increment(anyString())).thenReturn(5L);

        service.earnAdReward(user, "k4", 20_000L);

        assertThat(wallet.getBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("최소 시청 시간을 켜면, 앱이 알려 준 시청 시간이 짧은 요청은 거절한다 (모르는 요청은 통과)")
    void minWatch() {
        ReflectionTestUtils.setField(service, "adMinWatchMs", 5000L);

        assertThatThrownBy(() -> service.earnAdReward(user, "k5", 1200L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("끝까지");
        assertThat(wallet.getBalance()).isZero();
        verify(ops, never()).setIfAbsent(eq("point:ad-cooldown:1"), anyString(), anyLong(), any()); // 쿨다운도 쓰지 않는다

        service.earnAdReward(user, "k6", null);   // 이전 버전 앱은 시간을 못 보낸다
        assertThat(wallet.getBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("상태 조회: 남은 횟수와 다음 보상까지 남은 초")
    void status() {
        when(redis.getExpire("point:ad-cooldown:1", TimeUnit.SECONDS)).thenReturn(123L);
        when(ops.get(anyString())).thenReturn("2");

        PointService.AdRewardStatus s = service.adRewardStatus(user);

        assertThat(s.amount()).isEqualTo(100);
        assertThat(s.dailyLimit()).isEqualTo(5);
        assertThat(s.remainingToday()).isEqualTo(3);
        assertThat(s.nextInSeconds()).isEqualTo(123);
    }

    @Test
    @DisplayName("상태 조회: 기록이 없으면 바로 받을 수 있다")
    void statusFresh() {
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-2L);
        when(ops.get(anyString())).thenReturn(null);

        PointService.AdRewardStatus s = service.adRewardStatus(user);

        assertThat(s.remainingToday()).isEqualTo(5);
        assertThat(s.nextInSeconds()).isZero();
    }
}
