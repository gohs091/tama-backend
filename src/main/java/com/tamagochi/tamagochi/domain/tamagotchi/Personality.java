package com.tamagochi.tamagochi.domain.tamagotchi;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 성격 무리. 키우는 동안 쌓인 성향 수치(까칠·우울·활기)로 진화할 때 무리가 정해지고,
 * 그 무리 안에서 앱의 22가지 성격(프론트 data/characterTags.ts의 PERSONALITIES 키) 중 하나를 고른다.
 * 낙서의 성격은 알일 때 앱이 무작위로 정한다. 이미지에는 반영하지 않고 글·대사로만 보여준다.
 */
public enum Personality {
    GENTLE(List.of("Docile", "Calm", "Mild", "Careful", "Serious", "Relaxed", "Rash")),              // 순한: 어느 성향도 크게 쌓이지 않음
    SENSITIVE(List.of("Adamant", "Sassy", "Timid", "Quiet")),                                     // 예민한: 까칠이 가장 높음
    GLOOMY(List.of("Lonely", "Bashful", "Quirky")),                                               // 시무룩한: 우울이 가장 높음
    LIVELY(List.of("Jolly", "Naughty", "Lax", "Brave", "Bold", "Naive", "Hardy", "Hasty"));       // 활발한: 활기가 가장 높음

    /** 성향이 이 값 이상이어야 그 무리가 된다. 모두 낮으면 순한 무리 */
    public static final int MIN_TRAIT = 30;

    private final List<String> keys;

    Personality(List<String> keys) {
        this.keys = keys;
    }

    public List<String> keys() {
        return keys;
    }

    public String pick(RandomGenerator random) {
        return keys.get(random.nextInt(keys.size()));
    }

    /** 가장 높은 성향이 MIN_TRAIT 이상이면 그 무리. 같으면 활기 > 까칠 > 우울 순으로 고른다 */
    public static Personality decide(int grumpy, int gloomy, int lively) {
        int top = Math.max(lively, Math.max(grumpy, gloomy));
        if (top < MIN_TRAIT) return GENTLE;
        if (lively == top) return LIVELY;
        if (grumpy == top) return SENSITIVE;
        return GLOOMY;
    }
}
