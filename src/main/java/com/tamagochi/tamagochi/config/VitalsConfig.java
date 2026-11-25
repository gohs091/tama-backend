package com.tamagochi.tamagochi.config;

import com.tamagochi.tamagochi.domain.tamagotchi.VitalsRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** 생존 규칙(tamagotchi.vitals.*). 시간 경과 틱과 "곧 떠나요" 알림이 같은 값을 쓴다 */
@Configuration
public class VitalsConfig {

    @Bean
    public VitalsRule vitalsRule(
            @Value("${tamagotchi.vitals.satiety-minutes-per-point:4}") int satietyMinutesPerPoint,
            @Value("${tamagotchi.vitals.strength-minutes-per-point:8}") int strengthMinutesPerPoint,
            @Value("${tamagotchi.vitals.death-after-empty-hours:6}") int deathAfterEmptyHours,
            @Value("${tamagotchi.vitals.poop-interval-minutes:60}") int poopIntervalMinutes,
            @Value("${tamagotchi.vitals.max-poop:13}") int maxPoop,
            @Value("${tamagotchi.vitals.max-tick-gap-minutes:5}") int maxTickGapMinutes) {
        return new VitalsRule(satietyMinutesPerPoint, strengthMinutesPerPoint,
                Duration.ofHours(deathAfterEmptyHours), poopIntervalMinutes, maxPoop,
                Duration.ofMinutes(maxTickGapMinutes));
    }
}
