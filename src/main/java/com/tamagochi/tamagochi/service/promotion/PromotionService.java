package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import com.tamagochi.tamagochi.domain.promotion.PromotionRecordedEvent;
import com.tamagochi.tamagochi.domain.promotion.PromotionInfo;
import com.tamagochi.tamagochi.domain.promotion.PromotionOutcome;
import com.tamagochi.tamagochi.domain.promotion.PromotionRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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

    /** off: 판정 안 함 / record: 대상 여부를 판정해 기록만 함 / grant: 기록하고 토스 포인트를 실제로 지급 */
    public enum Mode { OFF, RECORD, GRANT }

    private final PromotionGrantRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final Mode mode;

    public PromotionService(PromotionGrantRepository repository, Clock clock, ApplicationEventPublisher events,
                            @Value("${tamagotchi.promotion.mode:off}") String mode) {
        this.repository = repository;
        this.clock = clock;
        this.events = events;
        this.mode = switch (mode == null ? "" : mode.trim().toLowerCase()) {
            case "record" -> Mode.RECORD;
            case "grant" -> Mode.GRANT;
            default -> Mode.OFF;
        };
    }

    public boolean isEnabled() {
        return mode != Mode.OFF;
    }

    /** 하루 상한에 세지 않도록 먼 과거 날짜를 쓰는 관리자 테스트 지급용 날짜 */
    static final LocalDate TEST_GRANT_DATE = LocalDate.of(1970, 1, 1);

    /** 관리자 테스트 지급 한 건을 기록한다 (트레이닝과 무관, 하루 상한에 세지 않음). 지급은 호출한 쪽이 시작한다. */
    @Transactional
    public PromotionGrant recordTest(User user, int amount) {
        return repository.save(new PromotionGrant(user, "admin-test-" + java.util.UUID.randomUUID(),
                TEST_GRANT_DATE, 0, 0, amount));
    }

    /** 트레이닝 화면에 보여줄 프로모션 안내: 구간표와 오늘 남은 횟수 */
    public PromotionInfo info(User user) {
        int remaining = 0;
        if (isEnabled()) {
            long used = repository.countByUserIdAndGrantDateAndStatusNot(user.getId(), LocalDate.now(clock), PromotionGrantStatus.FAILED);
            remaining = (int) Math.max(0, PromotionRules.DAILY_LIMIT - used);
        }
        return PromotionInfo.builder()
                .enabled(isEnabled())
                .dailyLimit(PromotionRules.DAILY_LIMIT)
                .remainingToday(remaining)
                .maxCountedTaps(PromotionRules.MAX_COUNTED_TAPS)
                .tiers(PromotionRules.tiers())
                .build();
    }

    /** 이번 트레이닝(세션)의 프로모션 결과를 정한다. 꺼져 있으면 null. */
    @Transactional
    public PromotionOutcome evaluate(User user, String trainingSessionId, int clickCount) {
        if (!isEnabled()) return null;

        LocalDate today = LocalDate.now(clock);
        long usedToday = repository.countByUserIdAndGrantDateAndStatusNot(user.getId(), today, PromotionGrantStatus.FAILED);
        int remaining = (int) Math.max(0, PromotionRules.DAILY_LIMIT - usedToday);

        int tier = PromotionRules.tierForTaps(clickCount);
        if (tier == 0) {
            return outcome(PromotionOutcome.Result.BELOW_TIER, 0, 0, remaining);
        }
        if (remaining == 0) {
            return outcome(PromotionOutcome.Result.DAILY_LIMIT, tier, 0, 0);
        }

        int amount = PromotionRules.amountForTier(tier);
        PromotionGrant grant = repository.save(new PromotionGrant(user, trainingSessionId, today, clickCount, tier, amount));
        if (mode == Mode.GRANT) {
            events.publishEvent(new PromotionRecordedEvent(grant.getId())); // 커밋된 뒤 지급을 시작한다
        }
        log.info("프로모션 대상 기록 - userId: {}, taps: {}, tier: {}, amount: {}", user.getId(), clickCount, tier, amount);
        return outcome(PromotionOutcome.Result.RECORDED, tier, amount, remaining - 1);
    }

    private static PromotionOutcome outcome(PromotionOutcome.Result result, int tier, int amount, int remaining) {
        return PromotionOutcome.builder().result(result).tier(tier).amount(amount).remainingToday(remaining).build();
    }
}
