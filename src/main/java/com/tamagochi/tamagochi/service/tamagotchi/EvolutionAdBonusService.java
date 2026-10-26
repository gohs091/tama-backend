package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionRules;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * 광고를 보고 진화 게이지를 받는다. 광고 1번 = 게이지 GAUGE_PER_AD칸.
 * 광고 시청 자체는 서버가 검증할 수 없어서(보상형 광고 SDK는 앱 안에서만 확인된다) 호출 간격과 하루 횟수로 상한을 둔다.
 * 값은 훈련 한 번의 순비용(소모한 먹이·약 잉크 - 보상)과 광고 한 번의 잉크 가치(100)를 맞춰 정했다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionAdBonusService {

    public static final int GAUGE_PER_AD = 3;
    public static final int DAILY_LIMIT = 10;
    static final long COOLDOWN_SECONDS = 4;

    private static final String COOLDOWN_KEY_PREFIX = "evo-ad:cooldown:";
    private static final String DAILY_KEY_PREFIX = "evo-ad:count:";

    private final TamagotchiRepository tamagotchiRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;

    @Transactional
    public Tamagotchi grant(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        // 횟수를 쓰기 전에 받을 수 있는 상태인지부터 확인한다 (받을 수 없는 요청이 하루 횟수를 깎지 않게)
        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        int max = EvolutionRules.gaugeMax(tamagotchi.getEvolutionStage());
        if (max == 0) {
            throw new IllegalStateException("진화 게이지를 쌓을 수 있는 단계가 아니에요.");
        }
        if (tamagotchi.getEvoGauge() >= max) {
            throw new IllegalStateException("진화 게이지가 이미 가득 찼어요.");
        }

        Boolean passed = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_KEY_PREFIX + user.getId(), "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(passed)) {
            throw new IllegalStateException("잠시 후 다시 시도해주세요.");
        }

        String countKey = DAILY_KEY_PREFIX + user.getId() + ":" + LocalDate.now(clock);
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(countKey, 25, TimeUnit.HOURS);
        }
        if (count != null && count > DAILY_LIMIT) {
            throw new IllegalStateException("오늘 광고로 받을 수 있는 진화 게이지를 모두 받았어요.");
        }

        tamagotchi.addEvoGauge(GAUGE_PER_AD);
        log.info("광고 진화 게이지 - userId: {}, gauge: {}", user.getId(), tamagotchi.getEvoGauge());
        return tamagotchi;
    }
}
