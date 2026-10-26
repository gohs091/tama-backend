package com.tamagochi.tamagochi.domain.promotion;

import lombok.Builder;
import lombok.Getter;

/** 트레이닝 응답에 실어 보내는 프로모션 결과 */
@Getter
@Builder
public class PromotionOutcome {

    public enum Result {
        RECORDED,      // 대상이 되어 기록했다 (amount만큼 받을 예정)
        BELOW_TIER,    // 탭 수가 가장 낮은 구간에 못 미쳤다
        DAILY_LIMIT    // 오늘 받을 수 있는 횟수를 다 썼다
    }

    private Result result;
    private int tier;
    private int amount;          // 이번 트레이닝으로 받을 토스 포인트(원)
    private int remainingToday;  // 오늘 남은 프로모션 횟수
}
