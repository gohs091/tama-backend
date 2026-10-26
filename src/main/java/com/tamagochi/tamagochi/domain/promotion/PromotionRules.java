package com.tamagochi.tamagochi.domain.promotion;

/**
 * 완전체 트레이닝 프로모션 규칙. 랜덤 없이 탭 수 구간으로 정해진다.
 * 금액(원)과 구간은 시작값이다. 실제 탭 분포와 콘솔의 프로모션 예산·1회 지급 한도를 보고 조정한다.
 */
public final class PromotionRules {

    /** 사람이 칠 수 있는 한계를 넘는 탭 수는 보상 계산에서 잘라낸다 (10초에 10번/초). */
    public static final int MAX_COUNTED_TAPS = 100;
    public static final int TIER1_MIN_TAPS = 60;
    public static final int TIER2_MIN_TAPS = 80;
    public static final int TIER1_AMOUNT = 1;
    public static final int TIER2_AMOUNT = 2;
    /** 하루(KST) 프로모션 대상이 되는 트레이닝 횟수 */
    public static final int DAILY_LIMIT = 3;

    private PromotionRules() {
    }

    /** 탭 수 → 구간 (0: 대상 아님, 1, 2) */
    public static int tierForTaps(int taps) {
        int counted = Math.min(Math.max(taps, 0), MAX_COUNTED_TAPS);
        if (counted >= TIER2_MIN_TAPS) return 2;
        if (counted >= TIER1_MIN_TAPS) return 1;
        return 0;
    }

    public static int amountForTier(int tier) {
        return switch (tier) {
            case 1 -> TIER1_AMOUNT;
            case 2 -> TIER2_AMOUNT;
            default -> 0;
        };
    }
}
