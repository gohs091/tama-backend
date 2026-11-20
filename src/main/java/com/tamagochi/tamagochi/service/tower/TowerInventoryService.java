package com.tamagochi.tamagochi.service.tower;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.tower.TowerInventory;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.tower.TowerInventoryRepository;
import com.tamagochi.tamagochi.service.common.OptimisticRetry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 탑 장비 보관: 가방(최대 BAG_MAX개)과 낀 장비 다섯 칸, 그리고 낙서(가진 낙서와 버튼 A·B의 지정)를 통째로 읽고 통째로 저장한다.
 * 모두 원정이 끝나도 남는다.
 *
 * 아이템은 앱이 전투 중에 만든다 (상대가 떨어뜨린다). 서버는 한 대 한 대의 전투를 검증하지 않으므로(TowerService.clearMap)
 * 떨어진 것 하나하나를 확인할 수 없다. 대신 저장할 때 모양과 범위를 본다:
 * 칸·등급·옵션 열쇠가 아는 것인지, 옵션 값이 그 열쇠의 상한 안인지, 전설 효과가 아는 코드인지, 낀 칸과 아이템의 칸이 맞는지.
 * 범위 밖이면 통째로 거절한다 (INVALID_INVENTORY). 즉 "있을 수 없는 아이템"은 막지만 "안 주운 아이템"은 못 막는다 —
 * 순위·거래처럼 남과 견주는 기능을 붙이기 전에 드롭을 서버가 굴리도록 바꿔야 한다.
 *
 * 옵션 열쇠·상한은 앱의 src/tower/items.ts (AFFIXES·LEGENDS)와 짝이다. 상한은 맨 위 공개 층에서 굴릴 수 있는 값보다 넉넉하다.
 *
 * 낙서는 모양만 본다 (앱의 src/tower/loadout.ts DoodleSave와 짝): DOODLE_MAX개 이하, id 모양, 중복 없음, 지정한 낙서는 가진 것, 색은 아는 것.
 * 낙서 표(어떤 id가 있는지)는 앱에만 있다 — 표에 한 줄 더해도 서버를 다시 배포하지 않게. 앱은 모르는 id를 읽을 때 버린다
 */
@Service
public class TowerInventoryService {

    public static final int BAG_MAX = 24;
    private static final List<String> EQUIP_KEYS = List.of("weapon", "head", "body", "charm1", "charm2");
    private static final Set<String> SLOTS = Set.of("weapon", "head", "body", "charm");
    private static final Set<String> STYLES = Set.of("melee", "ranged");
    /** 전설 효과 코드 (앱의 LEGENDS) */
    private static final Set<String> FX = Set.of("k8", "a0", "boom", "finish", "b0", "k14", "b10", "combo", "b8", "b14", "k10");
    /** 옵션 열쇠 → 한 아이템에서 가질 수 있는 가장 큰 크기 */
    private static final Map<String, Double> STAT_MAX = Map.ofEntries(
            Map.entry("hp", 80.0), Map.entry("def", 60.0), Map.entry("mspd", 40.0), Map.entry("aspd", 70.0), Map.entry("crit", 35.0),
            Map.entry("critm", 150.0), Map.entry("all", 40.0), Map.entry("d_근접", 110.0), Map.entry("d_탄", 110.0), Map.entry("d_스킬", 110.0),
            Map.entry("d_불", 130.0), Map.entry("d_물", 130.0), Map.entry("d_얼음", 130.0), Map.entry("d_독", 130.0), Map.entry("d_지속", 120.0),
            Map.entry("d_소환", 130.0), Map.entry("range", 100.0), Map.entry("mrange", 70.0), Map.entry("kb", 150.0), Map.entry("cdr", 45.0),
            Map.entry("mpsave", 60.0), Map.entry("regen", 3.5), Map.entry("leech", 7.5), Map.entry("evade", 30.0), Map.entry("thorn", 150.0),
            Map.entry("pen_arm", 90.0), Map.entry("dotdur", 125.0), Map.entry("proj", 2.0));
    private static final int STATS_MAX = 8;
    private static final Pattern ID = Pattern.compile("[a-z0-9]{4,32}");
    public static final int DOODLE_MAX = 64;
    private static final Pattern DOODLE_ID = Pattern.compile("[a-z][0-9]{1,2}");
    /** 낙서에 입히는 색 (앱의 src/tower/doodles.ts COLORS) */
    private static final Set<String> COLORS = Set.of("ink", "red", "blue", "green", "yellow", "purple");

    private final TowerInventoryRepository repository;
    private final OptimisticRetry retry;
    private final Clock clock;
    private final ObjectMapper json;

    public TowerInventoryService(TowerInventoryRepository repository, OptimisticRetry retry, Clock clock, ObjectMapper json) {
        this.repository = repository;
        this.retry = retry;
        this.clock = clock;
        this.json = json;
    }

