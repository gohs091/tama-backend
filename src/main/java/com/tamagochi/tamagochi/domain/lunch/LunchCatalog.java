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
 * 도시락 데이터 (resources/lunch/lunchboxes.json). 도시락은 한 종류뿐이고 개수만 쌓인다.
 * 열매로 만들고, 탑 원정 중에 먹으면 포만감이 정해진 만큼 찬다. 가방 용량(기본·상한)도 같은 파일에 있다.
 * 수치는 모두 고정값이다 (무작위 없음). 밸런스를 바꿀 때는 JSON만 고친다.
 *
 * legacySatiety는 예전에 세 종류(riceball·hearty·feast)였을 때의 포만감이다. 새로 만들 수는 없고,
 * 예전 앱이 보내는 id를 받아 주는 것과 이미 저장된 예전 도시락을 지금 도시락 개수로 바꾸는 데만 쓴다.
 */
public final class LunchCatalog {

    private LunchCatalog() {}

    /** 포만감은 0~100이라 도시락 하나가 100을 넘게 채울 수 없다 */
    public static final int MAX_SATIETY = 100;

    /** 응답 모양을 지키려고 남긴 자리 (LunchView.unlock). 지금은 해금 조건이 있는 도시락이 없어 늘 null이다 */
    public record Unlock(String type, String id) {}

    public record Lunchbox(String id, String emoji, String name, int fruitCost, int satiety) {}

    private record Data(Lunchbox lunchbox, int defaultBagSlots, int maxBagSlots, Map<String, Integer> legacySatiety) {}

    private static final Data DATA = load();

    /** 하나뿐인 도시락 */
    public static Lunchbox single() {
        return DATA.lunchbox();
    }

    /** 하나뿐인 도시락의 id (찬장·가방 맵의 키) */
    public static String id() {
        return DATA.lunchbox().id();
    }

    public static List<Lunchbox> all() {
        return List.of(DATA.lunchbox());
    }

    /** 가방 기본 용량 (도시락 개수) */
    public static int defaultBagSlots() {
        return DATA.defaultBagSlots();
    }

    /** 보너스를 더해도 넘지 않는 가방 용량 상한 */
    public static int maxBagSlots() {
        return DATA.maxBagSlots();
    }

    /** 예전 도시락 id인가 (riceball·hearty·feast) */
    public static boolean isLegacy(String id) {
        return id != null && DATA.legacySatiety().containsKey(id);
    }

    /**
     * 요청의 도시락 id → 하나뿐인 도시락. id가 비었거나 예전 id여도 그 도시락으로 본다 (예전 앱이 터지지 않게).
     * 전혀 모르는 id만 비어 있다
     */
    public static Optional<Lunchbox> resolve(String id) {
        if (id == null || id.isBlank()) return Optional.of(single());
        String key = id.trim();
        return key.equals(id()) || isLegacy(key) ? Optional.of(single()) : Optional.empty();
    }

    /**
     * 저장돼 있던 도시락 하나가 지금 도시락 몇 개인가. 포만감을 잃지 않게 올림한다: ceil(예전 포만감 / 지금 포만감).
     * 지금 도시락과 모르는 id는 1개로 센다 (사라지지 않게)
     */
    public static int unitsPer(String storedId) {
        Integer old = storedId == null ? null : DATA.legacySatiety().get(storedId);
        if (old == null) return 1;
        int now = single().satiety();
        return (old + now - 1) / now;
    }

    private static Data load() {
        try (InputStream in = LunchCatalog.class.getResourceAsStream("/lunch/lunchboxes.json")) {
            if (in == null) throw new IllegalStateException("lunch/lunchboxes.json 이 없어요");
            JsonNode root = new ObjectMapper().readTree(in);

            JsonNode n = required(root, "lunchbox");
            Lunchbox box = new Lunchbox(required(n, "id").asText(), required(n, "emoji").asText(), required(n, "name").asText(),
                    required(n, "fruitCost").asInt(), required(n, "satiety").asInt());
            if (box.id().isBlank() || box.name().isBlank()) throw new IllegalStateException("이름 없는 도시락이 있어요");
            if (box.fruitCost() <= 0) throw new IllegalStateException("도시락 열매 값은 1 이상: " + box.id());
            if (box.satiety() <= 0 || box.satiety() > MAX_SATIETY) throw new IllegalStateException("도시락 포만감은 1~100: " + box.id());

            JsonNode bag = required(root, "bag");
            int defaultSlots = required(bag, "defaultSlots").asInt();
            int maxSlots = required(bag, "maxSlots").asInt();
            if (defaultSlots < 1 || maxSlots < defaultSlots) {
                throw new IllegalStateException("가방 용량은 1 이상이고 상한은 기본보다 작을 수 없어요");
            }

            Map<String, Integer> legacy = new LinkedHashMap<>();
            JsonNode l = root.get("legacySatiety");
            if (l != null && !l.isNull()) {
                for (Map.Entry<String, JsonNode> e : l.properties()) {
                    int satiety = e.getValue().asInt();
                    if (e.getKey().isBlank() || e.getKey().equals(box.id())) {
                        throw new IllegalStateException("예전 도시락 id가 잘못됐어요: " + e.getKey());
                    }
                    if (satiety <= 0) throw new IllegalStateException("예전 도시락 포만감은 1 이상: " + e.getKey());
                    legacy.put(e.getKey(), satiety);
                }
            }
            return new Data(box, defaultSlots, maxSlots, Collections.unmodifiableMap(legacy));
        } catch (IOException e) {
            throw new UncheckedIOException("lunch/lunchboxes.json 을 읽지 못했어요", e);
        }
    }

    private static JsonNode required(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) throw new IllegalStateException("lunch/lunchboxes.json 에 " + field + " 가 없어요");
        return n;
    }
}
