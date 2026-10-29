package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 기록이 DB에 커밋된 뒤 다른 스레드에서 지급을 시작한다 (트레이닝 응답이 토스 호출을 기다리지 않게). */
@Component
@RequiredArgsConstructor
// 트레이닝 보상이나 진화 달성 보상 중 하나라도 실제 지급(grant)하면 켠다
@ConditionalOnExpression("'${tamagotchi.promotion.mode:off}' == 'grant' or '${tamagotchi.promotion.evolution.mode:off}' == 'grant'")
public class PromotionPayoutListener {

    private final PromotionGranter granter;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecorded(PromotionRecordedEvent event) {
        granter.grant(event.grantId());
    }
}
