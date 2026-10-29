package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import com.tamagochi.tamagochi.domain.promotion.PromotionRecordedEvent;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 진화 달성 보상: 스케치가 되면 10원, 완성작이 되면 50원(토스 포인트).
 * 스케치는 사용자당 처음 한 번만 준다 (첫 보상으로 "진짜 받는다"를 경험하게 하되, 스케치만 노리고 다시 키우는 걸 막는다).
 * 완성작은 끄적이를 다시 키워 또 진화하면 또 주되, 한 달(KST, 매월 1일 초기화)에 4번까지다 (일주일에 한 번꼴).
 * mode=off 이면 아무것도 하지 않고, record 이면 기록만, grant 이면 진화 보상용 코드로 실제 지급한다.
 */
@Service
@Slf4j
public class EvolutionPromotionService {

    /** 단계별 보상 금액(원) */
    public static final Map<EvolutionStage, Integer> AMOUNTS = Map.of(EvolutionStage.CHILD, 10, EvolutionStage.ADULT, 50);
    /** 완성작 보상을 한 달에 받을 수 있는 횟수 */
    public static final int MONTHLY_LIMIT = 4;
    /** 사용자당 처음 한 번만 주는 단계 */
    private static final Set<EvolutionStage> FIRST_ONLY = Set.of(EvolutionStage.CHILD);
    private static final List<EvolutionStage> STAGES = List.of(EvolutionStage.CHILD, EvolutionStage.ADULT);

    private final PromotionGrantRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final String mode;

    public EvolutionPromotionService(PromotionGrantRepository repository, Clock clock, ApplicationEventPublisher events,
                                     @Value("${tamagotchi.promotion.evolution.mode:off}") String mode) {
        this.repository = repository;
        this.clock = clock;
        this.events = events;
        this.mode = mode == null ? "off" : mode.trim().toLowerCase();
    }

    public boolean isEnabled() {
        return mode.equals("record") || mode.equals("grant");
    }

    /** remaining: 더 받을 수 있는 횟수 (firstOnly면 평생, 아니면 이번 달). 0이면 지금은 받을 수 없다 */
    public record Reward(String stage, int amount, int remaining, boolean firstOnly) {}
    public record Info(boolean enabled, int monthlyLimit, List<Reward> rewards) {}

    public Info info(User user) {
        LocalDate today = LocalDate.now(clock);
        List<Reward> rewards = STAGES.stream()
                .map(s -> new Reward(s.name(), AMOUNTS.get(s), isEnabled() ? remaining(user, s, today) : 0, FIRST_ONLY.contains(s)))
                .toList();
        return new Info(isEnabled(), MONTHLY_LIMIT, rewards);
    }

    /** 진화한 직후 부른다. 보상이 있는 단계이고 이번 달 횟수가 남았으면 기록하고, grant 모드면 커밋 뒤 지급을 시작한다 */
    @Transactional
    public void onEvolved(User user, EvolutionStage stage) {
        Integer amount = AMOUNTS.get(stage);
        if (!isEnabled() || amount == null) return;
        LocalDate today = LocalDate.now(clock);
        if (remaining(user, stage, today) <= 0) {
            log.info("진화 달성 보상 횟수 소진 - userId: {}, stage: {}", user.getId(), stage);
            return;
        }
        // 순번은 실패 건까지 세어 키가 겹치지 않게 한다 (횟수 제한은 실패 건을 빼고 센다)
        String prefix = PromotionGrant.evolutionMonthPrefix(stage.name(), user.getId(), today);
        int seq = (int) repository.countByTrainingSessionIdStartingWith(prefix) + 1;

        PromotionGrant grant = repository.save(PromotionGrant.evolution(user, stage.name(), today, seq, amount));
        if (mode.equals("grant")) {
            events.publishEvent(new PromotionRecordedEvent(grant.getId()));
        }
        log.info("진화 달성 보상 기록 - userId: {}, stage: {}, amount: {}, 이번 달 {}번째", user.getId(), stage, amount, seq);
    }

    private int remaining(User user, EvolutionStage stage, LocalDate today) {
        if (FIRST_ONLY.contains(stage)) {
            // 달과 상관없이 이 단계 보상을 한 번이라도 받았는지 (키: evolution-단계-사용자-...)
            String any = PromotionGrant.evolutionUserPrefix(stage.name(), user.getId());
            long used = repository.countByTrainingSessionIdStartingWithAndStatusNot(any, PromotionGrantStatus.FAILED);
            return used > 0 ? 0 : 1;
        }
        String prefix = PromotionGrant.evolutionMonthPrefix(stage.name(), user.getId(), today);
        long used = repository.countByTrainingSessionIdStartingWithAndStatusNot(prefix, PromotionGrantStatus.FAILED);
        return (int) Math.max(0, MONTHLY_LIMIT - used);
    }
}
