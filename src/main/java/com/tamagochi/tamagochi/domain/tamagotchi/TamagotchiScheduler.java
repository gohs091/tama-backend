package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TamagotchiScheduler {

    private final TamagotchiRepository tamagotchiRepository;

    @Value("${tamagotchi.hunger.increase-threshold-minutes}")
    private int increaseThresholdMinutes;

    @Value("${tamagotchi.hunger.increase-amount}")
    private int increaseAmount;

    // 1분마다 배고픔 증가 체크
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void increaseHunger() {
        List<Tamagotchi> list = tamagotchiRepository.findAll();

        for (Tamagotchi tamagotchi : list) {
            if (tamagotchi.isDead()) continue;
            if (tamagotchi.isSleeping()) continue;

            if (tamagotchi.getLastFedAt() == null ||
                    tamagotchi.getLastFedAt()
                            .isBefore(LocalDateTime.now().minusMinutes(increaseThresholdMinutes))) {
                tamagotchi.increaseHunger(increaseAmount);
                log.info("배고픔 증가 - tamagotchi: {}, hunger: {}",
                        tamagotchi.getName(), tamagotchi.getHunger());
            }

            // 사망 체크
            if (tamagotchi.isDead()) {
                tamagotchi.reset();
                log.info("사망 및 리셋 - tamagotchi: {}", tamagotchi.getName());
            }
        }
    }

    // 30분마다 똥 생성 체크
    @Scheduled(fixedDelay = 1800000)
    @Transactional
    public void generatePoop() {
        List<Tamagotchi> list = tamagotchiRepository.findAll();

        for (Tamagotchi tamagotchi : list) {
            if (tamagotchi.isDead()) continue;
            if (tamagotchi.isSleeping()) continue;
            if (tamagotchi.getEvolutionStage() == EvolutionStage.EGG) continue;

            // 마지막 먹이 준 지 30분 지나면 똥 생성
            if (tamagotchi.getLastFedAt() != null &&
                    tamagotchi.getLastFedAt()
                            .isBefore(LocalDateTime.now().minusMinutes(30))) {
                tamagotchi.poop();
                log.info("똥 생성 - tamagotchi: {}, poopCount: {}",
                        tamagotchi.getName(), tamagotchi.getPoopCount());
            }

            // 사망 체크
            if (tamagotchi.isDead()) {
                tamagotchi.reset();
                log.info("똥 가득 - 사망 및 리셋 - tamagotchi: {}", tamagotchi.getName());
            }
        }
    }
}