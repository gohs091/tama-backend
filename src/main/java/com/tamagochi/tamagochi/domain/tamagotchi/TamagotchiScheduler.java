package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TamagotchiScheduler {

    private final TamagotchiRepository tamagotchiRepository;
    /** tamagotchi.vitals.* (VitalsConfig) */
    private final VitalsRule rule;
    private final com.tamagochi.tamagochi.service.usage.UsageRecorder usageRecorder; // 떠남을 사건 기록에 남긴다

    // 1분마다 포만감·파워 소진, 똥 증가, 사망 판정 (사망은 완성작 전에만. 완성작은 디버프만: DebuffRules)
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void tick() {
        LocalDateTime now = LocalDateTime.now();

        for (Tamagotchi tamagotchi : tamagotchiRepository.findAllByStatus(com.tamagochi.tamagochi.domain.tamagotchi.PetStatus.ACTIVE)) {
            boolean wasDead = tamagotchi.isDead();
            tamagotchi.applyTime(now, rule);
            if (!wasDead && tamagotchi.isDead()) {
                log.info("사망 - tamagotchi: {}, hunger: {}, strength: {}, poop: {}",
                        tamagotchi.getName(), tamagotchi.getHunger(),
                        tamagotchi.getStrength(), tamagotchi.getPoopCount());
                // 까닭은 방금 떠남을 판정한 조건 그대로 (둘 이상이면 +로 잇는다)
                String cause = tamagotchi.leftBecause().stream().map(Enum::name).collect(java.util.stream.Collectors.joining("+"));
                usageRecorder.petEvent(tamagotchi.getUser() == null ? null : tamagotchi.getUser().getId(), tamagotchi.getPublicId(),
                        com.tamagochi.tamagochi.domain.usage.PetEventType.LEAVE, tamagotchi.getEvolutionStage(), cause.isEmpty() ? null : cause);
            }
        }
    }
}
