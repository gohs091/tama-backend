package com.tamagochi.tamagochi.service.character;

import java.util.Random;

/**
 * 사용자가 그린 그림과 팩션 생물이 합쳐지는 방식. 낙서를 만들 때 시드로 하나 고르고,
 * 이후 스케치/완성작은 낙서 이미지를 참조해 같은 방식으로 성장한다.
 */
public enum FusionMode {
    /** 그림 자체가 몸 (눈이 달린 칼). 팩션은 장식·소품으로 입힌다 */
    SELF,
    /** 팩션 생물이 그림을 쥐고/물고/들고 있다 (용이 칼을 물고 있음) */
    HELD,
    /** 그림이 팩션 생물 몸의 시그니처 일부가 된다 (칼날이 뿔이 된 용) */
    FEATURE,
    /** 그림이 팩션 생물의 탈것이 된다 (용이 타고 날아가는 칼) */
    MOUNT;

    /**
     * 같은 시드는 항상 같은 방식. 시드가 없으면 매번 무작위.
     * 물체: SELF 40 / HELD 20 / FEATURE 20 / MOUNT 20
     * 동물·생물·사람: SELF 40 / FEATURE 30 / MOUNT 30 (동물이 무언가를 "쥔다"는 방식은 제외)
     */
    public static FusionMode pick(Long seed, boolean animalLike) {
        int roll = (seed == null ? new Random() : new Random(seed)).nextInt(100);
        if (roll < 40) return SELF;
        if (animalLike) return roll < 70 ? FEATURE : MOUNT;
        if (roll < 60) return HELD;
        return roll < 80 ? FEATURE : MOUNT;
    }
}
