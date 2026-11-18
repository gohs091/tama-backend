package com.tamagochi.tamagochi.domain.tower;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 탑의 층 데이터 (resources/tower/floors.json, M-0): 공개 층 상한, 층마다의 바퀴·진영·맵 수·구출 NPC id·이야기 소품·맵별 몬스터 수.
 * 스키마는 1~51층 전부를 담는다 (17층씩 3바퀴: 창고·공장·본사). 진영은 바퀴마다 한 번씩, inks/regions.json 순서 그대로다.
 * 공개 층 상한(openFloorCap)은 "지금 들어갈 수 있는 가장 높은 층"일 뿐이라, 상한보다 위 층도 데이터에는 있다.
 * 지금은 1~2층만 내용이 있고 3~51층은 골격(층·바퀴·진영·맵 수)뿐이다. 맵별 몬스터 수는 전부 임시다.
 * 맵 수는 고정 상수가 아니라 데이터다 — 최종 숫자는 밸런스 시뮬(H-5)로 정하고, 바꿀 때는 JSON만 고친다.
 * 구출 NPC는 id만 적는다 (구출 로직·npcs.json 대조는 여기서 하지 않는다). 전투력 숫자는 싣지 않는다.
 * 읽으면서 검증하고, 깨진 값은 넘기지 않고 실패한다.
 */
public final class TowerFloorCatalog {

    private TowerFloorCatalog() {}

    /** 한 층은 맵 3장보다 많아야 한다 */
    public static final int MIN_MAPS_PER_FLOOR = 4;

    /** 한 바퀴의 층 수 (= 진영 수) */
    public static final int FLOORS_PER_WHEEL = 17;

    /** 바퀴 수: 1 창고, 2 공장, 3 본사 */
    public static final int WHEEL_COUNT = 3;

    /** 스키마가 담는 층 수 (1~51). 들어갈 수 있는 층은 openFloorCap까지다 */
    public static final int SCHEMA_FLOOR_COUNT = FLOORS_PER_WHEEL * WHEEL_COUNT;

    static final String RESOURCE = "/tower/floors.json";
    static final String REGIONS_RESOURCE = "/inks/regions.json";

    /** 바퀴: 번호(1부터), 이름, 그 바퀴의 첫 층과 마지막 층 */
    public record Wheel(int wheel, String name, int firstFloor, int lastFloor) {}

    /** 맵 한 장의 몬스터 수 (map은 1부터). provisional: 임시 값인가 */
    public record MapMonsters(int map, int normal, int swarm, int boss, boolean provisional) {}

    /** 이야기 소품: 짧은 제목만. provisional: 임시 값인가 */
    public record Story(String title, boolean provisional) {}

    /**
     * 한 층. rescueNpcIds: 이 층에서 구출하는 NPC의 id (없으면 빈 목록), story: 이야기 소품 (없으면 null),
     * maps: 맵별 몬스터 수 (아직 적지 않은 층은 빈 목록, 적었으면 길이가 mapsPerFloor와 같다)
     */
    public record Floor(int floor, int wheel, String faction, int mapsPerFloor, List<String> rescueNpcIds,
                        Story story, List<MapMonsters> maps) {

        /** 그 맵의 몬스터 수. 아직 적지 않았으면 비어 있다 */
        public Optional<MapMonsters> map(int map) {
            return map >= 1 && map <= maps.size() ? Optional.of(maps.get(map - 1)) : Optional.empty();
        }
    }

    /** openFloorCap: 지금 열려 있는 가장 높은 층. byFloor: 1~51층 */
    public record Floors(int openFloorCap, int defaultMapsPerFloor, List<Wheel> wheels, Map<Integer, Floor> byFloor) {

        /** 그 층의 데이터. 스키마 밖의 층이면 비어 있다 */
        public Optional<Floor> floor(int floor) {
            return Optional.ofNullable(byFloor.get(floor));
        }

