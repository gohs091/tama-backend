package com.tamagochi.tamagochi.domain.tower;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.tower.TowerMonsterCatalog.FloorMonsters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 전투 화면(H-2)이 읽는 전투 데이터 묶음: 끄적이 기본 수치(resources/tower/player.json)와
 * 층별 몬스터(monsters.json)·스킬(skills.json)·보조(supports.json).
 * 전부 임시 값이고, 사람마다 다르지 않다. 서버는 이 수치로 한 대 한 대를 검증하지 않는다 (퍼히트 검증은 범위 밖).
 * 전투력 숫자는 데이터에도 응답에도 싣지 않는다. 읽으면서 검증하고, 깨진 값은 넘기지 않고 실패한다.
 */
public final class TowerCombatCatalog {

    private TowerCombatCatalog() {}

    static final String PLAYER_RESOURCE = "/tower/player.json";
    static final String SKILLS_RESOURCE = "/tower/skills.json";
    static final String SUPPORTS_RESOURCE = "/tower/supports.json";

    /** 가까운 공격. cooldownSeconds는 다시 쓰기까지, arcRadians는 앞쪽 좌우 각 */
    public record Melee(int damage, double cooldownSeconds, int rangePx, double arcRadians, int mpCost, int knockback) {}

    /** 쏘기(직선탄). rangePx = projectileSpeed × projectileLifeSeconds */
    public record Ranged(int damage, double cooldownSeconds, int rangePx, int projectileSpeed,
                         double projectileLifeSeconds, int mpCost, int knockback) {}

    public record Critical(double chance, double damageMultiplier, double knockbackMultiplier) {}

    public record Block(double seconds, double extraCooldownSeconds, double damageTakenMultiplier) {}

    public record Movement(int speedPxPerSecond, double attackingSpeedMultiplier) {}

    public record Regen(int hpPerSecond, int mpPerSecond, double mpDelaySeconds) {}

    /** 포만감이 satietyBelow 미만이면 배고픔: 주는 피해·이동 속도에 곱한다 */
    public record Hunger(int satietyBelow, double damageMultiplier, double speedMultiplier) {

        public boolean hungry(int satiety) {
            return satiety < satietyBelow;
        }
    }

    /** 쓰러졌을 때: 그 맵 입구에서 HP·MP 가득으로 다시 시작, 잃는 것 없음 */
    public record Defeat(String restartAt, boolean restoreHp, boolean restoreMp, boolean losesAnything) {}

    /** 끄적이 기본 전투 수치 (임시). hp·mp는 화면 표시용 기본값, MP 소모·회복은 0~mpScaleMax 눈금 */
    public record Player(boolean provisional, String source, String note, int hp, int mp, int mpScaleMax, String mpNote,
                         Melee melee, Ranged ranged, Critical critical, Block block, Movement movement, Regen regen,
                         Hunger hunger, Defeat defeat, String damageFormula) {

        /**
         * 끄적이가 주는 피해 (계산 유틸). 기본 피해 × (배고프면 hunger.damageMultiplier) × 방어·저항 배수 × (치명타면 배수).
         * mitigation은 상대의 방어·저항 배수(0~1, 막는 것이 없으면 1). 서버 검증에는 쓰지 않는다
         */
        public double outgoingDamage(double baseDamage, int satiety, double mitigation, boolean criticalHit) {
            double damage = baseDamage * mitigation;
            if (hunger.hungry(satiety)) damage *= hunger.damageMultiplier();
            if (criticalHit) damage *= critical.damageMultiplier();
            return damage;
        }

        /** 끄적이가 받는 피해 (계산 유틸): 몬스터 한 방 × (막는 중이면 block.damageTakenMultiplier) */
        public double incomingDamage(int monsterAttack, boolean blocking) {
            return blocking ? monsterAttack * block.damageTakenMultiplier() : monsterAttack;
        }
    }

    /**
     * GET /api/tower/combat 응답. monsters는 층 번호 순서. skills·supports는 각 JSON 파일의 내용 그대로다
     * (supports는 없어도 앱이 돌아가야 하는 선택 항목)
     */
    public record Combat(boolean provisional, Player player, List<FloorMonsters> monsters,
                         Map<String, Object> skills, Map<String, Object> supports) {}

