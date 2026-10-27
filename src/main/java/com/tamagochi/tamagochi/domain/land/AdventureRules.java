package com.tamagochi.tamagochi.domain.land;

/**
 * 모험 한 번의 잉크 계산. 확률 없이 시간으로만 정해진다.
 * - 소모: 다 채운 1분마다 그때 탐험 깊이의 분당 잉크(LandCatalog.costPerMinute)
 * - 줍기: 하루 첫 모험이면 처음 3분 동안 20초마다 +2, 그 뒤(또는 보너스가 없으면 처음부터) 30분까지 1분마다 +1,
 *   30분 이후는 2분마다 +1
 * - 끝: 4시간이 지나거나, 다음 1분을 쓰면 잔액이 RESERVE_INK 아래로 내려가거나, 구역을 다 탐험하면
 */
public final class AdventureRules {

    private AdventureRules() {}

    public static final int MAX_MINUTES = 4 * 60;
    /** 돌아와서 고기·알약은 줄 수 있게 남겨 두는 잉크 */
    public static final int RESERVE_INK = 20;

    static final int BONUS_SECONDS = 180;
    static final int BONUS_STEP_SECONDS = 20;
    static final int BONUS_INK = 2;
    static final int STEADY_UNTIL_MINUTES = 30;

    /** 시작 후 seconds초 동안 주운 잉크 */
    public static int pickedInk(int seconds, boolean bonus) {
        if (seconds <= 0) return 0;
        int minutes = seconds / 60;
        int ink = 0;
        if (bonus) {
            ink += BONUS_INK * (Math.min(seconds, BONUS_SECONDS) / BONUS_STEP_SECONDS);
        }
        int steadyFrom = bonus ? BONUS_SECONDS / 60 : 0; // 보너스 구간의 분은 1분 보상을 겹쳐 주지 않는다
        ink += Math.max(0, Math.min(minutes, STEADY_UNTIL_MINUTES) - steadyFrom);
        if (minutes > STEADY_UNTIL_MINUTES) {
            ink += minutes / 2 - STEADY_UNTIL_MINUTES / 2; // 31분부터 짝수 분마다 +1
        }
        return ink;
    }

    /** 시작 때 이미 exploredAtStart분 탐험한 구역에서 minutes분 동안 쓴 잉크 */
    public static int spentInk(int minutes, int exploredAtStart) {
        int ink = 0;
        for (int m = 1; m <= minutes; m++) {
            ink += LandCatalog.costPerMinute(exploredAtStart + m - 1);
        }
        return ink;
    }

    public enum EndReason { STOPPED, TIME_UP, LOW_INK, COMPLETE }

    /** 지금까지 지난 시간 중 모험이 실제로 이어진 분과, 그 사이 끝났다면 이유 */
    public record Progress(int minutes, EndReason ended) {}

    /**
     * elapsedSeconds 동안 모험이 어디까지 갔는지. balanceWithoutAdventure는 이번 모험의 소모·줍기를 빼고 본 지금 잔액이다.
     * 1분을 다 채울 때마다 잔액·시간·구역 끝을 확인한다.
     */
    public static Progress progress(int elapsedSeconds, int exploredAtStart, int regionTotalMinutes,
                                    boolean bonus, int balanceWithoutAdventure) {
        int elapsedMinutes = Math.max(0, elapsedSeconds) / 60;
        int spent = 0;
        for (int m = 1; m <= Math.min(elapsedMinutes, MAX_MINUTES); m++) {
            int cost = LandCatalog.costPerMinute(exploredAtStart + m - 1);
            int balance = balanceWithoutAdventure + pickedInk(m * 60, bonus) - spent - cost;
            if (balance < RESERVE_INK) return new Progress(m - 1, EndReason.LOW_INK);
            spent += cost;
            if (exploredAtStart + m >= regionTotalMinutes) return new Progress(m, EndReason.COMPLETE);
        }
        if (elapsedMinutes >= MAX_MINUTES) return new Progress(MAX_MINUTES, EndReason.TIME_UP);
        return new Progress(elapsedMinutes, null);
    }

    /** 이 잔액으로 모험을 시작할 수 있는지: 첫 1분을 쓰고도 RESERVE_INK가 남아야 한다 */
    public static boolean canAfford(int balance, int exploredAtStart) {
        return balance - LandCatalog.costPerMinute(exploredAtStart) >= RESERVE_INK;
    }
}
