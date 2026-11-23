package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ImageQuotaServiceTest {

    private ImageQuotaService service;
    private StringRedisTemplate redis;
    private final Map<String, Long> counters = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(inv -> counters.merge(inv.getArgument(0), 1L, Long::sum));
        when(ops.decrement(anyString())).thenAnswer(inv -> counters.merge(inv.getArgument(0), -1L, Long::sum));

        service = new ImageQuotaService(redis);
        ReflectionTestUtils.setField(service, "perUserMonthlyLimit", 3);
        ReflectionTestUtils.setField(service, "globalLimit", 5);
    }

    @Test
    @DisplayName("유저별 한도 이내에서는 허용하고 초과하면 거부한다")
    void perUserLimit() {
        assertThat(service.tryAcquire("u1")).isTrue();
        assertThat(service.tryAcquire("u1")).isTrue();
        assertThat(service.tryAcquire("u1")).isTrue();
        assertThat(service.tryAcquire("u1")).isFalse();
    }

    @Test
    @DisplayName("화가 회원은 유저 한도를 넘어도 허용한다 (회원 여부는 한도를 넘었을 때만 확인)")
    void painterPassesPerUserLimit() {
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BooleanSupplier painter = () -> { asked.incrementAndGet(); return true; };
        for (int i = 0; i < 3; i++) assertThat(service.tryAcquire("p1", painter)).isTrue();
        assertThat(asked.get()).isZero();
        assertThat(service.tryAcquire("p1", painter)).isTrue();   // 4번째: 유저 한도(3)를 넘었지만 통과
        assertThat(asked.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("화가 회원도 전체 일일 한도는 넘지 못한다")
    void painterStillBoundByGlobalLimit() {
        for (int i = 0; i < 5; i++) assertThat(service.tryAcquire("p1", () -> true)).isTrue();
        assertThat(service.tryAcquire("p1", () -> true)).isFalse();  // 전체 6번째 → 거부
    }

    @Test
    @DisplayName("거절된 요청은 세지 않는다: 한도를 넘겨 여러 번 요청해도 횟수가 한도에서 멈춘다")
    void rejectedRequestsAreNotCounted() {
        for (int i = 0; i < 3; i++) service.tryAcquire("u1");
        for (int i = 0; i < 10; i++) assertThat(service.tryAcquire("u1")).isFalse();
        assertThat(userCount("u1")).isEqualTo(3L);
    }

    @Test
    @DisplayName("그리기에 실패해 돌려주면 그 시도는 세지 않는다 (만들어진 그림만 센다)")
    void releaseGivesBackFailedAttempt() {
        for (int i = 0; i < 3; i++) service.tryAcquire("u1");
        assertThat(service.tryAcquire("u1")).isFalse();
        service.release("u1");                              // 세 번 중 한 번이 실패로 끝났다
        assertThat(userCount("u1")).isEqualTo(2L);
        assertThat(service.tryAcquire("u1")).isTrue();     // 한 번 더 그릴 수 있다
        assertThat(service.tryAcquire("u1")).isFalse();
    }

    @Test
    @DisplayName("유저 한도는 달마다 센다 (키에 연-월이 들어간다)")
    void userLimitIsPerMonth() {
        service.tryAcquire("u1");
        assertThat(counters).containsKey("imagegen:user:u1:" + java.time.YearMonth.now());
    }

    private long userCount(String user) {
        return counters.getOrDefault("imagegen:user:" + user + ":" + java.time.YearMonth.now(), 0L);
    }

    @Test
    @DisplayName("한도는 유저마다 따로 센다")
    void limitsAreIndependentPerUser() {
        for (int i = 0; i < 3; i++) service.tryAcquire("u1");
        assertThat(service.tryAcquire("u1")).isFalse();
        assertThat(service.tryAcquire("u2")).isTrue();
    }

    @Test
    @DisplayName("전체 일일 한도를 넘으면 유저 한도가 남아 있어도 거부한다")
    void globalLimit() {
        assertThat(service.tryAcquire("a")).isTrue();
        assertThat(service.tryAcquire("a")).isTrue();
        assertThat(service.tryAcquire("b")).isTrue();
        assertThat(service.tryAcquire("b")).isTrue();
        assertThat(service.tryAcquire("c")).isTrue();   // 전체 5번째
        assertThat(service.tryAcquire("c")).isFalse();  // 전체 6번째 → 거부
    }

    @Test
    @DisplayName("카운터가 처음 생길 때만 만료 시간을 설정한다")
    void expireSetOnFirstIncrementOnly() {
        service.tryAcquire("u1");
        service.tryAcquire("u1");
        verify(redis, times(1)).expire(contains("imagegen:user:u1:"), eq(Duration.ofDays(40)));
    }

    @Test
    @DisplayName("제한을 끄면(로컬 개발) 아무리 호출해도 허용하고 Redis를 건드리지 않는다")
    void disabled_alwaysAllows() {
        ReflectionTestUtils.setField(service, "enabled", false);
        for (int i = 0; i < 100; i++) assertThat(service.tryAcquire("u1")).isTrue();
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("진화 그림은 단계마다 하루 2번(처음 1번 + 실패했을 때 다시 1번)까지만 그린다")
    void evolvedLimitIsFirstTryPlusOneRetry() {
        assertThat(service.tryAcquireEvolved("u1", "child")).isTrue();   // 처음 그리기
        assertThat(service.tryAcquireEvolved("u1", "child")).isTrue();   // 실패했을 때 다시 한 번
        assertThat(service.tryAcquireEvolved("u1", "child")).isFalse();  // 그 뒤로는 거절 (다시 그리기 몫은 없다)
        assertThat(service.tryAcquireEvolved("u1", "adult")).isTrue();   // 단계마다 따로 센다
        assertThat(service.tryAcquireEvolved("u2", "child")).isTrue();   // 유저마다 따로 센다
    }
}