        /** 그 층의 맵 수. 스키마 밖의 층은 기본값 */
        public int mapsPerFloor(int floor) {
            Floor f = byFloor.get(floor);
            return f == null ? defaultMapsPerFloor : f.mapsPerFloor();
        }
    }

    private static final Set<String> ROOT_FIELDS =
            Set.of("provisional", "note", "source", "openFloorCap", "defaultMapsPerFloor", "wheels", "floors");
    private static final Set<String> WHEEL_FIELDS = Set.of("wheel", "name", "firstFloor", "lastFloor", "note");
    private static final Set<String> FLOOR_FIELDS =
            Set.of("floor", "wheel", "faction", "mapsPerFloor", "rescueNpcIds", "story", "maps", "note");
    private static final Set<String> STORY_FIELDS = Set.of("title", "provisional", "note");
    private static final Set<String> MAP_FIELDS = Set.of("map", "normal", "swarm", "boss", "provisional", "note");

    // 순서 주의: 층 데이터를 읽을 때 진영 순서가 먼저 있어야 한다
    private static final List<String> FACTIONS = loadFactions();
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

    public static Optional<Floor> floor(int floor) {
        return FLOORS.floor(floor);
    }

    /** 진영 순서 (inks/regions.json 그대로) */
    public static List<String> factions() {
        return FACTIONS;
    }

    /** 그 층의 바퀴 (1부터): 1~17층은 1, 18~34층은 2, 35~51층은 3 */
    public static int wheelOf(int floor) {
        return (floor - 1) / FLOORS_PER_WHEEL + 1;
    }

    /** 그 층의 진영: 바퀴마다 같은 순서로 한 번씩 */
    public static String factionOf(int floor) {
        return FACTIONS.get((floor - 1) % FLOORS_PER_WHEEL);
    }

