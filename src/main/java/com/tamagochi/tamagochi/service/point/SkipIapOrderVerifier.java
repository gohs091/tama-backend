package com.tamagochi.tamagochi.service.point;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 로컬 개발 전용: 검증 없이 통과시킨다. 운영에서는 tamagotchi.iap.verify=toss 로 둬야 한다. */
@Component
@ConditionalOnProperty(name = "tamagotchi.iap.verify", havingValue = "skip")
@Slf4j
public class SkipIapOrderVerifier implements IapOrderVerifier {

    @Override
    public boolean isPaid(String orderId, String sku) {
        log.warn("IAP 주문 검증을 건너뜁니다(개발 전용) - orderId: {}, sku: {}", orderId, sku);
        return true;
    }
}
