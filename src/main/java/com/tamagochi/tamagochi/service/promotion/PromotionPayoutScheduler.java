package com.tamagochi.tamagochi.service.promotion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 지급이 끊기거나 접수 중인 건을 주기적으로 다시 처리한다 (서버 재시작·일시 오류·결과 확인). */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "tamagotchi.promotion.mode", havingValue = "grant")
public class PromotionPayoutScheduler {

    private final PromotionGranter granter;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void sweep() {
        try {
            granter.sweep();
        } catch (Exception e) {
            log.error("프로모션 지급 점검 중 오류", e);
        }
    }
}
