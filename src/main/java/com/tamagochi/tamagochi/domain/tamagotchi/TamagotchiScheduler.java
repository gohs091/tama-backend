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
            }
        }
    }
}