    /** 아이템 하나. style은 무기만(기본 공격 형식), fx·fxText는 전설만 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String id, String slot, Integer rarity, String name, String emoji, String style,
                       Map<String, Double> stats, String fx, String fxText) {}

    /** 버튼 하나의 지정: 낙서 id(null이면 평타)와 입힌 색(null이면 타고난 색) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bind(String id, String color) {}

    /** 낙서: 가진 낙서 id와 버튼 A·B의 지정 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Doodles(List<String> own, Bind a, Bind b) {}

    /** 가방과 낀 장비와 낙서. equip의 열쇠는 weapon·head·body·charm1·charm2 (빈 칸은 null). doodles가 null이면 낙서를 저장한 적이 없다 (옛 저장값) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<Item> bag, Map<String, Item> equip, Doodles doodles) {
        static Data empty() {
            Map<String, Item> equip = new LinkedHashMap<>();
            for (String k : EQUIP_KEYS) equip.put(k, null);
            return new Data(List.of(), equip, null);
        }
    }

    public Data get(User user) {
        return repository.findByUserId(user.getId()).map(inv -> read(inv.getData())).orElseGet(Data::empty);
    }

    /**
     * 가방과 낀 장비와 낙서를 통째로 바꾼다. 범위 밖이면 아무것도 바꾸지 않고 거절한다.
     * 낙서를 보내지 않으면(낙서를 모르는 옛 앱) 저장된 낙서는 그대로 둔다
     */
    public Data save(User user, Data data) {
        Data clean = validated(data);
        return retry.run(() -> {
            TowerInventory inv = repository.findByUserId(user.getId()).orElseGet(() -> repository.save(new TowerInventory(user)));
            Data kept = clean.doodles() != null ? clean : new Data(clean.bag(), clean.equip(), read(inv.getData()).doodles());
            inv.replace(write(kept), LocalDateTime.now(clock));
            return kept;
        });
    }

    private String write(Data data) {
        try {
            return json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new CodedException("INVALID_INVENTORY", "장비를 저장할 수 없어요.");
        }
    }

    private Data read(String text) {
        try {
            Data d = json.readValue(text, Data.class);
            Map<String, Item> equip = new LinkedHashMap<>();
            for (String k : EQUIP_KEYS) equip.put(k, d.equip() == null ? null : d.equip().get(k));
            return new Data(d.bag() == null ? List.of() : d.bag(), equip, d.doodles());
        } catch (JsonProcessingException e) {
            return Data.empty(); // 읽을 수 없는 옛 모양: 빈 가방으로 본다
        }
    }

    private Data validated(Data data) {
        if (data == null) throw invalid("내용이 없어요");
        List<Item> bag = data.bag() == null ? List.of() : data.bag();
        if (bag.size() > BAG_MAX) throw invalid("가방이 너무 커요");
        Set<String> ids = new HashSet<>();
        for (Item it : bag) check(it, null, ids);
        Map<String, Item> equip = new LinkedHashMap<>();
        for (String k : EQUIP_KEYS) {
            Item it = data.equip() == null ? null : data.equip().get(k);
            if (it != null) check(it, k.startsWith("charm") ? "charm" : k, ids);
            equip.put(k, it);
        }
        return new Data(List.copyOf(bag), equip, doodles(data.doodles()));
    }

    /** 낙서를 본다. 안 보냈으면 null (저장할 때 저장된 것을 남긴다). 버튼 지정이 없으면 빈 지정(평타 · 타고난 색)으로 채운다 */
    private Doodles doodles(Doodles d) {
        if (d == null) return null;
        List<String> own = d.own() == null ? List.of() : d.own();
        if (own.size() > DOODLE_MAX) throw invalid("낙서 수");
        Set<String> seen = new HashSet<>();
        for (String id : own) {
            if (id == null || !DOODLE_ID.matcher(id).matches() || !seen.add(id)) throw invalid("낙서 id");
        }
        return new Doodles(List.copyOf(own), bind(d.a(), seen), bind(d.b(), seen));
    }

    private Bind bind(Bind b, Set<String> own) {
        if (b == null) return new Bind(null, null);
        if (b.id() != null && !own.contains(b.id())) throw invalid("지정한 낙서");
        if (b.color() != null && !COLORS.contains(b.color())) throw invalid("색");
        return b;
    }

    /** 아이템 하나를 본다. slot을 주면 그 칸의 것이어야 한다 */
    private void check(Item it, String slot, Set<String> ids) {
        if (it == null) throw invalid("빈 아이템");
        if (it.id() == null || !ID.matcher(it.id()).matches() || !ids.add(it.id())) throw invalid("아이템 id");
        if (it.slot() == null || !SLOTS.contains(it.slot()) || (slot != null && !slot.equals(it.slot()))) throw invalid("칸");
        if (it.rarity() == null || it.rarity() < 0 || it.rarity() > 3) throw invalid("등급");
        if (it.name() == null || it.name().isBlank() || it.name().length() > 30) throw invalid("이름");
        if (it.emoji() == null || it.emoji().length() > 8) throw invalid("그림");
        if (it.style() != null && (!"weapon".equals(it.slot()) || !STYLES.contains(it.style()))) throw invalid("형식");
        if (it.fx() != null && (it.rarity() != 3 || !FX.contains(it.fx()))) throw invalid("효과");
        if (it.fxText() != null && (it.rarity() != 3 || it.fxText().length() > 60)) throw invalid("효과 설명");
        Map<String, Double> stats = it.stats();
        if (stats == null || stats.size() > STATS_MAX) throw invalid("옵션 수");
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            Double max = STAT_MAX.get(e.getKey());
            Double v = e.getValue();
            if (max == null || v == null || v.isNaN() || Math.abs(v) > max) throw invalid("옵션 " + e.getKey());
            if ("proj".equals(e.getKey()) && it.rarity() != 3) throw invalid("옵션 proj"); // 탄 수는 전설에만 붙는다
        }
    }

    private static CodedException invalid(String what) {
        return new CodedException("INVALID_INVENTORY", "장비를 저장할 수 없어요. (" + what + ")");
    }
}
