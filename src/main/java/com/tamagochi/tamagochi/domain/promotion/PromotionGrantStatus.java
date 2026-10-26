package com.tamagochi.tamagochi.domain.promotion;

public enum PromotionGrantStatus {
    RECORDED,  // 대상으로 기록만 했다 (실제 지급 전)
    GRANTED,   // 토스 포인트 지급 완료
    FAILED     // 지급 시도가 실패했다
}
