package com.tamagochi.tamagochi.domain.tower;

import com.tamagochi.tamagochi.domain.tower.TowerMap.Arena;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Point;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Rescue;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Spawn;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Theme;
import com.tamagochi.tamagochi.domain.tower.TowerMap.Tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 시드로 맵 한 장을 조립한다 (M-1). 결정적이다: 같은 요청(시드·크기·몬스터 수)이면 언제나 같은 맵이 나온다
 * (자체 난수기 SplitMix64만 쓰고, 시각·해시 순서 같은 바깥 값은 쓰지 않는다).
 * 그림 한 장을 돌리거나 뒤집어 붙이지 않는다 — 칸마다 따로 판다:
 * 1) 왼쪽에서 오른쪽으로 띠를 나눠 빈터를 하나씩 놓고 (모양은 울퉁불퉁한 타원), 2) 이웃한 빈터를 구불구불한 길로 잇고,
 * 3) 곁방을 몇 개 달고, 4) 입구에서 닿지 않는 칸은 메우고, 5) 길을 끊지 않는 자리에만 장애물을 놓고, 6) 스폰을 흩어 놓는다.
 * 보스 맵은 오른쪽에 네모난 경기장과 그 뒤의 뒷방(구출 자리·출구)을 먼저 놓고, 왼쪽에 경기장까지 가는 길을 판다.
 * 일반 몬스터는 경기장 밖 길목에, 보스는 경기장 가운데에 선다.
 */
public final class TowerMapAssembler {

    private TowerMapAssembler() {}

    public static final int MIN_WIDTH = 36;
    public static final int MIN_HEIGHT = 18;

    /** 스폰은 입구에서 이만큼(칸, 가로·세로 중 큰 쪽) 떨어진다 */
    static final int SPAWN_ENTRY_DISTANCE = 5;

    /** 조립 요청. normal·swarm·boss는 floors.json의 그 맵 몬스터 수, rescueNpcIds는 이 맵에 자리를 둘 NPC (없으면 빈 목록) */
    public record Request(int floor, int map, String faction, long seed, int width, int height, int tileSize,
                          int normal, int swarm, int boss, List<String> rescueNpcIds, Theme theme, boolean provisional) {}

    /** SplitMix64. 자바 버전이 바뀌어도 같은 수열이 나오게 직접 적는다 */
    private static final class Rng {
        private long state;

        Rng(long seed) {
            this.state = seed;
        }

        long next() {
            state += 0x9E3779B97F4A7C15L;
            long z = state;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        /** lo ~ hi (둘 다 포함). hi가 lo보다 작으면 lo */
        int range(int lo, int hi) {
            if (hi <= lo) return lo;
            return lo + (int) Math.floorMod(next(), (long) (hi - lo + 1));
        }

        /** 0 이상 1 미만 */
        double unit() {
            return (next() >>> 11) * 0x1.0p-53;
        }
    }

