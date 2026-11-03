package com.tamagochi.tamagochi.domain.spar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 스파링 상대 목록 (resources/spar/enemies.json, 412명). 상대마다 나오는 레벨 범위와 떨어뜨리는 잉크(최대 5칸)가 정해져 있다.
 * 그림은 CDN의 public/spar/{id}.v{IMAGE_VERSION}.png
 */
public final class SparRoster {

    /** 그림을 고쳐 다시 올리면 올린다 (CDN 캐시 때문에 주소를 바꾼다) */
    public static final int IMAGE_VERSION = 1;

    public enum Power { WEAK, NORMAL, STRONG, BOSS }

    public record Enemy(String id, String name, String faction, Power power, String axis,
                        int minLevel, int maxLevel, List<String> drops) {
        public boolean boss() {
            return power == Power.BOSS;
        }

        public String imageKey() {
            return "public/spar/" + id + ".v" + IMAGE_VERSION + ".png";
        }
    }

    private static final Map<String, Enemy> ENEMIES = load();

    private SparRoster() {
    }

    public static Optional<Enemy> find(String id) {
        return Optional.ofNullable(id == null ? null : ENEMIES.get(id));
    }

    public static List<Enemy> all() {
        return List.copyOf(ENEMIES.values());
    }

    /**
     * 이 레벨에 나오는 상대 중 하나를 고른다. 범위에 드는 상대가 없으면(최고 레벨보다 높을 때 등) 범위가 가장 가까운 상대들 중에서 고른다.
     */
    public static Enemy pick(int level, RandomGenerator random) {
        List<Enemy> best = new ArrayList<>();
        int bestGap = Integer.MAX_VALUE;
        for (Enemy e : ENEMIES.values()) {
            int gap = level < e.minLevel() ? e.minLevel() - level : Math.max(0, level - e.maxLevel());
            if (gap < bestGap) {
                bestGap = gap;
                best.clear();
            }
            if (gap == bestGap) best.add(e);
        }
        return best.get(random.nextInt(best.size()));
    }

    private static Map<String, Enemy> load() {
        try (InputStream in = SparRoster.class.getResourceAsStream("/spar/enemies.json")) {
            if (in == null) throw new IllegalStateException("spar/enemies.json 이 없어요");
            Map<String, Enemy> map = new LinkedHashMap<>();
            for (JsonNode n : new ObjectMapper().readTree(in)) {
                List<String> drops = new ArrayList<>();
                n.get("drops").forEach(d -> drops.add(d.asText()));
                Enemy e = new Enemy(n.get("id").asText(), n.get("name").asText(), n.get("faction").asText(),
                        Power.valueOf(n.get("power").asText().toUpperCase()), n.get("axis").asText(),
                        n.get("minLevel").asInt(), n.get("maxLevel").asInt(), List.copyOf(drops));
                if (map.put(e.id(), e) != null) throw new IllegalStateException("중복 상대: " + e.id());
            }
            return Collections.unmodifiableMap(map);
        } catch (IOException e) {
            throw new UncheckedIOException("spar/enemies.json 을 읽지 못했어요", e);
        }
    }
}
