package com.tamagochi.tamagochi.domain.yard;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 마당 규칙: 화분이 자라는 시간·수확량, 소품 값(열매), 소품 자리, 살 수 있는 배경(잉크) */
public final class YardCatalog {

    private YardCatalog() {}

    /** 화분이 다 자라는 데 걸리는 시간 */
    public static final Duration GROW = Duration.ofHours(4);
    /** 한 번 딸 때 받는 열매 */
    public static final int HARVEST = 3;
    /** 광고 보고 바로 자라기: 하루(KST) 횟수 */
    public static final int BOOST_PER_DAY = 3;
    /** 다른 진영 배경 값 (잉크) */
    public static final int BACKGROUND_PRICE = 300;

    /** 소품 자리: s0·s1 끄적이 뒤, s2·s3 끄적이 앞 */
    public static final List<String> SLOTS = List.of("s0", "s1", "s2", "s3");

    /** 소품 → 값(열매). 순서대로 상점에 보인다 */
    public static final Map<String, Integer> PROPS;
    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("bush", 3);
        m.put("mailbox", 4);
        m.put("sign", 4);
        m.put("lantern", 5);
        m.put("bench", 6);
        m.put("snowman", 7);
        m.put("tent", 10);
        PROPS = java.util.Collections.unmodifiableMap(m);
    }

    /** 배경으로 고를 수 있는 진영 (진영 배경 그림과 같은 이름) */
    public static final List<String> BACKGROUNDS = List.of(
            "nature-spirits", "deep-savers", "nightmare-soldiers", "wind-guardians", "metal-empire", "virus-busters",
            "dragons-roar", "jungle-troopers", "dark-area", "trash", "space", "desert", "snowfield", "volcano",
            "candy-land", "ninja", "magic-school");

    public static int propPrice(String propId) {
        Integer price = PROPS.get(propId);
        if (price == null) throw new IllegalArgumentException("없는 소품이에요.");
        return price;
    }

    public static void requireSlot(String slot) {
        if (!SLOTS.contains(slot)) throw new IllegalArgumentException("없는 자리예요.");
    }

    public static void requireBackground(String background) {
        if (!BACKGROUNDS.contains(background)) throw new IllegalArgumentException("없는 배경이에요.");
    }
}