    public static TowerMap assemble(Request r) {
        int w = r.width();
        int h = r.height();
        if (w < MIN_WIDTH || h < MIN_HEIGHT) {
            throw new IllegalStateException("맵이 너무 작아요 (최소 " + MIN_WIDTH + "x" + MIN_HEIGHT + "): " + w + "x" + h);
        }
        Rng rng = new Rng(r.seed());
        boolean[][] open = new boolean[h][w];
        boolean bossMap = r.boss() > 0;
        if (!bossMap && !r.rescueNpcIds().isEmpty()) {
            throw new IllegalStateException("구출 자리는 보스 맵에만 둬요: " + r.floor() + "층 맵 " + r.map());
        }

        // 보스 맵: 경기장과 뒷방을 먼저 놓는다. 길·빈터는 그 왼쪽(maxX까지)에만 판다
        Arena arena = null;
        int backX = 0, backY = 0, backH = 0;
        int maxX = w - 2;
        if (bossMap) {
            int aw = rng.range(10, 13);
            int ah = rng.range(8, Math.min(11, h - 4));
            int ax = w - aw - 6;
            int ay = rng.range(2, h - 2 - ah);
            arena = new Arena(ax, ay, aw, ah, new Point(ax + aw / 2, ay + ah / 2));
            fill(open, ax, ay, aw, ah);
            backH = rng.range(5, Math.min(7, ah));
            backY = ay + rng.range(0, ah - backH);
            backX = ax + aw + 1;
            fill(open, backX, backY, w - 1 - backX, backH);
            int doorY = backY + rng.range(1, backH - 3);
            open[doorY][ax + aw] = true;       // 경기장과 뒷방 사이의 문 (두 칸)
            open[doorY + 1][ax + aw] = true;
            maxX = ax - 2;
        }

        // 빈터: 왼쪽부터 띠마다 하나씩
        int stations = bossMap ? rng.range(3, 4) : rng.range(4, 6);
        int minX = 2;
        int span = maxX - minX + 1;
        Point[] station = new Point[stations];
        for (int i = 0; i < stations; i++) {
            int lo = minX + span * i / stations;
            int hi = minX + span * (i + 1) / stations - 1;
            station[i] = new Point(rng.range(lo, hi), rng.range(3, h - 4));
            blob(open, rng, station[i], rng.range(2, 4), rng.range(2, 3), maxX);
        }
        for (int i = 0; i + 1 < stations; i++) corridor(open, rng, station[i], station[i + 1]);
        if (bossMap) corridor(open, rng, station[stations - 1], new Point(arena.x(), arena.center().y()));

        // 곁방: 가장 가까운 빈터에 잇는다
        int sideRooms = rng.range(1, 3);
        for (int i = 0; i < sideRooms; i++) {
            Point room = new Point(rng.range(minX + 1, maxX - 1), rng.range(3, h - 4));
            blob(open, rng, room, rng.range(2, 3), rng.range(2, 3), maxX);
            Point nearest = station[0];
            for (Point s : station) if (manhattan(s, room) < manhattan(nearest, room)) nearest = s;
            corridor(open, rng, room, nearest);
        }

        Point entry = station[0];
        Point exit = bossMap ? new Point(w - 2, backY + backH / 2) : station[stations - 1];

        // 입구에서 닿지 않는 칸은 메운다 (울퉁불퉁한 가장자리에서 떨어져 나온 칸)
        boolean[][] reached = reach(open, entry);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) open[y][x] = reached[y][x];

        // 구출 자리: 뒷방 안, 한 줄 걸러 하나씩
        List<Rescue> rescues = new ArrayList<>();
        for (int i = 0; i < r.rescueNpcIds().size(); i++) {
            int y = backY + 1 + 2 * i;
            if (y > backY + backH - 1) throw new IllegalStateException("뒷방에 구출 자리가 모자라요: " + r.floor() + "층 맵 " + r.map());
            rescues.add(new Rescue(backX + 1, y, r.rescueNpcIds().get(i)));
        }

        // 장애물: 둘레 여덟 칸이 모두 열린 칸에만 놓는다 → 길이 끊기지 않는다. 포탈 곁과 경기장·뒷방에는 놓지 않는다
        boolean[][] obstacle = new boolean[h][w];
        for (int i = 0, attempts = w * h / 20; i < attempts; i++) {
            int x = rng.range(2, maxX - 1);
            int y = rng.range(2, h - 3);
            if (!open[y][x] || !ringOpen(open, x, y)) continue;
            if (chebyshev(x, y, entry) <= 2 || chebyshev(x, y, exit) <= 2) continue;
            open[y][x] = false;
            obstacle[y][x] = true;
        }

        // 못 들어가는 곳: 벽이던 칸만 바꾼다 (치장일 뿐, 충돌은 그대로)
        boolean[][] hazard = new boolean[h][w];
        int pools = rng.range(1, 3);
        for (int i = 0; i < pools; i++) {
            int cx = rng.range(2, w - 3);
            int cy = rng.range(2, h - 3);
            int radius = rng.range(1, 2);
            for (int y = Math.max(1, cy - radius); y <= Math.min(h - 2, cy + radius); y++) {
                for (int x = Math.max(1, cx - radius); x <= Math.min(w - 2, cx + radius); x++) {
                    if (!open[y][x] && !obstacle[y][x] && Math.abs(x - cx) + Math.abs(y - cy) <= radius + 1) hazard[y][x] = true;
                }
            }
        }

