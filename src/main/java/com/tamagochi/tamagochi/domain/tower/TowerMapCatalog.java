package com.tamagochi.tamagochi.domain.tower;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Point;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Rescue;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Spawn;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Theme;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Tile;
import com.tamagochi.tamagochi.domain.tower.TowerMap.TileType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 탑의 맵 데이터 (resources/tower/maps.json, M-1). 파일에는 층·맵마다의 고정 시드·크기와 테마만 있고,
 * 칸 배치는 TowerMapAssembler가 시드로 조립한다 (결정적). 몬스터 수·구출 NPC id는 floors.json(TowerFloorCatalog)에서 가져온다.
 * 구출 자리는 그 층의 보스 맵(보스가 있는 마지막 맵)에만 둔다 — 자리만 있고 구출 로직은 없다.
 * 지금은 1~2층만 있다. 맵이 없는 층은 비어 있는 값으로 답한다.
 * 읽으면서 검증하고 (스폰 수, 걸을 수 있는 자리, 입구에서 출구·스폰·구출 자리까지의 길, 보스 경기장), 깨진 값은 넘기지 않고 실패한다.
 */
public final class TowerMapCatalog {

    private TowerMapCatalog() {}

    static final String RESOURCE = "/tower/maps.json";

    private static final Set<String> ROOT_FIELDS = Set.of("provisional", "note", "tileSize", "themes", "floors");
    private static final Set<String> THEME_FIELDS = Set.of("id", "name", "faction", "background", "tiles", "note");
    private static final Set<String> TILE_FIELDS = Set.of("name", "color");
    private static final Set<String> FLOOR_FIELDS = Set.of("floor", "theme", "maps", "note");
    private static final Set<String> MAP_FIELDS = Set.of("map", "seed", "width", "height", "note");

    /** 층 → 그 층의 맵 (맵 번호 순서) */
    private static final Map<Integer, List<TowerMap>> MAPS = load();

    /** 맵이 있는 층 번호 (오름차순) */
    public static List<Integer> floorsWithMaps() {
        return List.copyOf(MAPS.keySet());
    }

    /** 그 층의 맵 전부. 아직 맵이 없는 층이면 빈 목록 */
    public static List<TowerMap> floorMaps(int floor) {
        return MAPS.getOrDefault(floor, List.of());
    }

    /** 맵 한 장. 없으면 비어 있다 */
    public static Optional<TowerMap> map(int floor, int map) {
        List<TowerMap> maps = floorMaps(floor);
        return map >= 1 && map <= maps.size() ? Optional.of(maps.get(map - 1)) : Optional.empty();
    }

