package com.tamagochi.tamagochi.domain.tower;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 층별 몬스터 스탯 (resources/tower/monsters.json): 층마다 일반(normal)·무리(swarm)·보스(boss)의 HP와 한 방 공격.
 * 지금 값은 전부 임시다 — 환산식이 사양에 없어, 시안 1층 값에 층 스케일 1.085^(층−1)을 곱해 반올림한 숫자를 그대로 적었다.
 * 그 곡선은 스탯을 정할 때만 쓰고, 전투력 숫자는 데이터에도 API에도 싣지 않는다.
 * 지금은 1~2층만 있다 (3층 이상과 맵당 마릿수는 M-0/M-1). 서버는 아직 이 값으로 전투를 계산하지 않는다 (퍼히트 검증은 범위 밖).
 * 읽으면서 검증하고, 깨진 값은 넘기지 않고 실패한다.
 */
public final class TowerMonsterCatalog {

    private TowerMonsterCatalog() {}

    static final String RESOURCE = "/tower/monsters.json";

    public enum Role { NORMAL, SWARM, BOSS }

    /** 한 종류의 스탯. attack은 한 방 피해 */
    public record Stats(int hp, int attack) {}

    public record FloorMonsters(int floor, Stats normal, Stats swarm, Stats boss) {

        public Stats of(Role role) {
            return switch (role) {
                case NORMAL -> normal;
                case SWARM -> swarm;
                case BOSS -> boss;
            };
        }
    }

    /** byFloor: 스탯을 적은 층 (1층부터 빠짐없이 이어진다) */
    public record Monsters(Map<Integer, FloorMonsters> byFloor) {

        /** 그 층의 스탯. 아직 적지 않은 층이면 비어 있다 */
        public Optional<FloorMonsters> floor(int floor) {
            return Optional.ofNullable(byFloor.get(floor));
        }
    }

    private static final Set<String> ROOT_FIELDS = Set.of("provisional", "note", "source", "floors");
    private static final Set<String> FLOOR_FIELDS = Set.of("floor", "note", "normal", "swarm", "boss");
    private static final Set<String> STATS_FIELDS = Set.of("hp", "attack", "note");

    private static final Monsters MONSTERS = load();

    public static Monsters monsters() {
        return MONSTERS;
    }

    public static Optional<FloorMonsters> floor(int floor) {
        return MONSTERS.floor(floor);
    }

    private static Monsters load() {
        try (InputStream in = TowerMonsterCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("tower/monsters.json 이 없어요");
            return parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("tower/monsters.json 을 읽지 못했어요", e);
        }
    }

    /** JSON 글을 읽어 검증한다. 문법이 틀렸거나(같은 이름을 두 번 적음, 뒤에 남는 글자 포함) 값이 틀리면 IllegalStateException */
    static Monsters parse(String json) {
        ObjectMapper mapper = new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("tower/monsters.json 문법이 잘못됐어요: " + e.getMessage(), e);
        }
        return parse(root);
    }

    /**
     * 검증: 층 번호는 1부터 빠짐없이 이어지고 공개 층 상한을 넘지 않는다. 층마다 normal·swarm·boss가 모두 있고,
     * hp·attack은 0보다 큰 정수다. 모르는 필드(오타, 전투력 숫자 등)는 받지 않는다
     */
    static Monsters parse(JsonNode root) {
        if (root == null || !root.isObject()) throw new IllegalStateException("tower/monsters.json 형식이 잘못됐어요");
        requireKnownFields(root, ROOT_FIELDS, "맨 위");
        JsonNode floors = root.get("floors");
        if (floors == null || !floors.isArray() || floors.isEmpty()) {
            throw new IllegalStateException("tower/monsters.json 에 floors 목록이 없어요");
        }
        Map<Integer, FloorMonsters> byFloor = new LinkedHashMap<>();
        int expected = TowerRules.FIRST_FLOOR;
        for (JsonNode n : floors) {
            if (!n.isObject()) throw new IllegalStateException("floors 의 항목은 객체여야 해요");
            int floor = requireInt(n, "floor", "floors");
            String where = floor + "층";
            requireKnownFields(n, FLOOR_FIELDS, where);
            if (floor != expected) {
                throw new IllegalStateException("층은 1층부터 빠짐없이 차례로 적어요: " + expected + "층 자리에 " + floor);
            }
            if (floor > TowerFloorCatalog.openFloorCap()) {
                throw new IllegalStateException("공개 층 상한(" + TowerFloorCatalog.openFloorCap() + ")을 넘는 층: " + floor);
            }
            byFloor.put(floor, new FloorMonsters(floor,
                    stats(n, "normal", where), stats(n, "swarm", where), stats(n, "boss", where)));
            expected++;
        }
        return new Monsters(Map.copyOf(byFloor));
    }

    private static Stats stats(JsonNode floorNode, String role, String where) {
        JsonNode n = floorNode.get(role);
        String at = where + " " + role;
        if (n == null || !n.isObject()) throw new IllegalStateException("tower/monsters.json " + at + " 가 없어요");
        requireKnownFields(n, STATS_FIELDS, at);
        return new Stats(requirePositive(n, "hp", at), requirePositive(n, "attack", at));
    }

    private static int requirePositive(JsonNode node, String field, String where) {
        int v = requireInt(node, field, where);
        if (v <= 0) throw new IllegalStateException(where + " 의 " + field + " 는 0보다 커야 해요: " + v);
        return v;
    }

    private static int requireInt(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isInt()) {
            throw new IllegalStateException("tower/monsters.json " + where + " 에 정수 " + field + " 가 없어요");
        }
        return v.asInt();
    }

    private static void requireKnownFields(JsonNode node, Set<String> allowed, String where) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!allowed.contains(name)) {
                throw new IllegalStateException("tower/monsters.json " + where + " 에 모르는 필드: " + name);
            }
        }
    }
}