        List<Spawn> spawns = spawns(r, rng, open, entry, exit, arena);

        // 칸 종류
        Tile[][] tile = new Tile[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (open[y][x]) tile[y][x] = rng.range(0, 6) == 0 ? Tile.FLOOR_ALT : Tile.FLOOR;
                else tile[y][x] = obstacle[y][x] ? Tile.OBSTACLE : hazard[y][x] ? Tile.HAZARD : Tile.WALL;
            }
        }
        if (arena != null) {
            for (int y = arena.y(); y < arena.y() + arena.height(); y++) {
                for (int x = arena.x(); x < arena.x() + arena.width(); x++) tile[y][x] = Tile.ARENA;
            }
        }
        for (Rescue rescue : rescues) tile[rescue.y()][rescue.x()] = Tile.RESCUE;
        tile[entry.y()][entry.x()] = Tile.ENTRY;
        tile[exit.y()][exit.x()] = Tile.EXIT;

        List<List<Integer>> tiles = new ArrayList<>();
        List<List<Integer>> collision = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            List<Integer> tileRow = new ArrayList<>();
            List<Integer> collisionRow = new ArrayList<>();
            for (int x = 0; x < w; x++) {
                tileRow.add(tile[y][x].id());
                collisionRow.add(tile[y][x].walkable() ? 0 : 1);
            }
            tiles.add(List.copyOf(tileRow));
            collision.add(List.copyOf(collisionRow));
        }
        return new TowerMap(r.floor(), r.map(), r.faction(), r.seed(), w, h, r.tileSize(), List.copyOf(tiles),
                List.copyOf(collision), entry, exit, spawns, arena, List.copyOf(rescues), r.theme(), r.provisional());
    }

    /** 스폰 자리. 보스는 경기장 가운데 줄에, 나머지는 경기장 밖에 서로 멀리 흩어 놓는다 */
    private static List<Spawn> spawns(Request r, Rng rng, boolean[][] open, Point entry, Point exit, Arena arena) {
        int h = open.length;
        int w = open[0].length;
        List<Point> candidates = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!open[y][x]) continue;
                if (arena != null && x >= arena.x()) continue;
                if (chebyshev(x, y, entry) < SPAWN_ENTRY_DISTANCE || chebyshev(x, y, exit) < 2) continue;
                candidates.add(new Point(x, y));
            }
        }
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = rng.range(0, i);
            Point swap = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, swap);
        }
        int wanted = r.normal() + r.swarm();
        List<Point> chosen = new ArrayList<>();
        for (int gap = 6; gap >= 1 && chosen.size() < wanted; gap--) {
            chosen.clear();
            for (Point c : candidates) {
                boolean far = true;
                for (Point p : chosen) {
                    if (manhattan(p, c) < gap) {
                        far = false;
                        break;
                    }
                }
                if (far) chosen.add(c);
                if (chosen.size() == wanted) break;
            }
        }
        if (chosen.size() < wanted) {
            throw new IllegalStateException("스폰 자리가 모자라요: " + r.floor() + "층 맵 " + r.map());
        }
        List<Spawn> spawns = new ArrayList<>();
        for (int i = 0; i < wanted; i++) {
            Point p = chosen.get(i);
            spawns.add(new Spawn(p.x(), p.y(), i < r.normal() ? TowerMap.ROLE_NORMAL : TowerMap.ROLE_SWARM));
        }
        for (int i = 0; i < r.boss(); i++) {
            int offset = (i + 1) / 2 * 2 * (i % 2 == 0 ? -1 : 1); // 0, +2, -2, +4, -4
            int x = arena.center().x() + offset;
            if (!arena.contains(x, arena.center().y())) {
                throw new IllegalStateException("경기장에 보스 자리가 모자라요: " + r.floor() + "층 맵 " + r.map());
            }
            spawns.add(new Spawn(x, arena.center().y(), TowerMap.ROLE_BOSS));
        }
        return List.copyOf(spawns);
    }

    private static void fill(boolean[][] open, int x0, int y0, int width, int height) {
        for (int y = y0; y < y0 + height; y++) for (int x = x0; x < x0 + width; x++) open[y][x] = true;
    }

    /** 울퉁불퉁한 타원 빈터. 테두리 한 줄과 maxX 오른쪽은 건드리지 않는다 */
    private static void blob(boolean[][] open, Rng rng, Point c, int rx, int ry, int maxX) {
        int h = open.length;
        for (int y = Math.max(1, c.y() - ry); y <= Math.min(h - 2, c.y() + ry); y++) {
            for (int x = Math.max(1, c.x() - rx); x <= Math.min(maxX, c.x() + rx); x++) {
                double dx = (x - c.x()) / (double) rx;
                double dy = (y - c.y()) / (double) ry;
                if (dx * dx + dy * dy <= 0.75 + rng.unit() * 0.6) open[y][x] = true;
            }
        }
        open[c.y()][c.x()] = true;
    }

    /** from에서 to까지 두 칸 너비로 판다. 가끔 옆으로 한 칸 샌다. x는 두 점 사이를 벗어나지 않는다 */
    private static void corridor(boolean[][] open, Rng rng, Point from, Point to) {
        int h = open.length;
        int w = open[0].length;
        int limitX = Math.max(from.x(), to.x());
        int x = from.x();
        int y = from.y();
        for (int step = 0; step < 4 * (w + h); step++) {
            carve(open, x, y, limitX);
            if (x == to.x() && y == to.y()) return;
            int dx = Integer.signum(to.x() - x);
            int dy = Integer.signum(to.y() - y);
            int roll = rng.range(0, 9);
            if (roll < 2 && dx != 0) {
                y = Math.max(2, Math.min(h - 3, y + (roll == 0 ? -1 : 1))); // 옆으로 샌다
            } else if (dx != 0 && (dy == 0 || roll < 7)) {
                x += dx;
            } else {
                y += dy;
            }
        }
        // 여기까지 오는 일은 없지만, 오면 곧게 잇는다
        while (x != to.x()) {
            x += Integer.signum(to.x() - x);
            carve(open, x, y, limitX);
        }
        while (y != to.y()) {
            y += Integer.signum(to.y() - y);
            carve(open, x, y, limitX);
        }
    }

    private static void carve(boolean[][] open, int x, int y, int limitX) {
        int h = open.length;
        int x2 = x + 1 <= limitX ? x + 1 : x - 1; // 오른쪽 끝에서는 왼쪽으로 넓혀 두 칸 너비를 지킨다
        int y2 = Math.min(h - 2, y + 1);
        open[y][x] = true;
        open[y][x2] = true;
        open[y2][x] = true;
        open[y2][x2] = true;
    }

    private static boolean ringOpen(boolean[][] open, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!open[y + dy][x + dx]) return false;
            }
        }
        return true;
    }

    /** from에서 상하좌우로 걸어서 닿는 칸 */
    private static boolean[][] reach(boolean[][] open, Point from) {
        int h = open.length;
        int w = open[0].length;
        boolean[][] seen = new boolean[h][w];
        ArrayDeque<Point> queue = new ArrayDeque<>();
        seen[from.y()][from.x()] = true;
        queue.add(from);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            for (int[] s : steps) {
                int x = p.x() + s[0];
                int y = p.y() + s[1];
                if (x < 0 || y < 0 || x >= w || y >= h || seen[y][x] || !open[y][x]) continue;
                seen[y][x] = true;
                queue.add(new Point(x, y));
            }
        }
        return seen;
    }

    private static int manhattan(Point a, Point b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }

    private static int chebyshev(int x, int y, Point p) {
        return Math.max(Math.abs(x - p.x()), Math.abs(y - p.y()));
    }
}
