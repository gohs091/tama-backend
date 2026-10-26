package com.tamagochi.tamagochi.domain.promotion;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 트레이닝 화면에 보여줄 프로모션 안내. 구간과 상한은 서버 규칙을 그대로 내려보낸다. */
@Getter
@Builder
public class PromotionInfo {
    private boolean enabled;                      // false면 프로모션을 보여주지 않는다
    private int dailyLimit;                       // 하루 프로모션 대상 횟수
    private int remainingToday;                   // 오늘 남은 횟수
    private int maxCountedTaps;                   // 이 탭 수까지만 인정
    private List<PromotionRules.Tier> tiers;      // 탭 수 구간과 금액(원)
}