    private static List<String> loadFactions() {
        try (InputStream in = TowerFloorCatalog.class.getResourceAsStream(REGIONS_RESOURCE)) {
            if (in == null) throw new IllegalStateException("inks/regions.json 이 없어요");
            JsonNode root = new ObjectMapper().readTree(in);
            List<String> ids = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode id : root) ids.add(id.asText());
            }
            if (ids.size() != FLOORS_PER_WHEEL || new HashSet<>(ids).size() != ids.size()) {
                throw new IllegalStateException("inks/regions.json 의 진영은 서로 다른 " + FLOORS_PER_WHEEL + "개여야 해요: " + ids.size());
            }
            return List.copyOf(ids);
        } catch (IOException e) {
            throw new IllegalStateException("inks/regions.json 을 읽지 못했어요", e);
        }
    }

    private static Floors load() {
        try (InputStream in = TowerFloorCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("tower/floors.json 이 없어요");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("tower/floors.json 을 읽지 못했어요", e);
        }
    }

    /** JSON 글을 읽어 검증한다. 문법이 틀렸거나(같은 이름을 두 번 적음, 뒤에 남는 글자 포함) 값이 틀리면 IllegalStateException */
    static Floors parse(String json) {
        ObjectMapper mapper = new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("tower/floors.json 문법이 잘못됐어요: " + e.getMessage(), e);
        }
        return parse(root);
    }

    /**
     * 검증: 상한은 1 ~ 51, 맵 수는 MIN_MAPS_PER_FLOOR 이상. 바퀴는 1~3이 차례로 있고 층 범위가 17층씩이다.
     * 층은 1~51이 모두 있고 같은 층을 두 번 적지 않는다 (상한보다 위 층도 받는다). 층의 바퀴·진영은 층 번호로 정해진 값과 같아야 한다.
     * maps를 적었으면 길이가 그 층의 mapsPerFloor와 같고, 맵 번호가 1부터 차례로 이어진다. 모르는 필드(오타, 전투력 숫자 등)는 받지 않는다
     */
    static Floors parse(JsonNode root) {
        if (root == null || !root.isObject()) throw new IllegalStateException("tower/floors.json 형식이 잘못됐어요");
        requireKnownFields(root, ROOT_FIELDS, "맨 위");
        int cap = requireInt(root, "openFloorCap", "맨 위");
        if (cap < TowerRules.FIRST_FLOOR || cap > SCHEMA_FLOOR_COUNT) {
            throw new IllegalStateException("공개 층 상한은 1~" + SCHEMA_FLOOR_COUNT + ": " + cap);
        }
        int defaultMaps = requireInt(root, "defaultMapsPerFloor", "맨 위");
        requireMaps(defaultMaps, "기본값");

        List<Wheel> wheels = wheels(root.get("wheels"));

        JsonNode floors = root.get("floors");
        if (floors == null || !floors.isArray()) throw new IllegalStateException("tower/floors.json 에 floors 목록이 없어요");
        Map<Integer, Floor> byFloor = new LinkedHashMap<>();
        for (JsonNode n : floors) {
            if (!n.isObject()) throw new IllegalStateException("floors 의 항목은 객체여야 해요");
            Floor floor = floor(n);
            if (byFloor.put(floor.floor(), floor) != null) throw new IllegalStateException("중복 층: " + floor.floor());
        }
        for (int f = TowerRules.FIRST_FLOOR; f <= SCHEMA_FLOOR_COUNT; f++) {
            if (!byFloor.containsKey(f)) throw new IllegalStateException("빠진 층: " + f);
        }
        return new Floors(cap, defaultMaps, wheels, Map.copyOf(byFloor));
    }

    private static List<Wheel> wheels(JsonNode node) {
        if (node == null || !node.isArray() || node.size() != WHEEL_COUNT) {
            throw new IllegalStateException("tower/floors.json 의 wheels 는 " + WHEEL_COUNT + "개여야 해요");
        }
        List<Wheel> wheels = new ArrayList<>();
        int expected = 1;
        for (JsonNode n : node) {
            if (!n.isObject()) throw new IllegalStateException("wheels 의 항목은 객체여야 해요");
            String where = expected + "바퀴";
            requireKnownFields(n, WHEEL_FIELDS, where);
            int wheel = requireInt(n, "wheel", where);
            int first = requireInt(n, "firstFloor", where);
            int last = requireInt(n, "lastFloor", where);
            if (wheel != expected || first != (wheel - 1) * FLOORS_PER_WHEEL + 1 || last != wheel * FLOORS_PER_WHEEL) {
                throw new IllegalStateException("바퀴는 1부터 차례로, " + FLOORS_PER_WHEEL + "층씩이에요: " + where);
            }
            wheels.add(new Wheel(wheel, requireText(n, "name", where), first, last));
            expected++;
        }
        return List.copyOf(wheels);
    }

    private static Floor floor(JsonNode n) {
        int floor = requireInt(n, "floor", "floors");
        String where = floor + "층";
        requireKnownFields(n, FLOOR_FIELDS, where);
        if (floor < TowerRules.FIRST_FLOOR || floor > SCHEMA_FLOOR_COUNT) {
            throw new IllegalStateException("층 번호는 1~" + SCHEMA_FLOOR_COUNT + ": " + floor);
        }
        int wheel = requireInt(n, "wheel", where);
        if (wheel != wheelOf(floor)) {
            throw new IllegalStateException(where + " 의 바퀴는 " + wheelOf(floor) + " 이어야 해요: " + wheel);
        }
        String faction = requireText(n, "faction", where);
        if (!faction.equals(factionOf(floor))) {
            throw new IllegalStateException(where + " 의 진영은 " + factionOf(floor) + " 이어야 해요 (regions.json 순서): " + faction);
        }
        int maps = requireInt(n, "mapsPerFloor", where);
        requireMaps(maps, where);
        return new Floor(floor, wheel, faction, maps, rescueNpcIds(n.get("rescueNpcIds"), where),
                story(n.get("story"), where), maps(n.get("maps"), maps, where));
    }

    /** 구출 NPC id 목록. 없거나 비어 있어도 된다. 빈 글자·같은 id 두 번은 받지 않는다 (npcs.json 대조는 하지 않는다) */
    private static List<String> rescueNpcIds(JsonNode node, String where) {
        if (node == null) return List.of();
        if (!node.isArray()) throw new IllegalStateException(where + " 의 rescueNpcIds 는 목록이어야 해요");
        List<String> ids = new ArrayList<>();
        for (JsonNode id : node) {
            if (!id.isTextual() || id.asText().isBlank()) {
                throw new IllegalStateException(where + " 의 rescueNpcIds 에는 비어 있지 않은 글자만 적어요");
            }
            if (ids.contains(id.asText())) throw new IllegalStateException(where + " 의 구출 NPC 중복: " + id.asText());
            ids.add(id.asText());
        }
        return List.copyOf(ids);
    }

    private static Story story(JsonNode node, String where) {
        if (node == null) return null;
        String at = where + " story";
        if (!node.isObject()) throw new IllegalStateException(at + " 는 객체여야 해요");
        requireKnownFields(node, STORY_FIELDS, at);
        return new Story(requireText(node, "title", at), optionalBoolean(node, "provisional", at));
    }

    private static List<MapMonsters> maps(JsonNode node, int mapsPerFloor, String where) {
        if (node == null) return List.of();
        if (!node.isArray()) throw new IllegalStateException(where + " 의 maps 는 목록이어야 해요");
        if (node.isEmpty()) return List.of();
        if (node.size() != mapsPerFloor) {
            throw new IllegalStateException(where + " 의 maps 길이(" + node.size() + ")가 mapsPerFloor(" + mapsPerFloor + ")와 달라요");
        }
        List<MapMonsters> maps = new ArrayList<>();
        int expected = 1;
        for (JsonNode n : node) {
            String at = where + " 맵 " + expected;
            if (!n.isObject()) throw new IllegalStateException(at + " 는 객체여야 해요");
            requireKnownFields(n, MAP_FIELDS, at);
            int map = requireInt(n, "map", at);
            if (map != expected) throw new IllegalStateException(where + " 의 맵은 1부터 차례로 적어요: " + expected + " 자리에 " + map);
            int normal = requireCount(n, "normal", at);
            int swarm = requireCount(n, "swarm", at);
            int boss = requireCount(n, "boss", at);
            if (normal + swarm + boss == 0) throw new IllegalStateException(at + " 에 몬스터가 없어요");
            maps.add(new MapMonsters(map, normal, swarm, boss, optionalBoolean(n, "provisional", at)));
            expected++;
        }
        return List.copyOf(maps);
    }

    private static int requireCount(JsonNode node, String field, String where) {
        int v = requireInt(node, field, where);
        if (v < 0) throw new IllegalStateException(where + " 의 " + field + " 는 0 이상이어야 해요: " + v);
        return v;
    }

    private static int requireInt(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isInt()) {
            throw new IllegalStateException("tower/floors.json " + where + " 에 정수 " + field + " 가 없어요");
        }
        return v.asInt();
    }

    private static String requireText(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalStateException("tower/floors.json " + where + " 에 글자 " + field + " 가 없어요");
        }
        return v.asText();
    }

    private static boolean optionalBoolean(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null) return false;
        if (!v.isBoolean()) throw new IllegalStateException(where + " 의 " + field + " 는 true/false 여야 해요");
        return v.asBoolean();
    }

    private static void requireKnownFields(JsonNode node, Set<String> known, String where) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (!known.contains(field)) {
                throw new IllegalStateException("tower/floors.json " + where + " 에 모르는 필드: " + field);
            }
        }
    }

    private static void requireMaps(int maps, String where) {
        if (maps < MIN_MAPS_PER_FLOOR) {
            throw new IllegalStateException("한 층의 맵 수는 " + MIN_MAPS_PER_FLOOR + " 이상 (" + where + "): " + maps);
        }
    }
}
