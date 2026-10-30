package com.tamagochi.tamagochi.domain.tamagotchi;

/**
 * 진화 규칙. 게이지와 단계는 서버가 판정한다(클라이언트 값은 신뢰하지 않는다).
 * 프론트 src/utils/evolutionCondition.ts, evolutionGauge.ts 는 이 값을 서버 응답으로 받아 쓴다.
 */
public final class EvolutionRules {

    public static final int MIN_SATIATION = 50;  // 포만감 50 이상
    public static final int MIN_STRENGTH = 50;   // 파워 50 이상
    public static final int MAX_POOP = 0;        // 응가 0개

    private EvolutionRules() {
    }

    /** 다음 단계로 진화하는 데 필요한 게이지 칸 수. 완성작으로 가는 길이 가장 길다. 진화할 수 없는 단계는 0. */
    public static int gaugeMax(EvolutionStage stage) {
        return switch (stage) {
            case BABY -> 10;
            case CHILD -> 60;
            case EGG, ADULT -> 0;
        };
    }

    /** 5초 트레이닝의 탭 수 → 별 (15번 미만 1, 15~32번 2, 33번 이상 3). 꾹 누르기(초당 5번)는 별 2개까지 */
    public static int gainForTaps(int taps) {
        if (taps >= 33) return 3;
        if (taps >= 15) return 2;
        return 1;
    }

    public static EvolutionStage next(EvolutionStage stage) {
        return switch (stage) {
            case EGG -> EvolutionStage.BABY;
            case BABY -> EvolutionStage.CHILD;
            case CHILD -> EvolutionStage.ADULT;
            case ADULT -> null;
        };
    }
}
