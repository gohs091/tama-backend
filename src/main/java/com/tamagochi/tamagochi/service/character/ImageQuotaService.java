package com.tamagochi.tamagochi.service.character;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 이미지 생성(유료 API) 호출 횟수 제한 — 유저별 하루 한도 + 서비스 전체 하루 한도.
 * 한도를 넘으면 생성 요청을 거부해 비용 폭주를 막는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageQuotaService {

    private final StringRedisTemplate redis;

    // 로컬 개발에서는 false로 꺼서 테스트가 한도에 막히지 않게 한다 (운영은 항상 true)
    @Value("${image.quota-enabled:true}")
    private boolean enabled = true;

    @Value("${image.daily-limit-per-user:8}")
    private int perUserLimit;

    @Value("${image.daily-limit-global:2000}")
    private int globalLimit;

    /** 호출 1회를 소모한다. 한도 이내면 true, 초과면 false. */
    public boolean tryAcquire(String tossUserId) {
        if (!enabled) return true;

        String day = LocalDate.now().toString();

        long userCount = increment("imagegen:user:" + tossUserId + ":" + day);
        if (userCount > perUserLimit) {
            log.warn("이미지 생성 유저 한도 초과 - user: {}, count: {}", tossUserId, userCount);
            return false;
        }

        long globalCount = increment("imagegen:global:" + day);
        if (globalCount > globalLimit) {
            log.error("이미지 생성 전체 일일 한도 초과 - count: {}", globalCount);
            return false;
        }
        return true;
    }

    private long increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count == null) throw new IllegalStateException("Redis 카운터 증가 실패: " + key);
        if (count == 1L) redis.expire(key, Duration.ofHours(48));
        return count;
    }
}
