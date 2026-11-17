package com.tamagochi.tamagochi.domain.tower;

/**
 * 탑 규칙 (모두 고정값, 무작위 없음). 밸런스를 바꿀 때는 여기만 고친다.
 * 탑의 시간 제한은 하루 포만감뿐이다: 하루 출발 횟수·새 층 돌파 횟수 제한은 없다.
 */
public final class TowerRules {

    private TowerRules() {}

    /** 맵 1장을 통과하면 쓰는 포만감 */
    public static final int MAP_SATIETY_COST = 35;

    /** 한 층의 맵 수. 마지막 맵을 통과하면 그 층을 돌파한 것이다 */
    public static final int MAPS_PER_FLOOR = 3;

    /**
     * 탑은 포만감을 이 아래로 내리지 않는다. 포만감 0(hunger 100)이 되면 굶주림 타이머(hungryStartedAt)가 돌기 시작하고,
     * 완성작 전(낙서·스케치)은 그 상태로 deathAfterEmpty(6시간)가 지나면 떠난다 (완성작은 떠나지 않고 디버프만 받는다).
     * 탑 소모만으로 그 타이머가 시작되지 않게 1을 남긴다. 그 뒤 시간이 흘러 0이 되는 것은 집에서와 같은 기존 규칙이다
     */
    public static final int SATIETY_FLOOR = 1;

    public static final int FIRST_FLOOR = 1;
}
