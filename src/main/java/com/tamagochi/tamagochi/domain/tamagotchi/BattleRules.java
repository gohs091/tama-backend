package com.tamagochi.tamagochi.domain.tamagotchi;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 트레이닝 스파링 규칙. 수치는 모두 임의값이라 여기서만 고친다.
 * 전투력 = (기본 + 위력) × 탭 보정(0.85~1.15) × 컨디션 보정(0.90~1.10) × 운(0.90~1.10).
 * 위력이 가장 큰 변수다: 탭을 많이 치는 것보다 오래 키운 끄적이가 유리하다 (꾹 누르기로 쳐도 손해가 크지 않다).
 */
public final class BattleRules {

    public static final int MIGHT_BASE = 20;          // 위력 0인 끄적이도 이 만큼의 힘은 있다
    public static final int TAPS_FOR_MAX = 40;        // 5초에 이만큼 치면 탭 보정이 가장 크다
    public static final double TAP_MUL_MIN = 0.85;
    public static final double TAP_MUL_RANGE = 0.30;  // 0.85 ~ 1.15
    public static final double COND_MUL_MIN = 0.90;
    public static final double COND_MUL_RANGE = 0.20; // 0.90 ~ 1.10
    public static final double LUCK_MIN = 0.90;
    public static final double LUCK_RANGE = 0.20;     // 0.90 ~ 1.10

    // 상대(가상 스파링 상대): 내 위력 ±15%, 레벨 ±1, 탭 15~40, 컨디션 40~100. 레벨별 적은 나중에 이미지와 함께 만든다
    public static final double ENEMY_MIGHT_SPREAD = 0.15;
    public static final int ENEMY_TAPS_MIN = 15;
    public static final int ENEMY_TAPS_MAX = 40;
    public static final int ENEMY_COND_MIN = 40;
    /** 앱에 들어 있는 상대 그림 이름 (앱이 이 이름으로 그림을 고른다) */
    public static final List<String> ENEMY_KEYS = List.of("crow", "dog", "person", "train");

    public static final int WIN_EXP_BONUS_PERCENT = 50; // 이기면 기본 경험치의 50%를 더 준다 (광고 2배에는 포함하지 않는다)

    private BattleRules() {
    }

    public static double tapMul(int taps) {
        return TAP_MUL_MIN + TAP_MUL_RANGE * Math.min(Math.max(taps, 0), TAPS_FOR_MAX) / TAPS_FOR_MAX;
    }

    public static double conditionMul(int condition) {
        return COND_MUL_MIN + COND_MUL_RANGE * Math.min(Math.max(condition, 0), 100) / 100.0;
    }

    /** luckPermille: 운을 천분율로 (900~1100) */
    public static double power(int might, int taps, int condition, int luckPermille) {
        return (MIGHT_BASE + Math.max(might, 0)) * tapMul(taps) * conditionMul(condition) * (luckPermille / 1000.0);
    }

    public static int randomLuckPermille() {
        return (int) Math.round((LUCK_MIN + ThreadLocalRandom.current().nextDouble() * LUCK_RANGE) * 1000);
    }

    /** 내 레벨·위력에 맞춘 가상 상대 */
    public static Enemy randomEnemy(int myLevel, int myMight) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double spread = 1 - ENEMY_MIGHT_SPREAD + r.nextDouble() * ENEMY_MIGHT_SPREAD * 2;
        return new Enemy(
                ENEMY_KEYS.get(r.nextInt(ENEMY_KEYS.size())),
                Math.max(1, myLevel + r.nextInt(-1, 2)),
                (int) Math.round(myMight * spread),
                r.nextInt(ENEMY_TAPS_MIN, ENEMY_TAPS_MAX + 1),
                r.nextInt(ENEMY_COND_MIN, 101),
                randomLuckPermille());
    }

    public record Enemy(String key, int level, int might, int taps, int condition, int luckPermille) {
    }
}