    private static final Set<String> PLAYER_FIELDS = Set.of("provisional", "note", "source", "hp", "mp", "mpScaleMax", "mpNote",
            "melee", "ranged", "critical", "block", "movement", "regen", "hunger", "defeat", "damageFormula");
    private static final Set<String> MELEE_FIELDS = Set.of("note", "damage", "cooldownSeconds", "rangePx", "arcRadians", "mpCost", "knockback");
    private static final Set<String> RANGED_FIELDS = Set.of("note", "damage", "cooldownSeconds", "rangePx", "projectileSpeed",
            "projectileLifeSeconds", "mpCost", "knockback");
    private static final Set<String> CRITICAL_FIELDS = Set.of("note", "chance", "damageMultiplier", "knockbackMultiplier");
    private static final Set<String> BLOCK_FIELDS = Set.of("note", "seconds", "extraCooldownSeconds", "damageTakenMultiplier");
    private static final Set<String> MOVEMENT_FIELDS = Set.of("note", "speedPxPerSecond", "attackingSpeedMultiplier");
    private static final Set<String> REGEN_FIELDS = Set.of("note", "hpPerSecond", "mpPerSecond", "mpDelaySeconds");
    private static final Set<String> HUNGER_FIELDS = Set.of("note", "satietyBelow", "damageMultiplier", "speedMultiplier");
    private static final Set<String> DEFEAT_FIELDS = Set.of("note", "restartAt", "restoreHp", "restoreMp", "losesAnything");

    private static final Combat COMBAT = load();

    public static Combat combat() {
        return COMBAT;
    }

    public static Player player() {
        return COMBAT.player();
    }

    private static Combat load() {
        Player player = parsePlayer(readText(PLAYER_RESOURCE));
        List<FloorMonsters> monsters = TowerMonsterCatalog.monsters().byFloor().values().stream()
                .sorted(Comparator.comparingInt(FloorMonsters::floor)).toList();
        Map<String, Object> skills = parseDocument(readText(SKILLS_RESOURCE), "tower/skills.json", "skills");
        Map<String, Object> supports = parseDocument(readText(SUPPORTS_RESOURCE), "tower/supports.json", "supports");
        return new Combat(true, player, monsters, skills, supports);
    }

    private static String readText(String resource) {
        try (InputStream in = TowerCombatCatalog.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException(resource.substring(1) + " 이 없어요");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(resource.substring(1) + " 을 읽지 못했어요", e);
        }
    }

    private static ObjectMapper strictMapper() {
        return new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private static JsonNode readTree(String json, String file) {
        JsonNode root;
        try {
            root = strictMapper().readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException(file + " 문법이 잘못됐어요: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) throw new IllegalStateException(file + " 형식이 잘못됐어요");
        return root;
    }

    /**
     * 스킬·보조 파일을 통째로 읽는다 (내용은 그대로 응답에 싣는다). 맨 위는 객체이고 listField 목록이 비어 있지 않아야 하며,
     * 어느 깊이에도 전투력 숫자 필드가 없어야 한다
     */
    static Map<String, Object> parseDocument(String json, String file, String listField) {
        JsonNode root = readTree(json, file);
        JsonNode list = root.get(listField);
        if (list == null || !list.isArray() || list.isEmpty()) {
            throw new IllegalStateException(file + " 에 " + listField + " 목록이 없어요");
        }
        rejectBattlePower(root, file);
        Map<String, Object> map = strictMapper().convertValue(root, new TypeReference<LinkedHashMap<String, Object>>() {});
        return Collections.unmodifiableMap(map);
    }

    private static void rejectBattlePower(JsonNode node, String file) {
        if (node.isObject()) {
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("battlepower") || lower.endsWith("bp")) {
                    throw new IllegalStateException(file + " 에 전투력 숫자 필드는 둘 수 없어요: " + name);
                }
            }
        }
        node.forEach(child -> rejectBattlePower(child, file));
    }

    /**
     * player.json 검증: 필드가 모두 있고 형식이 맞는다. HP·MP·피해·사거리는 0보다 큰 정수, 쿨다운은 0보다 크고,
     * 배수·확률은 0 초과 1 이하(치명타 배수는 1 이상)다. 모르는 필드(오타, 전투력 숫자 등)는 받지 않는다
     */
    static Player parsePlayer(String json) {
        String file = "tower/player.json";
        JsonNode root = readTree(json, file);
        requireKnownFields(root, PLAYER_FIELDS, "맨 위");
        JsonNode provisional = root.get("provisional");
        if (provisional == null || !provisional.isBoolean()) {
            throw new IllegalStateException(file + " 에 provisional(true/false)이 없어요");
        }

        JsonNode m = section(root, "melee", MELEE_FIELDS);
        Melee melee = new Melee(positiveInt(m, "damage", "melee"), positive(m, "cooldownSeconds", "melee"),
                positiveInt(m, "rangePx", "melee"), positive(m, "arcRadians", "melee"),
                nonNegativeInt(m, "mpCost", "melee"), nonNegativeInt(m, "knockback", "melee"));

        JsonNode r = section(root, "ranged", RANGED_FIELDS);
        Ranged ranged = new Ranged(positiveInt(r, "damage", "ranged"), positive(r, "cooldownSeconds", "ranged"),
                positiveInt(r, "rangePx", "ranged"), positiveInt(r, "projectileSpeed", "ranged"),
                positive(r, "projectileLifeSeconds", "ranged"),
                nonNegativeInt(r, "mpCost", "ranged"), nonNegativeInt(r, "knockback", "ranged"));

        JsonNode c = section(root, "critical", CRITICAL_FIELDS);
        Critical critical = new Critical(fraction(c, "chance", "critical"),
                atLeastOne(c, "damageMultiplier", "critical"), atLeastOne(c, "knockbackMultiplier", "critical"));

        JsonNode b = section(root, "block", BLOCK_FIELDS);
        Block block = new Block(positive(b, "seconds", "block"), nonNegative(b, "extraCooldownSeconds", "block"),
                fraction(b, "damageTakenMultiplier", "block"));

        JsonNode mv = section(root, "movement", MOVEMENT_FIELDS);
        Movement movement = new Movement(positiveInt(mv, "speedPxPerSecond", "movement"),
                fraction(mv, "attackingSpeedMultiplier", "movement"));

        JsonNode rg = section(root, "regen", REGEN_FIELDS);
        Regen regen = new Regen(nonNegativeInt(rg, "hpPerSecond", "regen"), nonNegativeInt(rg, "mpPerSecond", "regen"),
                nonNegative(rg, "mpDelaySeconds", "regen"));

        JsonNode h = section(root, "hunger", HUNGER_FIELDS);
        Hunger hunger = new Hunger(positiveInt(h, "satietyBelow", "hunger"),
                fraction(h, "damageMultiplier", "hunger"), fraction(h, "speedMultiplier", "hunger"));

        JsonNode d = section(root, "defeat", DEFEAT_FIELDS);
        Defeat defeat = new Defeat(text(d, "restartAt", "defeat"), bool(d, "restoreHp", "defeat"),
                bool(d, "restoreMp", "defeat"), bool(d, "losesAnything", "defeat"));

        return new Player(provisional.asBoolean(), text(root, "source", "맨 위"), text(root, "note", "맨 위"),
                positiveInt(root, "hp", "맨 위"), positiveInt(root, "mp", "맨 위"), positiveInt(root, "mpScaleMax", "맨 위"),
                text(root, "mpNote", "맨 위"), melee, ranged, critical, block, movement, regen, hunger, defeat,
                text(root, "damageFormula", "맨 위"));
    }

    private static JsonNode section(JsonNode root, String field, Set<String> allowed) {
        JsonNode n = root.get(field);
        if (n == null || !n.isObject()) throw new IllegalStateException("tower/player.json 에 " + field + " 가 없어요");
        requireKnownFields(n, allowed, field);
        return n;
    }

    private static int requireInt(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isInt()) {
            throw new IllegalStateException("tower/player.json " + where + " 에 정수 " + field + " 가 없어요");
        }
        return v.asInt();
    }

