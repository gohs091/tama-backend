package com.tamagochi.tamagochi.domain.lunch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 도시락 목록 (resources/lunch/lunchboxes.json). 열매로 만들고, 탑 원정 중에 먹으면 포만감이 정해진 만큼 찬다.
 * 수치는 모두 고정값이다 (무작위 없음). 밸런스를 바꿀 때는 JSON만 고친다.
 */
public final class LunchCatalog {

    private LunchCatalog() {}

    /** 포만감은 0~100이라 도시락 하나가 100을 넘게 채울 수 없다 */
    public static final int MAX_SATIETY = 100;

    /** 해금 조건 (예: NPC hamster를 구하면 열린다). 해금 수단이 생기기 전까지 조건이 있는 도시락은 잠겨 있다 */
    public record Unlock(String type, String id) {}

    /** unlock이 null이면 처음부터 만들 수 있다 */
    public record Lunchbox(String id, String emoji, String name, int fruitCost, int satiety, Unlock unlock) {}

    private static final Map<String, Lunchbox> LUNCHBOXES = load();

    public static List<Lunchbox> all() {
        return List.copyOf(LUNCHBOXES.values());
    }

    public static Optional<Lunchbox> find(String id) {
        return Optional.ofNullable(id == null ? null : LUNCHBOXES.get(id));
    }

    private static Map<String, Lunchbox> load() {
        try (InputStream in = LunchCatalog.class.getResourceAsStream("/lunch/lunchboxes.json")) {
            if (in == null) throw new IllegalStateException("lunch/lunchboxes.json 이 없어요");
            Map<String, Lunchbox> map = new LinkedHashMap<>();
            for (JsonNode n : new ObjectMapper().readTree(in)) {
                JsonNode u = n.get("unlock");
                Unlock unlock = u == null || u.isNull() ? null : new Unlock(u.get("type").asText(), u.get("id").asText());
                Lunchbox box = new Lunchbox(n.get("id").asText(), n.get("emoji").asText(), n.get("name").asText(),
                        n.get("fruitCost").asInt(), n.get("satiety").asInt(), unlock);
                if (box.id().isBlank() || box.name().isBlank()) throw new IllegalStateException("이름 없는 도시락이 있어요");
                if (box.fruitCost() <= 0) throw new IllegalStateException("도시락 열매 값은 1 이상: " + box.id());
                if (box.satiety() <= 0 || box.satiety() > MAX_SATIETY) throw new IllegalStateException("도시락 포만감은 1~100: " + box.id());
                if (map.put(box.id(), box) != null) throw new IllegalStateException("중복 도시락: " + box.id());
            }
            return Collections.unmodifiableMap(map);
        } catch (IOException e) {
            throw new UncheckedIOException("lunch/lunchboxes.json 을 읽지 못했어요", e);
        }
    }
}
