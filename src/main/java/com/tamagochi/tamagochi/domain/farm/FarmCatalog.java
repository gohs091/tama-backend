package com.tamagochi.tamagochi.domain.farm;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 농장 규칙. 열매를 버는 곳: 잉크로 씨앗을 심고, 거두면 열매가 나오고 같은 작물이 다시 자란다.
 * 물이 있는 동안은 WATER_SPEED배 빨리 자란다. 물은 오늘의 퀘스트·광고로 채운다.
 */
public final class FarmCatalog {

    private FarmCatalog() {}

    /** 작물: 자라는 시간, 거두면 받는 열매, 씨앗값(잉크) */
    public record Crop(String id, Duration grow, int fruit, int seedCost) {}

    public static final Map<String, Crop> CROPS;
    static {
        Map<String, Crop> m = new LinkedHashMap<>();
        m.put("strawberry", new Crop("strawberry", Duration.ofMinutes(30), 1, 5));
        m.put("blueberry", new Crop("blueberry", Duration.ofHours(4), 3, 10));
        m.put("pumpkin", new Crop("pumpkin", Duration.ofHours(12), 8, 20));
        CROPS = java.util.Collections.unmodifiableMap(m);
    }

    /** 밭은 3×3. 칸 번호 = 줄 × 3 + 칸 (0이 맨 뒤) */
    public static final int PLOTS = 9;
    /** 처음 열린 칸 (뒤쪽 2×2) */
    public static final Set<Integer> INITIAL_OPEN = Set.of(0, 1, 3, 4);
    /** 잠긴 칸을 여는 값(열매). 몇 번째로 여는지에 따라 오른다 */
    public static final List<Integer> UNLOCK_PRICES = List.of(15, 30, 50, 80, 120);

    /** 물이 있는 동안 자라는 속도 */
    public static final int WATER_SPEED = 2;
    /** 물은 이만큼까지만 쌓인다 */
    public static final Duration WATER_MAX = Duration.ofHours(8);

    /** 광고 보고 물 받기 */
    public static final Duration AD_WATER = Duration.ofHours(2);
    public static final int AD_PER_DAY = 2;

    /** 오늘의 퀘스트. side = 가끔 끼는 곁다리(보상이 크다) */
    public enum Quest {
        MEAT(3, Duration.ofMinutes(20), 0, false),
        PILL(3, Duration.ofMinutes(20), 0, false),
        TRAIN(3, Duration.ofMinutes(20), 0, false),
        BATTLE(1, Duration.ofHours(1), 5, true);

        public final int need;
        public final Duration water;
        public final int fruit;
        public final boolean side;

        Quest(int need, Duration water, int fruit, boolean side) {
            this.need = need;
            this.water = water;
            this.fruit = fruit;
            this.side = side;
        }
    }

    public static final List<Quest> DAILY = List.of(Quest.MEAT, Quest.PILL, Quest.TRAIN);
    private static final List<Quest> SIDES = List.of(Quest.BATTLE);

    /** 오늘 끼는 곁다리 퀘스트 (이틀에 한 번, 모두에게 같다). 없으면 비어 있다 */
    public static Optional<Quest> sideQuest(LocalDate day) {
        long d = day.toEpochDay();
        if (d % 2 != 0) return Optional.empty();
        return Optional.of(SIDES.get((int) ((d / 2) % SIDES.size())));
    }

    public static Crop crop(String id) {
        Crop c = id == null ? null : CROPS.get(id);
        if (c == null) throw new IllegalArgumentException("없는 작물이에요.");
        return c;
    }

    public static void requirePlot(int plot) {
        if (plot < 0 || plot >= PLOTS) throw new IllegalArgumentException("없는 밭이에요.");
    }
}
