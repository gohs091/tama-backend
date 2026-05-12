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

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void increaseHunger() {
        List<Tamagotchi> tamagotchiList = tamagotchiRepository.findAll();


        for (Tamagotchi tamagotchi : tamagotchiList) {
//            if (tamagotchi.getLastFedAt() == null ||
//                    tamagotchi.getLastFedAt()
//                            .isBefore(LocalDateTime.now().minusMinutes(increaseThresholdMinutes)))
                tamagotchi.increaseHunger(increaseAmount);
                log.info("배고픔 증가 - tamagotchi: {}, hunger: {}",
                        tamagotchi.getName(), tamagotchi.getHunger());

        }
    }
}