    private static int positiveInt(JsonNode node, String field, String where) {
        int v = requireInt(node, field, where);
        if (v <= 0) throw new IllegalStateException(where + " 의 " + field + " 는 0보다 커야 해요: " + v);
        return v;
    }

    private static int nonNegativeInt(JsonNode node, String field, String where) {
        int v = requireInt(node, field, where);
        if (v < 0) throw new IllegalStateException(where + " 의 " + field + " 는 0 이상이어야 해요: " + v);
        return v;
    }

    private static double requireNumber(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isNumber()) {
            throw new IllegalStateException("tower/player.json " + where + " 에 숫자 " + field + " 가 없어요");
        }
        return v.asDouble();
    }

    private static double positive(JsonNode node, String field, String where) {
        double v = requireNumber(node, field, where);
        if (!(v > 0)) throw new IllegalStateException(where + " 의 " + field + " 는 0보다 커야 해요: " + v);
        return v;
    }

    private static double nonNegative(JsonNode node, String field, String where) {
        double v = requireNumber(node, field, where);
        if (!(v >= 0)) throw new IllegalStateException(where + " 의 " + field + " 는 0 이상이어야 해요: " + v);
        return v;
    }

    /** 0 초과 1 이하 (배수·확률) */
    private static double fraction(JsonNode node, String field, String where) {
        double v = requireNumber(node, field, where);
        if (!(v > 0 && v <= 1)) throw new IllegalStateException(where + " 의 " + field + " 는 0 초과 1 이하여야 해요: " + v);
        return v;
    }

    private static double atLeastOne(JsonNode node, String field, String where) {
        double v = requireNumber(node, field, where);
        if (!(v >= 1)) throw new IllegalStateException(where + " 의 " + field + " 는 1 이상이어야 해요: " + v);
        return v;
    }

    private static String text(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalStateException("tower/player.json " + where + " 에 글 " + field + " 가 없어요");
        }
        return v.asText();
    }

    private static boolean bool(JsonNode node, String field, String where) {
        JsonNode v = node.get(field);
        if (v == null || !v.isBoolean()) {
            throw new IllegalStateException("tower/player.json " + where + " 에 true/false " + field + " 가 없어요");
        }
        return v.asBoolean();
    }

    private static void requireKnownFields(JsonNode node, Set<String> allowed, String where) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!allowed.contains(name)) {
                throw new IllegalStateException("tower/player.json " + where + " 에 모르는 필드: " + name);
            }
        }
    }
}
