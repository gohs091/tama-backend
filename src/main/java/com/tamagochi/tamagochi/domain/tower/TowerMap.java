package com.tamagochi.tamagochi.domain.tower;

import java.util.List;

/**
 * 탑 맵 한 장 (M-1). 전투 화면(H-2)이 그대로 그릴 수 있는 형식이다. 좌표는 모두 칸 좌표 (x: 왼쪽부터 0, y: 위부터 0).
 * tiles[y][x]는 칸 종류 id (Tile.id), collision[y][x]는 1이면 막힌 칸·0이면 걸을 수 있는 칸이다.
 * entry는 들어온 포탈, exit는 나가는 포탈. spawns의 role은 normal | swarm | boss, 개수는 floors.json의 그 맵 수와 같다.
 * bossArena는 보스 맵에만 있고 (나머지는 null), rescues는 구출 자리와 NPC id다 (자리만 — 구출 로직은 없다).
 * theme은 치장 힌트 (칸 이름·자리표시 색)일 뿐이다. 전투력 숫자는 싣지 않는다.
 */
public record TowerMap(int floor, int map, String faction, long seed, int width, int height, int tileSize,
                       List<List<Integer>> tiles, List<List<Integer>> collision, Point entry, Point exit,
                       List<Spawn> spawns, Arena bossArena, List<Rescue> rescues, Theme theme, boolean provisional) {

    public static final String ROLE_NORMAL = "normal";
    public static final String ROLE_SWARM = "swarm";
    public static final String ROLE_BOSS = "boss";

    /** 칸 종류. id는 적힌 순서(0부터)이고 바꾸면 안 된다 — 프론트가 이 숫자로 그린다 */
    public enum Tile {
        /** 바닥 */
        FLOOR(true),
        /** 바닥 (무늬만 다름: 꽃·해초) */
        FLOOR_ALT(true),
        /** 벽 (나무·암초) */
        WALL(false),
        /** 바닥 위 장애물 (바위·산호) */
        OBSTACLE(false),
        /** 못 들어가는 곳 (연못·해구) */
        HAZARD(false),
        /** 들어온 포탈 */
        ENTRY(true),
        /** 나가는 포탈 */
        EXIT(true),
        /** 보스 경기장 바닥 */
        ARENA(true),
        /** 구출 자리 */
        RESCUE(true);

        private final boolean walkable;

        Tile(boolean walkable) {
            this.walkable = walkable;
        }

        public int id() {
            return ordinal();
        }

        public boolean walkable() {
            return walkable;
        }

        /** JSON에 적는 이름 (소문자) */
        public String key() {
            return name().toLowerCase();
        }
    }

    public record Point(int x, int y) {}

    public record Spawn(int x, int y, String role) {}

    public record Rescue(int x, int y, String npcId) {}

    /** 보스 경기장: 왼쪽 위 칸(x, y)과 크기. 안의 칸은 전부 걸을 수 있다 */
    public record Arena(int x, int y, int width, int height, Point center) {

        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    /** 칸 종류 하나의 치장 힌트. color는 그림이 없을 때 칠하는 자리표시 색 */
    public record TileType(int id, String key, String name, boolean walkable, String color) {}

    /** 테마: id, 이름, 배경색, 칸 종류마다의 이름·색 (Tile 순서 그대로) */
    public record Theme(String id, String name, String background, List<TileType> tileTypes) {}
}
