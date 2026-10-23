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

        service = new ImageQuotaService(redis);
        ReflectionTestUtils.setField(service, "perUserLimit", 3);
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
        verify(redis, times(1)).expire(contains("imagegen:user:u1:"), eq(Duration.ofHours(48)));
    }

    @Test
    @DisplayName("제한을 끄면(로컬 개발) 아무리 호출해도 허용하고 Redis를 건드리지 않는다")
    void disabled_alwaysAllows() {
        ReflectionTestUtils.setField(service, "enabled", false);
        for (int i = 0; i < 100; i++) assertThat(service.tryAcquire("u1")).isTrue();
        verifyNoInteractions(redis);
    }
}
