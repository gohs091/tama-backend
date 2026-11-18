package com.tamagochi.tamagochi.domain.gear;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 장비 카탈로그 (resources/tower/gear.json). 지금 범위는 기본 무기 두 종(나무 붓·깃 펜촉)뿐이다.
 * 가격은 열매 정가만 허용한다. 잉크(유료로도 얻는 재화)나 다른 유료 가격이 있으면 로딩을 실패시킨다.
 * 전투력 숫자는 실리지 않는다. 수치는 임시이며, 밸런스를 바꿀 때는 JSON만 고친다.
 */
public final class GearCatalog {

    private GearCatalog() {}

    static final String RESOURCE = "/tower/gear.json";

    public enum Slot { WEAPON }
    public enum Style { MELEE, RANGED }
    public enum Tier { T1 }

    /** 정가. fruit만 허용한다 (잉크·조각은 이 카탈로그에 올 수 없다) */
    public record Price(int fruit) {}

    /**
     * 기본 무기. meleeDamage·rangedDamage는 자신의 스타일 쪽만 채울 수 있고, 지금은 둘 다 비워 둔다 (추가 피해 미정).
     * 비운 쪽은 장착해도 그 공격 종류를 바꾸지 않는다 (맨손 = 시안 자이로 25·11 유지).
     * unlockNpc는 가게 가드 훅(오쿠보 N-5). 이 카탈로그는 읽기만 하고, 구출 여부는 아직 검사하지 않는다.
     */
    public record Weapon(String id, String emoji, String name, Slot slot, Style style, Tier tier, Price price,
                         int unlockFloor, String unlockNpc, Integer meleeDamage, Integer rangedDamage, String note) {}

    /** 무기를 안 낀 기본 공격 */
    public record Unarmed(int meleeDamage, int rangedDamage, String note) {}

    /** 지금 장착을 반영한 근접·원거리 기본 피해. 전투력 숫자가 아니다 */
    public record Attacks(int meleeDamage, int rangedDamage) {}

    private record Data(boolean provisional, String note, Unarmed unarmed, List<Weapon> weapons, Map<String, Weapon> byId) {}

    private static final Data DATA = load();

    public static boolean provisional() {
        return DATA.provisional();
    }

    public static String note() {
        return DATA.note();
    }

    public static Unarmed unarmed() {
        return DATA.unarmed();
    }

    public static List<Weapon> weapons() {
        return DATA.weapons();
    }

    public static Optional<Weapon> weapon(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(DATA.byId().get(id.trim()));
    }

    /** 장착한 무기 id(없으면 맨손)로 근접·원거리 기본 피해를 계산한다 */
    public static Attacks attacks(String equippedWeaponId) {
        Unarmed bare = DATA.unarmed();
        int melee = bare.meleeDamage();
        int ranged = bare.rangedDamage();
        if (equippedWeaponId == null || equippedWeaponId.isBlank()) return new Attacks(melee, ranged);
        Weapon w = DATA.byId().get(equippedWeaponId.trim());
        if (w == null) return new Attacks(melee, ranged);
        if (w.meleeDamage() != null) melee = w.meleeDamage();
        if (w.rangedDamage() != null) ranged = w.rangedDamage();
        return new Attacks(melee, ranged);
    }

    private static Data load() {
        try (InputStream in = GearCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("tower/gear.json 이 없어요");
            return parse(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("tower/gear.json 을 읽지 못했어요", e);
        }
    }

    /** 파일을 읽는 load와 테스트가 같이 쓴다. 깨진 값·잉크 가격·전투력 필드는 예외를 던진다 */
    static Data parse(JsonNode root) {
        requireObject(root, "tower/gear.json");
        rejectCombatPower(root, "tower/gear.json");
        requireKnownFields(root, Set.of("provisional", "note", "unarmed", "weapons"), "tower/gear.json");

        boolean provisional = bool(root, "provisional", "tower/gear.json");
        String note = text(root, "note", "tower/gear.json");
        Unarmed unarmed = unarmed(required(root, "unarmed", "tower/gear.json"));

        JsonNode list = required(root, "weapons", "tower/gear.json");
        if (!list.isArray() || list.isEmpty()) {
            throw new IllegalStateException("tower/gear.json 의 weapons 는 비어 있지 않은 배열이어야 해요");
        }
        List<Weapon> weapons = new ArrayList<>();
        Map<String, Weapon> byId = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            Weapon w = weapon(list.get(i), "weapons[" + i + "]");
            if (byId.containsKey(w.id())) {
                throw new IllegalStateException("tower/gear.json 에 같은 id가 두 번 있어요: " + w.id());
            }
            weapons.add(w);
            byId.put(w.id(), w);
        }
        if (weaponMissing(byId, "wooden-brush") || weaponMissing(byId, "feather-nib")) {
            throw new IllegalStateException("tower/gear.json 에 기본 무기 wooden-brush·feather-nib 가 없어요");
        }
        return new Data(provisional, note, unarmed, List.copyOf(weapons), Collections.unmodifiableMap(byId));
    }

    private static boolean weaponMissing(Map<String, Weapon> byId, String id) {
        return !byId.containsKey(id);
    }

    private static Unarmed unarmed(JsonNode n) {
        String where = "unarmed";
        requireObject(n, where);
        rejectCombatPower(n, where);
        requireKnownFields(n, Set.of("note", "meleeDamage", "rangedDamage"), where);
        int melee = positiveInt(n, "meleeDamage", where);
        int ranged = positiveInt(n, "rangedDamage", where);
        return new Unarmed(melee, ranged, text(n, "note", where));
    }

