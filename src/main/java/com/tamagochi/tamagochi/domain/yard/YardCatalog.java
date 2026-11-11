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

    // ── 끄적이 방 (잘 때 가는 곳). 가구도 열매로 사고, 종류(kind)가 맞는 자리에만 놓는다
    //    벽지·바닥·침대 자리가 비어 있으면 앱이 기본 그림을 그린다

    /** 방 가구 하나: 값(열매)과 종류 */
    public record RoomItem(String id, int price, String kind) {}

    /** 방 자리 → 놓을 수 있는 종류 */
    public static final Map<String, String> ROOM_SLOTS;
    /** 방 가구. 순서대로 상점에 보인다 */
    public static final Map<String, RoomItem> ROOM_ITEMS;
    static {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("r-wallpaper", "wallpaper");
        s.put("r-floor", "floor");
        s.put("r-bed", "bed");
        // 벽 장식·가구는 여섯 개까지 놓는다 (자리는 이름일 뿐이고, 어디에 둘지는 roomPos가 정한다)
        for (int i = 0; i < 6; i++) s.put("r-w" + i, "wall");
        for (int i = 0; i < 6; i++) s.put("r-s" + i, "stand");
        s.put("r-rug", "rug");
        ROOM_SLOTS = java.util.Collections.unmodifiableMap(s);

        Map<String, RoomItem> m = new LinkedHashMap<>();
        for (RoomItem i : List.of(
                new RoomItem("bed-wood", 12, "bed"), new RoomItem("bed-cloud", 25, "bed"), new RoomItem("bed-royal", 40, "bed"),
                new RoomItem("wp-sky", 6, "wallpaper"), new RoomItem("wp-dot", 6, "wallpaper"), new RoomItem("wp-flower", 10, "wallpaper"),
                new RoomItem("wp-star", 10, "wallpaper"), new RoomItem("wp-wood", 8, "wallpaper"),
                new RoomItem("fl-check", 6, "floor"), new RoomItem("fl-carpet", 8, "floor"), new RoomItem("fl-grass", 8, "floor"),
                new RoomItem("frame", 5, "wall"), new RoomItem("garland", 5, "wall"), new RoomItem("clock", 6, "wall"), new RoomItem("shelf", 8, "wall"),
                new RoomItem("plant", 5, "stand"), new RoomItem("lamp", 8, "stand"), new RoomItem("toybox", 8, "stand"),
                new RoomItem("drawer", 10, "stand"), new RoomItem("bookshelf", 12, "stand"),
                new RoomItem("rug-round", 6, "rug"), new RoomItem("rug-star", 8, "rug"), new RoomItem("rug-heart", 8, "rug")))
            m.put(i.id(), i);
        ROOM_ITEMS = java.util.Collections.unmodifiableMap(m);
    }

    /** 배경으로 고를 수 있는 진영 (진영 배경 그림과 같은 이름) */
    public static final List<String> BACKGROUNDS = List.of(
            "nature-spirits", "deep-savers", "nightmare-soldiers", "wind-guardians", "metal-empire", "virus-busters",
            "dragons-roar", "jungle-troopers", "dark-area", "trash", "space", "desert", "snowfield", "volcano",
            "candy-land", "ninja", "magic-school");

    public static int propPrice(String propId) {
        Integer price = PROPS.get(propId);
        if (price != null) return price;
        RoomItem item = ROOM_ITEMS.get(propId);
        if (item == null) throw new IllegalArgumentException("없는 소품이에요.");
        return item.price();
    }

    public static void requireSlot(String slot) {
        if (!SLOTS.contains(slot) && !ROOM_SLOTS.containsKey(slot)) throw new IllegalArgumentException("없는 자리예요.");
    }

    /**
     * 방 안에서 옮길 수 있는 범위 (방 폭·높이의 천분율, {x최소, x최대, y최소, y최대}).
     * 벽 장식은 벽 안에서, 가구·러그·침대는 바닥 안에서만 움직인다. 벽지·바닥은 옮기는 것이 아니다.
     * 좌표 기준: 벽 장식·러그는 그림 가운데, 가구는 발밑, 침대는 눕는 면
     */
    public static int[] roomZone(String slot) {
        String kind = ROOM_SLOTS.get(slot);
        if (kind == null) throw new IllegalArgumentException("없는 자리예요.");
        return switch (kind) {
            case "wall" -> new int[]{40, 960, 60, 600};
            case "stand" -> new int[]{60, 940, 715, 985};
            case "rug" -> new int[]{120, 880, 740, 975};
            case "bed" -> new int[]{180, 820, 740, 920};
            default -> throw new IllegalArgumentException("옮길 수 없는 자리예요.");
        };
    }

    public static int[] clampRoomPos(String slot, int x, int y) {
        int[] z = roomZone(slot);
        return new int[]{Math.max(z[0], Math.min(z[1], x)), Math.max(z[2], Math.min(z[3], y))};
    }

    /** 마당 소품은 마당 자리에, 방 가구는 종류가 같은 방 자리에만 놓는다 */
    public static void requireFit(String slot, String propId) {
        requireSlot(slot);
        RoomItem item = ROOM_ITEMS.get(propId);
        boolean fits = item == null ? SLOTS.contains(slot) && PROPS.containsKey(propId) : item.kind().equals(ROOM_SLOTS.get(slot));
        if (!fits) throw new IllegalArgumentException("이 자리에는 놓을 수 없어요.");
    }

    public static void requireBackground(String background) {
        if (!BACKGROUNDS.contains(background)) throw new IllegalArgumentException("없는 배경이에요.");
    }
}
