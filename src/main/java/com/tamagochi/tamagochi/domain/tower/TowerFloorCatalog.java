package com.tamagochi.tamagochi.domain.tower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 탑의 층 데이터 (resources/tower/floors.json): 공개 층 상한과 층마다의 맵 수.
 * 맵 수는 고정 상수가 아니라 데이터다 — 최종 숫자는 밸런스 시뮬(H-5)로 정하고, 바꿀 때는 JSON만 고친다.
 * 층을 따로 적지 않으면 defaultMapsPerFloor를 쓴다.
 */
public final class TowerFloorCatalog {

    private TowerFloorCatalog() {}

    /** 한 층은 맵 3장보다 많아야 한다 */
    public static final int MIN_MAPS_PER_FLOOR = 4;

    static final String RESOURCE = "/tower/floors.json";

    /** openFloorCap: 지금 열려 있는 가장 높은 층. mapsByFloor: 따로 적은 층의 맵 수 */
    public record Floors(int openFloorCap, int defaultMapsPerFloor, Map<Integer, Integer> mapsByFloor) {

        /** 그 층의 맵 수. 적지 않은 층(상한 밖의 층 포함)은 기본값 */
        public int mapsPerFloor(int floor) {
            return mapsByFloor.getOrDefault(floor, defaultMapsPerFloor);
        }
    }

    private static final Floors FLOORS = load();

    public static Floors floors() {
        return FLOORS;
    }

    /** 지금 열려 있는 가장 높은 층. 이 층을 돌파해도 더 위로는 갈 수 없다 */
    public static int openFloorCap() {
        return FLOORS.openFloorCap();
    }

    public static int mapsPerFloor(int floor) {
        return FLOORS.mapsPerFloor(floor);
    }

    private static Floors load() {
        try (InputStream in = TowerFloorCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("tower/floors.json 이 없어요");
            return parse(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("tower/floors.json 을 읽지 못했어요", e);
        }
    }

    /** 읽으면서 검증한다: 상한은 1 이상, 맵 수는 MIN_MAPS_PER_FLOOR 이상, 층 번호는 1 ~ 상한, 같은 층을 두 번 적지 않는다 */
    static Floors parse(JsonNode root) {
        if (root == null || !root.isObject()) throw new IllegalStateException("tower/floors.json 형식이 잘못됐어요");
        int cap = requireInt(root, "openFloorCap");
        if (cap < TowerRules.FIRST_FLOOR) throw new IllegalStateException("공개 층 상한은 1 이상: " + cap);
        int defaultMaps = requireInt(root, "defaultMapsPerFloor");
        requireMaps(defaultMaps, "기본값");

        Map<Integer, Integer> byFloor = new LinkedHashMap<>();
        JsonNode floors = root.get("floors");
        if (floors != null && !floors.isNull()) {
            if (!floors.isArray()) throw new IllegalStateException("floors 는 목록이어야 해요");
            for (JsonNode n : floors) {
                int floor = requireInt(n, "floor");
                if (floor < TowerRules.FIRST_FLOOR || floor > cap) {
                    throw new IllegalStateException("층 번호는 1~" + cap + ": " + floor);
                }
                int maps = requireInt(n, "mapsPerFloor");
                requireMaps(maps, floor + "층");
                if (byFloor.put(floor, maps) != null) throw new IllegalStateException("중복 층: " + floor);
            }
        }
        return new Floors(cap, defaultMaps, Map.copyOf(byFloor));
    }

    private static int requireInt(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || !v.isInt()) throw new IllegalStateException("tower/floors.json 에 정수 " + field + " 가 없어요");
        return v.asInt();
    }

    private static void requireMaps(int maps, String where) {
        if (maps < MIN_MAPS_PER_FLOOR) {
            throw new IllegalStateException("한 층의 맵 수는 " + MIN_MAPS_PER_FLOOR + " 이상 (" + where + "): " + maps);
        }
    }
}
