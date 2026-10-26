package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionOutcome;
import com.tamagochi.tamagochi.domain.promotion.PromotionRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 완전체 트레이닝 결과에 따른 프로모션 대상 판정과 기록.
 * mode=off 이면 아무것도 하지 않고, record 이면 대상 여부를 판정해 기록만 남긴다(실제 지급은 아직 연동 전).
 */
@Service
@Slf4j
public class PromotionService {

    public enum Mode { OFF, RECORD }

    private final PromotionGrantRepository repository;
    private final Clock clock;
    private final Mode mode;

    public PromotionService(PromotionGrantRepository repository, Clock clock,
                            @Value("${tamagotchi.promotion.mode:off}") String mode) {
        this.repository = repository;
        this.clock = clock;
        this.mode = "record".equalsIgnoreCase(mode) ? Mode.RECORD : Mode.OFF;
    }

    public boolean isEnabled() {
        return mode != Mode.OFF;
    }

    /** 이번 트레이닝(세션)의 프로모션 결과를 정한다. 꺼져 있으면 null. */
    @Transactional
    public PromotionOutcome evaluate(User user, String trainingSessionId, int clickCount) {
        if (!isEnabled()) return null;

        LocalDate today = LocalDate.now(clock);
        long usedToday = repository.countByUserIdAndGrantDate(user.getId(), today);
        int remaining = (int) Math.max(0, PromotionRules.DAILY_LIMIT - usedToday);

        int tier = PromotionRules.tierForTaps(clickCount);
        if (tier == 0) {
            return outcome(PromotionOutcome.Result.BELOW_TIER, 0, 0, remaining);
        }
        if (remaining == 0) {
            return outcome(PromotionOutcome.Result.DAILY_LIMIT, tier, 0, 0);
        }

        int amount = PromotionRules.amountForTier(tier);
        repository.save(new PromotionGrant(user, trainingSessionId, today, clickCount, tier, amount));
        log.info("프로모션 대상 기록 - userId: {}, taps: {}, tier: {}, amount: {}", user.getId(), clickCount, tier, amount);
        return outcome(PromotionOutcome.Result.RECORDED, tier, amount, remaining - 1);
    }

    private static PromotionOutcome outcome(PromotionOutcome.Result result, int tier, int amount, int remaining) {
        return PromotionOutcome.builder().result(result).tier(tier).amount(amount).remainingToday(remaining).build();
    }
}
