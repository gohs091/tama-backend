package com.tamagochi.tamagochi.service.character;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.BooleanSupplier;

/**
 * 이미지 생성(유료 API) 횟수 제한 — 유저별 한 달 한도 + 서비스 전체 하루 한도.
 * 한도를 넘으면 생성 요청을 거부해 비용 폭주를 막는다.
 * 만들어진 그림만 센다: 요청할 때 한 칸을 잡아 두고(tryAcquire), 그리기에 실패하면 돌려준다(release).
 * 거절된 요청은 세지 않는다. (요청할 때 잡아 두는 것은 한꺼번에 여러 번 요청해 한도를 넘기는 것을 막기 위해서다)
 * 화가 회원은 유저별 한도에 걸리지 않는다 (서비스 전체 한도와 진화 그림 단계별 한도는 똑같이 적용).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageQuotaService {

    private final StringRedisTemplate redis;

    // 로컬 개발에서는 false로 꺼서 테스트가 한도에 막히지 않게 한다 (운영은 항상 true)
    @Value("${image.quota-enabled:true}")
    private boolean enabled = true;

    @Value("${image.monthly-limit-per-user:30}")
    private int perUserMonthlyLimit;

    @Value("${image.daily-limit-global:2000}")
    private int globalLimit;

    private static final Duration USER_KEY_TTL = Duration.ofDays(40);   // 달마다 키가 달라서 그 달이 지나면 쓰이지 않는다
    private static final Duration DAY_KEY_TTL = Duration.ofHours(48);

    /** 한 칸을 잡는다. 한도 이내면 true, 초과면 false (초과면 세지 않는다). */
    public boolean tryAcquire(String tossUserId) {
        return tryAcquire(tossUserId, () -> false);
    }

    /**
     * 한 칸을 잡는다. 한도 이내면 true, 초과면 false (초과면 세지 않는다). 그리기에 실패하면 release로 돌려준다.
     * painter: 화가 회원인지. 유저 한도를 넘었을 때만 물어본다 (회원이면 유저 한도를 넘어도 통과, 전체 한도는 그대로).
     */
    public boolean tryAcquire(String tossUserId, BooleanSupplier painter) {
        if (!enabled) return true;

        String userKey = userKey(tossUserId);
        long userCount = increment(userKey, USER_KEY_TTL);
        if (userCount > perUserMonthlyLimit) {
            if (!painter.getAsBoolean()) {
                redis.opsForValue().decrement(userKey);
                log.warn("이미지 생성 유저 한 달 한도 초과 - user: {}, limit: {}", tossUserId, perUserMonthlyLimit);
                return false;
            }
            log.info("이미지 생성 유저 한도 넘김(화가 회원) - user: {}, count: {}", tossUserId, userCount);
        }

        String globalKey = globalKey();
        long globalCount = increment(globalKey, DAY_KEY_TTL);
        if (globalCount > globalLimit) {
            redis.opsForValue().decrement(globalKey);
            redis.opsForValue().decrement(userKey);
            log.error("이미지 생성 전체 일일 한도 초과 - limit: {}", globalLimit);
            return false;
        }
        return true;
    }

    /** 잡아 둔 한 칸을 돌려준다: 그림을 만들지 못했을 때 (실패한 시도는 한도에 세지 않는다) */
    public void release(String tossUserId) {
        if (!enabled || tossUserId == null || tossUserId.isBlank()) return;
        try {
            redis.opsForValue().decrement(userKey(tossUserId));
            redis.opsForValue().decrement(globalKey());
        } catch (RuntimeException e) {
            log.warn("이미지 생성 한도 돌려주기 실패 - user: {}, {}", tossUserId, e.getMessage());
        }
    }

    private static String userKey(String tossUserId) {
        return "imagegen:user:" + tossUserId + ":" + YearMonth.now();
    }

    private static String globalKey() {
        return "imagegen:global:" + LocalDate.now();
    }

    /** 진화 그림(child·adult)은 단계마다 하루 이만큼만 그린다: 처음 1번 + 그리기에 실패했을 때 다시 1번. 성공한 그림을 다시 그리는 몫은 없다 */
    public static final int EVOLVED_BASE_PER_STAGE = 2;

    /**
     * 진화 그림 1회를 소모한다. 단계별 하루 한도 안이면 true.
     * 잉크를 갈아 끼우는 식으로 앱이 후보를 계속 다시 그리게 만들어도 여기서 막힌다.
     * (여기는 시도 횟수를 센다: 실패했을 때의 재시도 1번이 이미 몫에 들어 있다)
     */
    public boolean tryAcquireEvolved(String tossUserId, String stage) {
        if (!enabled) return true;
        String day = LocalDate.now().toString();
        long count = increment("imagegen:evolved:" + tossUserId + ":" + stage + ":" + day, DAY_KEY_TTL);
        int limit = EVOLVED_BASE_PER_STAGE;
        if (count > limit) {
            log.warn("진화 그림 단계 한도 초과 - user: {}, stage: {}, count: {}, limit: {}", tossUserId, stage, count, limit);
            return false;
        }
        return true;
    }

    private long increment(String key, Duration ttl) {
        Long count = redis.opsForValue().increment(key);
        if (count == null) throw new IllegalStateException("Redis 카운터 증가 실패: " + key);
        if (count == 1L) redis.expire(key, ttl);
        return count;
    }
}
