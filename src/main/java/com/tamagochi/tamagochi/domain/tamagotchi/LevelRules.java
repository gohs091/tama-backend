package com.tamagochi.tamagochi.domain.tamagotchi;

/**
 * 레벨·경험치·무게·위력 규칙. 수치는 모두 임의값이라 여기서만 고친다.
 * 레벨은 진화해도 이어진다. 단계마다 그 단계를 시작한 레벨 + ZONE_SPAN에 닿으면 "진화 가능 구간"이 되고,
 * 그때부터는 경험치가 절반만 들어오는 대신 레벨이 오를 때마다 새 진화 후보 모습을 받아 진화할지 고를 수 있다.
 */
public final class LevelRules {

    public static final int START_LEVEL = 1;
    public static final int EXP_PER_STAR = 5;         // 트레이닝 별(EvolutionRules.gainForTaps) 1개 = 기본 경험치 5. 광고를 보면 2배(10)
    public static final int ZONE_EXP_DIVISOR = 2;     // 진화 가능 구간에서는 경험치가 이만큼 나뉘어 들어온다 (올림)
    public static final int ZONE_EXP_PER_LEVEL = 60;  // 진화 가능 구간의 레벨 하나에 드는 경험치 (레벨과 상관없이 고정. 레벨마다 후보 이미지를 만든다)

    public static final int START_WEIGHT_DG = 100;    // 무게(dg, 데이터그램)
    public static final int OVERFEED_AT = 80;         // 포만감이 이 이상일 때 먹이면 과식이라 무게가 는다
    public static final int FEED_WEIGHT_DG = 1;       // 과식 1번에 늘어나는 무게
    public static final int TRAINING_WEIGHT_DG = 1;   // 트레이닝 1번에 빠지는 무게
    public static final int MIN_WEIGHT_DG = 50;

    public static final int MIGHT_PER_LEVEL = 5;      // 레벨이 오를 때마다 위력 (위력은 주로 레벨로 오른다)
    public static final int MIGHT_PER_TRAINING = 1;   // 트레이닝 한 판에 위력 (조금씩)

    private LevelRules() {
    }

    /** 이 레벨에서 다음 레벨로 가는 데 드는 경험치 */
    public static int expToNext(int level) {
        return 20 + 10 * level;
    }

    /** 단계를 시작한 레벨에서 몇 레벨 더 오르면 진화 가능 구간인가. 진화할 수 없는 단계는 0 */
    public static int zoneSpan(EvolutionStage stage) {
        return switch (stage) {
            case BABY -> 5;   // Lv.1에 태어나 Lv.6부터 (트레이닝 12~18번: 무료 유저도 첫날 진화)
            case CHILD -> 19; // 진화한 레벨 + 19부터 (무료 유저가 광고 2배를 매번 받아도 완성작까지 5일 이상: 별 3개 약 5.5일, 별 2개 약 9일)
            case EGG, ADULT -> 0;
        };
    }

    /** 트레이닝 탭 수로 받는 경험치. 진화 가능 구간이면 절반(올림) */
    public static int expForTaps(int taps, boolean inZone) {
        int exp = EvolutionRules.gainForTaps(taps) * EXP_PER_STAR;
        return inZone ? (exp + ZONE_EXP_DIVISOR - 1) / ZONE_EXP_DIVISOR : exp;
    }

    /** 이 레벨까지 레벨 업으로 받은 위력 (레벨이 생기기 전 행의 위력을 정할 때 쓴다) */
    public static int mightForLevel(int level) {
        return Math.max(0, level - START_LEVEL) * MIGHT_PER_LEVEL;
    }
}
