package com.tamagochi.tamagochi.domain.tamagotchi;

import java.util.ArrayList;
import java.util.List;

/**
 * 돌봄이 모자랄 때의 불이익(디버프) 규칙 (2026-11-17). 디버프는 모든 단계에 걸린다.
 * 완성작(ADULT)은 배고픔·파워·응가로 떠나지 않고 여기 적힌 불이익만 받는다.
 * 완성작 전(낙서·스케치)에는 디버프에 더해 예전처럼 떠날 수 있다: 포만감 0이나 파워 0이 VitalsRule.deathAfterEmpty만큼 이어지거나
 * 응가가 VitalsRule.maxPoop 이상이면 떠난다 (Tamagotchi.checkDeath).
 * 모두 고정 규칙이고(운 없음), 먹이기·알약·청소를 하면 바로 풀린다. 수치는 여기서만 고친다.
 *
 * <ul>
 *   <li>HUNGRY (hunger ≥ {@value #HUNGRY_AT}, 포만감 20 이하): 트레이닝을 할 수 없다. 무료 판을 다 쓴 친구 배틀도 포만감이 모자라면 할 수 없다
 *       (Tamagotchi.battleShortage). 고기를 먹여 포만감이 20을 넘으면 풀린다.</li>
 *   <li>WEAK (strength &lt; {@value #WEAK_BELOW}): 트레이닝을 할 수 없다. 무료 판을 다 쓴 친구 배틀도 파워가 모자라면 할 수 없다.
 *       알약으로 파워가 20 이상이 되면 풀린다.</li>
 *   <li>DIRTY (응가 ≥ {@value #DIRTY_AT}개): 트레이닝 경험치가 {@value #DIRTY_EXP_PERCENT}%만 들어오고(올림),
 *       스파링 컨디션이 {@value #DIRTY_CONDITION_PENALTY} 낮아진다. 완성작의 응가는 VitalsRule.maxPoop에서 더 늘지 않는다. 청소하면 풀린다.</li>
 * </ul>
 * 그 밖에 포만감·파워가 낮을수록 스파링 컨디션(전투력 보정 0.90~1.10)이 내려가는 것은 전과 같다 (BattleRules.conditionMul).
 */
public final class DebuffRules {

    /** 응답(TamagotchiResponse.debuffs)에 이 이름 그대로 나간다 */
    public enum Debuff { HUNGRY, WEAK, DIRTY }

    public static final int HUNGRY_AT = 80;               // hunger가 이 이상이면 배고픔 (포만감 20 이하)
    public static final int WEAK_BELOW = 20;              // 파워가 이 미만이면 기운 없음
    public static final int DIRTY_AT = 5;                 // 응가가 이만큼 쌓이면 지저분함
    public static final int DIRTY_EXP_PERCENT = 50;       // 지저분할 때 트레이닝 경험치 (%)
    public static final int DIRTY_CONDITION_PENALTY = 20; // 지저분할 때 스파링 컨디션에서 빼는 값

    private DebuffRules() {
    }

    public static boolean hungry(int hunger) {
        return hunger >= HUNGRY_AT;
    }

    public static boolean weak(int strength) {
        return strength < WEAK_BELOW;
    }

    public static boolean dirty(int poopCount) {
        return poopCount >= DIRTY_AT;
    }

    /** 지금 걸린 디버프 (HUNGRY, WEAK, DIRTY 순) */
    public static List<Debuff> of(int hunger, int strength, int poopCount) {
        List<Debuff> out = new ArrayList<>();
        if (hungry(hunger)) out.add(Debuff.HUNGRY);
        if (weak(strength)) out.add(Debuff.WEAK);
        if (dirty(poopCount)) out.add(Debuff.DIRTY);
        return out;
    }

    /** 트레이닝 기본 경험치에 지저분함을 반영한다 (올림, 0은 0) */
    public static int trainingExp(int exp, int poopCount) {
        return dirty(poopCount) ? (exp * DIRTY_EXP_PERCENT + 99) / 100 : exp;
    }

    /** 스파링 컨디션(0~100): 포만감과 파워의 평균에서 지저분하면 깎는다 */
    public static int sparCondition(int hunger, int strength, int poopCount) {
        int condition = ((100 - hunger) + strength) / 2;
        return Math.max(0, dirty(poopCount) ? condition - DIRTY_CONDITION_PENALTY : condition);
    }
}
