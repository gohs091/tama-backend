package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.domain.promotion.PromotionOutcome;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrainingResult {
    private int clickCount;
    private int reward;
    private int evoGain;   // 이번 트레이닝으로 오른 진화 게이지
    private int evoGauge;  // 오른 뒤의 진화 게이지
    private PromotionOutcome promotion; // 완전체 트레이닝의 프로모션 결과 (대상이 아니거나 꺼져 있으면 null)
}