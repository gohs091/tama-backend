package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TamagotchiScheduler {

    private final TamagotchiRepository tamagotchiRepository;

    @Value("${tamagotchi.vitals.satiety-minutes-per-point:4}")
    private int satietyMinutesPerPoint;

    @Value("${tamagotchi.vitals.strength-minutes-per-point:8}")
    private int strengthMinutesPerPoint;

    @Value("${tamagotchi.vitals.death-after-empty-hours:6}")
    private int deathAfterEmptyHours;

    @Value("${tamagotchi.vitals.poop-interval-minutes:60}")
    private int poopIntervalMinutes;

    @Value("${tamagotchi.vitals.max-poop:8}")
    private int maxPoop;

    @Value("${tamagotchi.vitals.max-tick-gap-minutes:5}")
    private int maxTickGapMinutes;

    // 1분마다 포만감·파워 소진, 똥 증가, 사망 판정
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void tick() {
        VitalsRule rule = new VitalsRule(satietyMinutesPerPoint, strengthMinutesPerPoint,
                Duration.ofHours(deathAfterEmptyHours), poopIntervalMinutes, maxPoop,
                Duration.ofMinutes(maxTickGapMinutes));
        LocalDateTime now = LocalDateTime.now();

        for (Tamagotchi tamagotchi : tamagotchiRepository.findAll()) {
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
