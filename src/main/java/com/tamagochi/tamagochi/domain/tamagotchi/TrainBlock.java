package com.tamagochi.tamagochi.domain.tamagotchi;

/**
 * 트레이닝(스파링)이나 광장 사냥을 못 하게 막는 조건. 조건과 순서는 여기 한 곳에만 둔다.
 * 스파링은 message 로 예외를 던지고(TamagotchiService.requireTrainable), 광장 사냥은 why 를 앱에 돌려준다(HuntService.status).
 */
public enum TrainBlock {
    DEAD("dead", "다마고치가 죽었어요."),
    BATHING("away", "잉크통에 들어가 있어요. 꺼낸 뒤에 해 주세요."),
    ADVENTURING("away", "모험을 떠나 있어요. 돌아온 뒤에 해 주세요."),
    ASLEEP("asleep", "다마고치가 자고 있어요."),
    EGG("egg", "알 상태에서는 트레이닝할 수 없어요."),
    HUNGRY("hungry", "배가 너무 고파서 트레이닝할 수 없어요."),
    WEAK("weak", "스트렝스가 부족해서 트레이닝할 수 없어요.");

    private final String why;
    private final String message;

    TrainBlock(String why, String message) {
        this.why = why;
        this.message = message;
    }

    /** 앱에 돌려주는 짧은 이유 */
    public String why() { return why; }

    /** 스파링이 던지는 예외 문구 */
    public String message() { return message; }

    /** 지금 막는 조건 하나(앞선 것부터). 할 수 있으면 null */
    public static TrainBlock of(Tamagotchi t) {
        if (t.isDead()) return DEAD;
        if (t.isBathing()) return BATHING;
        if (t.isAdventuring()) return ADVENTURING;
        if (t.isSleeping()) return ASLEEP;
        if (t.getEvolutionStage() == EvolutionStage.EGG) return EGG;
        if (DebuffRules.hungry(t.getHunger())) return HUNGRY;
        if (DebuffRules.weak(t.getStrength())) return WEAK;
        return null;
    }
}
