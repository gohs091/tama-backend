package com.tamagochi.tamagochi.domain.tower;

import com.tamagochi.tamagochi.domain.tower.TowerMap.Point;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Rescue;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Spawn;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 맵 데이터(M-1): 1~2층 맵 10장, 스폰 수, 서로 다른 배치, 결정적 조립, 걸어서 닿는 자리, 보스 경기장·구출 자리 */
class TowerMapCatalogTest {

    private static List<TowerMap> all() {
        List<TowerMap> maps = new ArrayList<>();
        for (int floor : TowerMapCatalog.floorsWithMaps()) maps.addAll(TowerMapCatalog.floorMaps(floor));
        return maps;
    }

    private static String name(TowerMap m) {
        return m.floor() + "층 맵 " + m.map();
    }

    private static String json() throws Exception {
        try (InputStream in = TowerMapCatalog.class.getResourceAsStream(TowerMapCatalog.RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 걸을 수 있는 칸 (collision 0) */
    private static boolean[][] walkable(TowerMap m) {
        boolean[][] grid = new boolean[m.height()][m.width()];
        for (int y = 0; y < m.height(); y++) {
            for (int x = 0; x < m.width(); x++) grid[y][x] = m.collision().get(y).get(x) == 0;
        }
        return grid;
    }

    /** 입구에서 상하좌우로 걸어서 닿는 칸 */
    private static boolean[][] reachable(TowerMap m) {
        boolean[][] walkable = walkable(m);
        boolean[][] seen = new boolean[m.height()][m.width()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seen[m.entry().y()][m.entry().x()] = true;
        queue.add(new int[]{m.entry().x(), m.entry().y()});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] s : steps) {
                int x = p[0] + s[0];
                int y = p[1] + s[1];
                if (x < 0 || y < 0 || x >= m.width() || y >= m.height() || seen[y][x] || !walkable[y][x]) continue;
                seen[y][x] = true;
                queue.add(new int[]{x, y});
            }
        }
        return seen;
    }

    /** 격자를 글자로 (줄마다 한 줄) */
    private static String text(int[][] grid) {
        StringBuilder sb = new StringBuilder();
        for (int[] row : grid) {
            for (int v : row) sb.append((char) ('a' + v));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static int[][] grid(List<List<Integer>> rows) {
        int[][] grid = new int[rows.size()][];
        for (int y = 0; y < rows.size(); y++) grid[y] = rows.get(y).stream().mapToInt(Integer::intValue).toArray();
        return grid;
    }

    /** 돌리고(0·90·180·270도) 뒤집은 여덟 모양 */
    private static Set<String> orientations(int[][] grid) {
        Set<String> out = new HashSet<>();
        int[][] g = grid;
        for (int i = 0; i < 4; i++) {
            out.add(text(g));
            int[][] mirrored = new int[g.length][g[0].length];
            for (int y = 0; y < g.length; y++) {
                for (int x = 0; x < g[0].length; x++) mirrored[y][g[0].length - 1 - x] = g[y][x];
            }
            out.add(text(mirrored));
            int[][] rotated = new int[g[0].length][g.length];
            for (int y = 0; y < g.length; y++) {
                for (int x = 0; x < g[0].length; x++) rotated[x][g.length - 1 - y] = g[y][x];
            }
            g = rotated;
        }
        return out;
    }

    @Test
    @DisplayName("1~2층 맵 10장이 로드된다: 층마다 5장, 진영·테마·시드·크기가 있고 3층부터는 아직 없다")
    void tenMapsLoad() {
        assertThat(TowerMapCatalog.class.getResource(TowerMapCatalog.RESOURCE)).isNotNull();
        assertThat(TowerMapCatalog.floorsWithMaps()).containsExactly(1, 2);
        assertThat(all()).hasSize(10);
        Set<Long> seeds = new HashSet<>();
        for (int floor = 1; floor <= 2; floor++) {
            List<TowerMap> maps = TowerMapCatalog.floorMaps(floor);
            assertThat(maps).hasSize(TowerFloorCatalog.mapsPerFloor(floor)).hasSize(5);
            for (int i = 0; i < maps.size(); i++) {
                TowerMap m = maps.get(i);
                assertThat(TowerMapCatalog.map(floor, i + 1)).containsSame(m);
                assertThat(m.floor()).isEqualTo(floor);
                assertThat(m.map()).isEqualTo(i + 1);
                assertThat(m.faction()).isEqualTo(floor == 1 ? "nature-spirits" : "deep-savers");
                assertThat(m.theme().id()).isEqualTo(floor == 1 ? "spirit-forest" : "deep-sea");
                assertThat(m.theme().tileTypes()).hasSize(Tile.values().length);
                assertThat(m.tileSize()).isPositive();
                assertThat(m.width()).isGreaterThanOrEqualTo(TowerMapAssembler.MIN_WIDTH);
                assertThat(m.height()).isGreaterThanOrEqualTo(TowerMapAssembler.MIN_HEIGHT);
                assertThat(m.tiles()).hasSize(m.height()).allSatisfy(row -> assertThat(row).hasSize(m.width()));
                assertThat(m.collision()).hasSize(m.height()).allSatisfy(row -> assertThat(row).hasSize(m.width()));
                assertThat(seeds.add(m.seed())).as("seed " + name(m)).isTrue();
            }
            assertThat(TowerMapCatalog.map(floor, 0)).isEmpty();
            assertThat(TowerMapCatalog.map(floor, 6)).isEmpty();
        }
        for (int floor : new int[]{0, 3, 17, 18, 51, 52}) {
            assertThat(TowerMapCatalog.floorMaps(floor)).as("floor " + floor).isEmpty();
            assertThat(TowerMapCatalog.map(floor, 1)).as("floor " + floor).isEmpty();
        }
    }

    @Test
    @DisplayName("스폰 수가 floors.json의 그 맵 수와 정확히 같다 (9 / 12 / 15 / 12 / 8+보스1, 무리 0)")
    void spawnCountsMatchFloors() {
        for (TowerMap m : all()) {
            TowerFloorCatalog.MapMonsters expected = TowerFloorCatalog.floor(m.floor()).orElseThrow().map(m.map()).orElseThrow();
            assertThat(m.spawns().stream().filter(s -> s.role().equals("normal")).count()).as(name(m)).isEqualTo(expected.normal());
            assertThat(m.spawns().stream().filter(s -> s.role().equals("swarm")).count()).as(name(m)).isEqualTo(expected.swarm());
            assertThat(m.spawns().stream().filter(s -> s.role().equals("boss")).count()).as(name(m)).isEqualTo(expected.boss());
            assertThat(m.spawns()).as(name(m)).hasSize(expected.normal() + expected.swarm() + expected.boss());
        }
        assertThat(all().stream().map(m -> m.spawns().size())).containsExactly(9, 12, 15, 12, 9, 9, 12, 15, 12, 9);
    }

    @Test
    @DisplayName("맵 10장의 배치가 서로 다르다: 칸·충돌이 같은 쌍도, 돌리거나 뒤집어 같아지는 쌍도 없다")
    void layoutsAreAllDifferent() {
        List<TowerMap> maps = all();
        for (int i = 0; i < maps.size(); i++) {
            TowerMap a = maps.get(i);
            Set<String> tilesA = orientations(grid(a.tiles()));
            Set<String> collisionA = orientations(grid(a.collision()));
            for (int j = i + 1; j < maps.size(); j++) {
                TowerMap b = maps.get(j);
                String pair = name(a) + " / " + name(b);
                assertThat(tilesA).as("칸 " + pair).doesNotContain(text(grid(b.tiles())));
                assertThat(collisionA).as("충돌 " + pair).doesNotContain(text(grid(b.collision())));
                // 같은 크기라면 칸의 3분의 1 이상이 달라야 한다 (색만 바꾼 복제가 아님) — 지금은 크기부터 모두 다르다
                assertThat(a.width() + "x" + a.height()).as("크기 " + pair).isNotEqualTo(b.width() + "x" + b.height());
            }
        }
    }

    @Test
    @DisplayName("같은 seed면 같은 맵: 다시 조립해도, 파일을 다시 읽어도 똑같다. seed가 다르면 다른 맵이다")
    void sameSeedSameMap() throws Exception {
        Map<Integer, List<TowerMap>> again = TowerMapCatalog.parse(json(), TowerFloorCatalog.floors());
        for (TowerMap m : all()) {
            assertThat(again.get(m.floor()).get(m.map() - 1)).as(name(m)).isEqualTo(m);

            TowerFloorCatalog.Floor floor = TowerFloorCatalog.floor(m.floor()).orElseThrow();
            TowerFloorCatalog.MapMonsters monsters = floor.map(m.map()).orElseThrow();
            List<String> rescueIds = m.rescues().stream().map(Rescue::npcId).toList();
            TowerMapAssembler.Request request = new TowerMapAssembler.Request(m.floor(), m.map(), m.faction(), m.seed(),
                    m.width(), m.height(), m.tileSize(), monsters.normal(), monsters.swarm(), monsters.boss(),
                    rescueIds, m.theme(), m.provisional());
            assertThat(TowerMapAssembler.assemble(request)).as(name(m)).isEqualTo(m);
            assertThat(TowerMapAssembler.assemble(request)).as(name(m)).isEqualTo(TowerMapAssembler.assemble(request));

            TowerMap other = TowerMapAssembler.assemble(new TowerMapAssembler.Request(m.floor(), m.map(), m.faction(),
                    m.seed() + 1, m.width(), m.height(), m.tileSize(), monsters.normal(), monsters.swarm(), monsters.boss(),
                    rescueIds, m.theme(), m.provisional()));
            assertThat(other.collision()).as(name(m)).isNotEqualTo(m.collision());
        }
    }

    @Test
    @DisplayName("입구·출구·모든 스폰·구출 자리는 서로 다른 걸을 수 있는 칸이고, 입구에서 출구·스폰·구출 자리까지 걸어서 닿는다")
    void spotsAreWalkableAndReachable() {
        for (TowerMap m : all()) {
            boolean[][] walkable = walkable(m);
            boolean[][] reachable = reachable(m);
            List<Point> spots = new ArrayList<>(List.of(m.entry(), m.exit()));
            for (Spawn s : m.spawns()) spots.add(new Point(s.x(), s.y()));
            for (Rescue r : m.rescues()) spots.add(new Point(r.x(), r.y()));
            assertThat(new HashSet<>(spots)).as(name(m)).hasSize(spots.size());
            for (Point p : spots) {
                assertThat(p.x()).as(name(m)).isBetween(0, m.width() - 1);
                assertThat(p.y()).as(name(m)).isBetween(0, m.height() - 1);
                assertThat(walkable[p.y()][p.x()]).as(name(m) + " walkable " + p).isTrue();
                assertThat(reachable[p.y()][p.x()]).as(name(m) + " reachable " + p).isTrue();
            }
            assertThat(m.tiles().get(m.entry().y()).get(m.entry().x())).isEqualTo(Tile.ENTRY.id());
            assertThat(m.tiles().get(m.exit().y()).get(m.exit().x())).isEqualTo(Tile.EXIT.id());
            // 충돌은 칸 종류와 맞고, 테두리는 막혀 있다
            for (int y = 0; y < m.height(); y++) {
                for (int x = 0; x < m.width(); x++) {
                    Tile tile = Tile.values()[m.tiles().get(y).get(x)];
                    assertThat(walkable[y][x]).as(name(m) + " " + x + "," + y).isEqualTo(tile.walkable());
                    if (x == 0 || y == 0 || x == m.width() - 1 || y == m.height() - 1) assertThat(walkable[y][x]).isFalse();
                }
            }
            // 입구에 서자마자 맞지 않는다
            for (Spawn s : m.spawns()) {
                int distance = Math.max(Math.abs(s.x() - m.entry().x()), Math.abs(s.y() - m.entry().y()));
                assertThat(distance).as(name(m)).isGreaterThanOrEqualTo(TowerMapAssembler.SPAWN_ENTRY_DISTANCE);
            }
        }
    }

    @Test
    @DisplayName("보스 경기장은 보스 맵(5번)에만 있다: 안은 전부 걸을 수 있고 보스가 그 안에, 일반 몬스터는 밖에 선다")
    void bossArenaOnlyOnBossMaps() {
        for (TowerMap m : all()) {
            boolean bossMap = TowerFloorCatalog.floor(m.floor()).orElseThrow().map(m.map()).orElseThrow().boss() > 0;
            assertThat(bossMap).as(name(m)).isEqualTo(m.map() == 5);
            if (!bossMap) {
                assertThat(m.bossArena()).as(name(m)).isNull();
                assertThat(m.tiles().stream().flatMap(List::stream)).as(name(m)).doesNotContain(Tile.ARENA.id());
                continue;
            }
            TowerMap.Arena arena = m.bossArena();
            assertThat(arena).as(name(m)).isNotNull();
            boolean[][] walkable = walkable(m);
            for (int y = arena.y(); y < arena.y() + arena.height(); y++) {
                for (int x = arena.x(); x < arena.x() + arena.width(); x++) assertThat(walkable[y][x]).as(name(m)).isTrue();
            }
            assertThat(arena.contains(arena.center().x(), arena.center().y())).isTrue();
            for (Spawn s : m.spawns()) {
                assertThat(arena.contains(s.x(), s.y())).as(name(m) + " " + s).isEqualTo(s.role().equals("boss"));
            }
        }
        // 두 보스 맵의 경기장도 서로 다르다
        assertThat(TowerMapCatalog.map(1, 5).orElseThrow().bossArena())
                .isNotEqualTo(TowerMapCatalog.map(2, 5).orElseThrow().bossArena());
    }

    @Test
    @DisplayName("구출 자리: 1층 보스 맵에 ns-hamster·ns-woodpecker 두 자리, 2층 보스 맵에 ds-sea-otter 한 자리, 다른 맵은 없다")
    void rescueSpots() {
        for (TowerMap m : all()) {
            List<String> ids = m.rescues().stream().map(Rescue::npcId).toList();
            if (m.map() != 5) {
                assertThat(ids).as(name(m)).isEmpty();
                continue;
            }
            assertThat(ids).as(name(m)).isEqualTo(TowerFloorCatalog.floor(m.floor()).orElseThrow().rescueNpcIds());
            for (Rescue r : m.rescues()) {
                assertThat(m.tiles().get(r.y()).get(r.x())).as(name(m)).isEqualTo(Tile.RESCUE.id());
                assertThat(r.x()).as("구출은 보스 뒤 " + name(m)).isGreaterThan(m.bossArena().x() + m.bossArena().width() - 1);
            }
        }
        assertThat(TowerMapCatalog.map(1, 5).orElseThrow().rescues()).extracting(Rescue::npcId)
                .containsExactly("ns-hamster", "ns-woodpecker");
        assertThat(TowerMapCatalog.map(2, 5).orElseThrow().rescues()).extracting(Rescue::npcId)
                .containsExactly("ds-sea-otter");
    }

    @Test
    @DisplayName("검증: 깨진 JSON, 모르는 필드(전투력 숫자), 맵 수 불일치, 같은 seed, 몬스터 수가 없는 층, 진영이 다른 테마, 너무 작은 맵은 실패한다")
    void validation() throws Exception {
        String valid = json();
        TowerFloorCatalog.Floors floors = TowerFloorCatalog.floors();
        assertThat(TowerMapCatalog.parse(valid, floors)).hasSize(2);

        String[] bad = {
                valid.substring(0, valid.length() - 3),
                valid + " trailing",
                "[]",
                "{}",
                valid.replace("\"seed\": 110101", "\"seed\": 110101, \"recommendedBp\": 100"),
                valid.replace("\"seed\": 110102", "\"seed\": 110101"),
                valid.replace("\"seed\": 110101,", ""),
                valid.replace("\"seed\": 110101", "\"seed\": -1"),
                valid.replace("{ \"map\": 4, \"seed\": 110104, \"width\": 42, \"height\": 30 },", ""),
                valid.replace("\"map\": 4, \"seed\": 110104", "\"map\": 3, \"seed\": 110104"),
                valid.replace("\"floor\": 2, \"theme\": \"deep-sea\"", "\"floor\": 3, \"theme\": \"deep-sea\""),
                valid.replace("\"floor\": 2, \"theme\": \"deep-sea\"", "\"floor\": 1, \"theme\": \"deep-sea\""),
                valid.replace("\"floor\": 2, \"theme\": \"deep-sea\"", "\"floor\": 2, \"theme\": \"spirit-forest\""),
                valid.replace("\"floor\": 2, \"theme\": \"deep-sea\"", "\"floor\": 2, \"theme\": \"lava\""),
                valid.replace("\"width\": 40, \"height\": 24", "\"width\": 20, \"height\": 24"),
                valid.replace("\"tileSize\": 32", "\"tileSize\": 0"),
                valid.replace("\"color\": \"#7fb069\"", "\"color\": \"green\""),
                valid.replace("\"hazard\": { \"name\": \"연못\", \"color\": \"#4f8a8b\" },", "")
        };
        for (String json : bad) {
            assertThat(json).isNotEqualTo(valid); // 바꿔치기가 실제로 먹혔다
            assertThatThrownBy(() -> TowerMapCatalog.parse(json, floors)).as(json.length() > 300 ? "…" : json)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