    private static Weapon weapon(JsonNode n, String where) {
        requireObject(n, where);
        rejectCombatPower(n, where);
        requireKnownFields(n, Set.of("id", "emoji", "name", "slot", "style", "tier", "price",
                "unlockFloor", "unlockNpc", "meleeDamage", "rangedDamage", "note"), where);

        String id = text(n, "id", where);
        String emoji = text(n, "emoji", where);
        String name = text(n, "name", where);
        Slot slot = slot(n, where);
        Style style = style(n, where);
        Tier tier = tier(n, where);
        Price price = price(required(n, "price", where), where + ".price");
        int unlockFloor = positiveInt(n, "unlockFloor", where);
        String unlockNpc = text(n, "unlockNpc", where);
        Integer melee = optionalPositiveInt(n, "meleeDamage", where);
        Integer ranged = optionalPositiveInt(n, "rangedDamage", where);
        String note = text(n, "note", where);

        if (slot != Slot.WEAPON) {
            throw new IllegalStateException(where + " 는 지금 WEAPON 만 받을 수 있어요: " + slot);
        }
        // 추가 피해는 미정일 수 있다. 채울 때는 자기 스타일 쪽만 (근접 무기에 rangedDamage 금지, 그 반대도 금지)
        if (style == Style.MELEE && ranged != null) {
            throw new IllegalStateException(where + " 근접 무기는 rangedDamage 를 비워야 해요 (원거리는 맨손 유지)");
        }
        if (style == Style.RANGED && melee != null) {
            throw new IllegalStateException(where + " 원거리 무기는 meleeDamage 를 비워야 해요 (근접은 맨손 유지)");
        }
        return new Weapon(id, emoji, name, slot, style, tier, price, unlockFloor, unlockNpc, melee, ranged, note);
    }

    private static Price price(JsonNode n, String where) {
        requireObject(n, where);
        // 잉크·조각 등 열매가 아닌 가격이 있으면 로딩 실패 (유료 재화로 전투력을 팔지 않기 위해)
        for (Iterator<String> it = n.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (!"fruit".equals(field)) {
                throw new IllegalStateException(where + " 에 열매가 아닌 가격이 있어요 (" + field
                        + "). 전투 수치 품목은 열매 정가만 쓸 수 있고, 잉크로는 살 수 없어요");
            }
        }
        int fruit = positiveInt(n, "fruit", where);
        return new Price(fruit);
    }

    private static Slot slot(JsonNode n, String where) {
        String raw = text(n, "slot", where);
        try {
            return Slot.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(where + " 의 slot 을 몰라요: " + raw);
        }
    }

    private static Style style(JsonNode n, String where) {
        String raw = text(n, "style", where);
        try {
            return Style.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(where + " 의 style 을 몰라요: " + raw);
        }
    }

    private static Tier tier(JsonNode n, String where) {
        String raw = text(n, "tier", where);
        try {
            return Tier.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(where + " 의 tier 을 몰라요: " + raw + " (지금은 T1만)");
        }
    }

    /** 전투력 숫자(BP)는 데이터에 실리지 않는다 */
    private static void rejectCombatPower(JsonNode n, String where) {
        for (Iterator<String> it = n.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            String lower = field.toLowerCase();
            if (lower.equals("bp") || lower.equals("recommendedbp") || lower.equals("battlepower")
                    || lower.equals("combatpower") || lower.contains("battle_power") || lower.contains("combat_power")) {
                throw new IllegalStateException(where + " 에 전투력 필드가 있어요: " + field);
            }
        }
    }

    private static JsonNode required(JsonNode parent, String field, String where) {
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) throw new IllegalStateException(where + " 에 " + field + " 가 없어요");
        return n;
    }

    private static void requireObject(JsonNode n, String where) {
        if (n == null || !n.isObject()) throw new IllegalStateException(where + " 는 객체여야 해요");
    }

    private static void requireKnownFields(JsonNode node, Set<String> allowed, String where) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!allowed.contains(name)) {
                throw new IllegalStateException(where + " 에 모르는 필드: " + name);
            }
        }
    }

    private static String text(JsonNode node, String field, String where) {
        JsonNode v = required(node, field, where);
        if (!v.isTextual() || v.asText().isBlank()) {
            throw new IllegalStateException(where + " 의 " + field + " 는 비어 있지 않은 글이어야 해요");
        }
        return v.asText();
    }

    private static boolean bool(JsonNode node, String field, String where) {
        JsonNode v = required(node, field, where);
        if (!v.isBoolean()) throw new IllegalStateException(where + " 의 " + field + " 는 true/false 여야 해요");
        return v.asBoolean();
    }

    private static int positiveInt(JsonNode node, String field, String where) {
        JsonNode v = required(node, field, where);
        if (!v.isInt() && !v.isLong()) {
            throw new IllegalStateException(where + " 의 " + field + " 는 정수여야 해요");
        }
        int n = v.asInt();
        if (n < 1) throw new IllegalStateException(where + " 의 " + field + " 는 1 이상이어야 해요: " + n);
        return n;
    }

    /** 필드가 없거나 null이면 비운다. 있으면 1 이상 */
    private static Integer optionalPositiveInt(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (!v.isInt() && !v.isLong()) {
            throw new IllegalStateException(where + " 의 " + field + " 는 정수여야 해요");
        }
        int n = v.asInt();
        if (n < 1) throw new IllegalStateException(where + " 의 " + field + " 는 1 이상이어야 해요: " + n);
        return n;
    }
}
