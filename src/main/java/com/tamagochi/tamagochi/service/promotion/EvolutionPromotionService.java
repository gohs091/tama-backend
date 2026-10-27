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

/**
 * 진화 달성 보상: 성체가 되면 10원, 완전체가 되면 50원(토스 포인트). 단계마다 사용자당 한 번이다.
 * 끄적이를 다시 키워(다시 그리기·사망 후 재시작) 또 진화해도 다시 주지 않는다.
 * mode=off 이면 아무것도 하지 않고, record 이면 기록만, grant 이면 진화 보상용 코드로 실제 지급한다.
 */
@Service
@Slf4j
public class EvolutionPromotionService {

    /** 단계별 보상 금액(원). 순서대로 화면에 보여준다 */
    public static final Map<EvolutionStage, Integer> AMOUNTS = Map.of(EvolutionStage.CHILD, 10, EvolutionStage.ADULT, 50);
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

    /** received: 이 단계 보상을 이미 받았는지(지급 실패 건은 빼고) */
    public record Reward(String stage, int amount, boolean received) {}
    public record Info(boolean enabled, List<Reward> rewards) {}

    public Info info(User user) {
        List<Reward> rewards = STAGES.stream()
                .map(s -> new Reward(s.name(), AMOUNTS.get(s), isEnabled() && received(user, s)))
                .toList();
        return new Info(isEnabled(), rewards);
    }

    /** 진화한 직후 부른다. 보상이 있는 단계이고 아직 안 받았으면 기록하고, grant 모드면 커밋 뒤 지급을 시작한다 */
    @Transactional
    public void onEvolved(User user, EvolutionStage stage) {
        Integer amount = AMOUNTS.get(stage);
        if (!isEnabled() || amount == null) return;
        if (repository.findByTrainingSessionId(PromotionGrant.evolutionKey(stage.name(), user.getId())).isPresent()) return;

        PromotionGrant grant = repository.save(PromotionGrant.evolution(user, stage.name(), LocalDate.now(clock), amount));
        if (mode.equals("grant")) {
            events.publishEvent(new PromotionRecordedEvent(grant.getId()));
        }
        log.info("진화 달성 보상 기록 - userId: {}, stage: {}, amount: {}", user.getId(), stage, amount);
    }

    private boolean received(User user, EvolutionStage stage) {
        return repository.findByTrainingSessionId(PromotionGrant.evolutionKey(stage.name(), user.getId()))
                .filter(g -> g.getStatus() != PromotionGrantStatus.FAILED)
                .isPresent();
    }
}
