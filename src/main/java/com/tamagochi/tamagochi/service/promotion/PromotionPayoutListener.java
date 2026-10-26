package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 기록이 DB에 커밋된 뒤 다른 스레드에서 지급을 시작한다 (트레이닝 응답이 토스 호출을 기다리지 않게). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tamagotchi.promotion.mode", havingValue = "grant")
public class PromotionPayoutListener {

    private final PromotionGranter granter;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecorded(PromotionRecordedEvent event) {
        granter.grant(event.grantId());
    }
}