    private static Map<Integer, List<TowerMap>> load() {
        try (InputStream in = TowerMapCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("tower/maps.json 이 없어요");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), TowerFloorCatalog.floors());
        } catch (IOException e) {
            throw new IllegalStateException("tower/maps.json 을 읽지 못했어요", e);
        }
    }

    /** JSON 글을 읽어 맵을 조립하고 검증한다. 문법이나 값이 틀리면 IllegalStateException */
    static Map<Integer, List<TowerMap>> parse(String json, TowerFloorCatalog.Floors floors) {
        ObjectMapper mapper = new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("tower/maps.json 문법이 잘못됐어요: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) throw new IllegalStateException("tower/maps.json 형식이 잘못됐어요");
        requireKnownFields(root, ROOT_FIELDS, "맨 위");
        boolean provisional = root.path("provisional").asBoolean(false);
        int tileSize = requireInt(root, "tileSize", "맨 위");
        if (tileSize < 1) throw new IllegalStateException("tileSize 는 1 이상: " + tileSize);

        Map<String, Theme> themes = new LinkedHashMap<>();
        Map<String, String> themeFaction = new LinkedHashMap<>();
        for (JsonNode n : requireArray(root, "themes", "맨 위")) {
            requireKnownFields(n, THEME_FIELDS, "테마");
            String id = requireText(n, "id", "테마");
            if (themes.put(id, theme(n, id)) != null) throw new IllegalStateException("중복 테마: " + id);
            themeFaction.put(id, requireText(n, "faction", "테마 " + id));
        }

        Map<Integer, List<TowerMap>> byFloor = new LinkedHashMap<>();
        Set<Long> seeds = new HashSet<>();
        int previousFloor = 0;
        for (JsonNode n : requireArray(root, "floors", "맨 위")) {
            int floorNo = requireInt(n, "floor", "floors");
            String where = floorNo + "층";
            requireKnownFields(n, FLOOR_FIELDS, where);
            if (floorNo <= previousFloor) throw new IllegalStateException("층은 오름차순으로 한 번씩 적어요: " + where);
            previousFloor = floorNo;
            TowerFloorCatalog.Floor floor = floors.floor(floorNo)
                    .orElseThrow(() -> new IllegalStateException("floors.json 에 없는 층: " + where));
            if (floor.maps().isEmpty()) throw new IllegalStateException(where + " 은 floors.json 에 몬스터 수가 없어요");
            String themeId = requireText(n, "theme", where);
            Theme theme = themes.get(themeId);
            if (theme == null) throw new IllegalStateException(where + " 의 모르는 테마: " + themeId);
            if (!floor.faction().equals(themeFaction.get(themeId))) {
                throw new IllegalStateException(where + " 의 진영(" + floor.faction() + ")과 테마 " + themeId + " 의 진영이 달라요");
            }
            JsonNode maps = requireArray(n, "maps", where);
            if (maps.size() != floor.mapsPerFloor()) {
                throw new IllegalStateException(where + " 의 맵 수(" + maps.size() + ")가 mapsPerFloor(" + floor.mapsPerFloor() + ")와 달라요");
            }
            int rescueMap = 0; // 구출 자리를 두는 맵: 보스가 있는 마지막 맵
            for (TowerFloorCatalog.MapMonsters m : floor.maps()) if (m.boss() > 0) rescueMap = m.map();
            if (rescueMap == 0 && !floor.rescueNpcIds().isEmpty()) {
                throw new IllegalStateException(where + " 에 구출 NPC가 있는데 보스 맵이 없어요");
            }

            List<TowerMap> assembled = new ArrayList<>();
            int expected = 1;
            for (JsonNode m : maps) {
                String at = where + " 맵 " + expected;
                requireKnownFields(m, MAP_FIELDS, at);
                int mapNo = requireInt(m, "map", at);
                if (mapNo != expected) throw new IllegalStateException(where + " 의 맵은 1부터 차례로 적어요: " + expected + " 자리에 " + mapNo);
                JsonNode seedNode = m.get("seed");
                if (seedNode == null || !seedNode.isIntegralNumber() || !seedNode.canConvertToLong()) {
                    throw new IllegalStateException(at + " 에 정수 seed 가 없어요");
                }
                long seed = seedNode.asLong();
                // 프론트(JS)가 숫자로 그대로 읽을 수 있는 범위만 받는다
                if (seed < 0 || seed > 9_007_199_254_740_991L) throw new IllegalStateException(at + " 의 seed 는 0 ~ 2^53-1: " + seed);
                if (!seeds.add(seed)) throw new IllegalStateException("같은 seed를 두 맵에 쓸 수 없어요: " + seed);
                TowerFloorCatalog.MapMonsters monsters = floor.map(mapNo).orElseThrow();
                List<String> rescueIds = mapNo == rescueMap ? floor.rescueNpcIds() : List.of();
                TowerMap map = TowerMapAssembler.assemble(new TowerMapAssembler.Request(floorNo, mapNo, floor.faction(), seed,
                        requireInt(m, "width", at), requireInt(m, "height", at), tileSize,
                        monsters.normal(), monsters.swarm(), monsters.boss(), rescueIds, theme, provisional));
                validate(map, monsters, rescueIds);
                assembled.add(map);
                expected++;
            }
            byFloor.put(floorNo, List.copyOf(assembled));
        }
        return java.util.Collections.unmodifiableMap(byFloor);
    }

    private static Theme theme(JsonNode n, String id) {
        String where = "테마 " + id;
        JsonNode tiles = n.get("tiles");
        if (tiles == null || !tiles.isObject()) throw new IllegalStateException(where + " 에 tiles 가 없어요");
        Set<String> keys = new HashSet<>();
        List<TileType> types = new ArrayList<>();
        for (Tile tile : Tile.values()) {
            keys.add(tile.key());
            JsonNode t = tiles.get(tile.key());
            String at = where + " " + tile.key();
            if (t == null || !t.isObject()) throw new IllegalStateException(at + " 칸이 없어요");
            requireKnownFields(t, TILE_FIELDS, at);
            String color = requireText(t, "color", at);
            if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalStateException(at + " 의 색은 #rrggbb: " + color);
            types.add(new TileType(tile.id(), tile.key(), requireText(t, "name", at), tile.walkable(), color));
        }
        requireKnownFields(tiles, keys, where + " tiles");
        return new Theme(id, requireText(n, "name", where), requireText(n, "background", where), List.copyOf(types));
    }

    /**
     * 조립한 맵을 확인한다: 크기, 칸 id와 충돌이 맞는지, 스폰 수가 floors.json과 같은지, 보스 경기장이 보스 맵에만 있는지,
     * 구출 자리가 맞는지, 입구·출구·스폰·구출 자리가 서로 다른 걸을 수 있는 칸이고 입구에서 걸어서 닿는지
     */
    static void validate(TowerMap map, TowerFloorCatalog.MapMonsters monsters, List<String> rescueIds) {
        String where = map.floor() + "층 맵 " + map.map();
        int w = map.width();
        int h = map.height();
        if (map.tiles().size() != h || map.collision().size() != h) throw new IllegalStateException(where + " 의 줄 수가 달라요");
        boolean[][] walkable = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            if (map.tiles().get(y).size() != w || map.collision().get(y).size() != w) {
                throw new IllegalStateException(where + " 의 칸 수가 달라요");
            }
            for (int x = 0; x < w; x++) {
                int id = map.tiles().get(y).get(x);
                if (id < 0 || id >= Tile.values().length) throw new IllegalStateException(where + " 에 모르는 칸: " + id);
                walkable[y][x] = Tile.values()[id].walkable();
                if (map.collision().get(y).get(x) != (walkable[y][x] ? 0 : 1)) {
                    throw new IllegalStateException(where + " 의 충돌이 칸 종류와 달라요: " + x + "," + y);
                }
                if (walkable[y][x] && (x == 0 || y == 0 || x == w - 1 || y == h - 1)) {
                    throw new IllegalStateException(where + " 의 테두리가 뚫려 있어요: " + x + "," + y);
                }
            }
        }

        int normal = 0, swarm = 0, boss = 0;
        List<Point> spots = new ArrayList<>(List.of(map.entry(), map.exit()));
        for (Spawn s : map.spawns()) {
            switch (s.role()) {
                case TowerMap.ROLE_NORMAL -> normal++;
                case TowerMap.ROLE_SWARM -> swarm++;
                case TowerMap.ROLE_BOSS -> boss++;
                default -> throw new IllegalStateException(where + " 의 모르는 스폰 역할: " + s.role());
            }
            if (TowerMap.ROLE_BOSS.equals(s.role()) && (map.bossArena() == null || !map.bossArena().contains(s.x(), s.y()))) {
                throw new IllegalStateException(where + " 의 보스가 경기장 밖에 있어요");
            }
            spots.add(new Point(s.x(), s.y()));
        }
        if (normal != monsters.normal() || swarm != monsters.swarm() || boss != monsters.boss()) {
            throw new IllegalStateException(where + " 의 스폰 수가 floors.json 과 달라요");
        }
        if ((map.bossArena() != null) != (monsters.boss() > 0)) {
            throw new IllegalStateException(where + ": 보스 경기장은 보스 맵에만 있어요");
        }
        List<String> ids = new ArrayList<>();
        for (Rescue r : map.rescues()) {
            ids.add(r.npcId());
            spots.add(new Point(r.x(), r.y()));
        }
        if (!ids.equals(rescueIds)) throw new IllegalStateException(where + " 의 구출 자리가 달라요: " + ids);

        boolean[][] seen = new boolean[h][w];
        if (new HashSet<>(spots).size() != spots.size()) throw new IllegalStateException(where + " 에 겹치는 자리가 있어요");
        for (Point p : spots) {
            if (p.x() < 0 || p.y() < 0 || p.x() >= w || p.y() >= h || !walkable[p.y()][p.x()]) {
                throw new IllegalStateException(where + " 에 걸을 수 없는 자리: " + p);
            }
        }
        ArrayDeque<Point> queue = new ArrayDeque<>();
        seen[map.entry().y()][map.entry().x()] = true;
        queue.add(map.entry());
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            for (int[] s : steps) {
                int x = p.x() + s[0];
                int y = p.y() + s[1];
                if (x < 0 || y < 0 || x >= w || y >= h || seen[y][x] || !walkable[y][x]) continue;
                seen[y][x] = true;
                queue.add(new Point(x, y));
            }
        }
        for (Point p : spots) {
            if (!seen[p.y()][p.x()]) throw new IllegalStateException(where + " 에서 입구에서 닿지 않는 자리: " + p);
        }
    }

    private static JsonNode requireArray(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isArray()) throw new IllegalStateException("tower/maps.json " + where + " 에 목록 " + field + " 가 없어요");
        for (JsonNode n : v) {
            if (!n.isObject()) throw new IllegalStateException("tower/maps.json " + where + " 의 " + field + " 항목은 객체여야 해요");
        }
        return v;
    }

    private static int requireInt(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isInt()) throw new IllegalStateException("tower/maps.json " + where + " 에 정수 " + field + " 가 없어요");
        return v.asInt();
    }

    private static String requireText(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalStateException("tower/maps.json " + where + " 에 글자 " + field + " 가 없어요");
        }
        return v.asText();
    }

    private static void requireKnownFields(JsonNode node, Set<String> known, String where) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (!known.contains(field)) throw new IllegalStateException("tower/maps.json " + where + " 에 모르는 필드: " + field);
        }
    }
}
