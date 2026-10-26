package com.tamagochi.tamagochi.domain.promotion;

/** 프로모션 대상이 기록됐다 (커밋 후 지급을 시작하는 신호) */
public record PromotionRecordedEvent(Long grantId) {
}
