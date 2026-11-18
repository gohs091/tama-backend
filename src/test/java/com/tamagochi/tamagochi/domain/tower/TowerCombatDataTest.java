package com.tamagochi.tamagochi.domain.tower;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** 전투 규칙 데이터 골격(resources/tower/*.json)이 읽히고, 시안에서 옮긴 값·임시 표시·꺼 둔 플래그가 그대로인지 */
class TowerCombatDataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static JsonNode read(String name) throws Exception {
        try (InputStream in = TowerCombatDataTest.class.getResourceAsStream("/tower/" + name)) {
            assertThat(in).as(name).isNotNull();
            return MAPPER.readTree(in);
        }
    }

    private static List<String> ids(JsonNode list) {
        List<String> out = new ArrayList<>();
        list.forEach(n -> out.add(n.get("id").asText()));
        return out;
    }

    @Test
    @DisplayName("skills.json: 시안 스킬 9종, 이름·설명이 있고 시안 값(임시)으로 표시돼 있다. SPEC §2.4 수치 그대로")
    void skills() throws Exception {
        JsonNode root = read("skills.json");
        assertThat(root.get("provisional").asBoolean()).isTrue();
        assertThat(ids(root.get("skills"))).containsExactly(
                "fire", "water", "wind", "earth", "summon", "aPoison", "aCurse", "aIce", "aRust");
        for (JsonNode s : root.get("skills")) {
            assertThat(s.get("name").asText()).as(s.toString()).isNotBlank();
            assertThat(s.get("description").asText()).isNotBlank();
            assertThat(s.get("source").asText()).isEqualTo("prototype");
            assertThat(s.get("provisional").asBoolean()).isTrue();
            assertThat(s.get("kind").asText()).isIn("charged", "arrow", "summon");
        }

        JsonNode charged = root.get("charged");
        assertThat(charged.get("baseDamage").asInt()).isEqualTo(45);
        assertThat(charged.get("mpCost").asInt()).isEqualTo(20);
        assertThat(charged.get("chargeSeconds").asDouble()).isEqualTo(1.5);
        assertThat(charged.get("cooldownSeconds").asDouble()).isEqualTo(2.5);
        // 배율 표: 시간% / 피해% / 밀림% / MP%
        int[][] table = {{9, 117, 27, 0}, {-9, 18, 27, 36}, {-18, 36, 27, 18}, {0, 36, 72, 54}};
        for (int i = 0; i < 4; i++) {
            JsonNode s = root.get("skills").get(i);
            assertThat(s.get("kind").asText()).isEqualTo("charged");
            JsonNode m = s.get("modifiersPercent");
            assertThat(new int[]{m.get("chargeTime").asInt(), m.get("damage").asInt(), m.get("knockback").asInt(), m.get("mp").asInt()})
                    .as(s.get("id").asText()).containsExactly(table[i]);
        }

        JsonNode arrow = root.get("arrow");
        assertThat(arrow.get("damage").asInt()).isEqualTo(28);
        assertThat(arrow.get("mpCost").asInt()).isEqualTo(8);
        assertThat(arrow.get("cooldownSeconds").asDouble()).isEqualTo(0.5);
        assertThat(arrow.get("projectileSpeed").asInt()).isEqualTo(560);

        JsonNode summon = root.get("summon");
        assertThat(summon.get("mpCost").asInt()).isEqualTo(25);
        assertThat(summon.get("cooldownSeconds").asDouble()).isEqualTo(1.5);
        assertThat(summon.get("durationSeconds").asInt()).isEqualTo(30);
        assertThat(summon.get("hitDamage").asInt()).isEqualTo(22);
        assertThat(summon.get("hitIntervalSeconds").asDouble()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("supports.json: 시안 보조 18종, 슬롯 키워드·이름·설명이 있고 임시로 표시돼 있다")
    void supports() throws Exception {
        JsonNode root = read("supports.json");
        assertThat(root.get("provisional").asBoolean()).isTrue();
        assertThat(ids(root.get("supports"))).containsExactly("split", "pierce", "burst", "b5", "a5", "shadow", "a2", "k2", "k4",
                "over", "b6", "k0", "b9", "tagP", "tagR", "tagC", "tagI", "convC");
        for (JsonNode s : root.get("supports")) {
            assertThat(s.get("slotKeyword").asText()).as(s.toString()).isIn("탄", "근접", "범위", "스킬", "");
            assertThat(s.get("name").asText()).isNotBlank();
            assertThat(s.get("description").asText()).isNotBlank();
            assertThat(s.get("provisional").asBoolean()).isTrue();
            assertThat(s.get("source").asText()).isEqualTo("prototype");
        }
        assertThat(root.get("supports").get(0).get("slotKeyword").asText()).isEqualTo("탄");
        assertThat(root.get("supports").get(17).get("name").asText()).isEqualTo("카오스로 바꾸기");
    }

    @Test
    @DisplayName("constellation.json: 골격만 — 규칙은 미정(문서와 시안이 다름)이고 노드 트리는 비어 있다")
    void constellation() throws Exception {
        JsonNode root = read("constellation.json");
        assertThat(root.get("provisional").asBoolean()).isTrue();
        assertThat(root.get("status").asText()).isEqualTo("미정");
        assertThat(root.get("nodes").isArray()).isTrue();
        assertThat(root.get("nodes")).isEmpty(); // 노드 트리는 H-3에서 채운다
        JsonNode rules = root.get("pointRules");
        assertThat(rules.get("decided").isNull()).isTrue();
        assertThat(rules.get("document").get("pointsPerLevel").asInt()).isEqualTo(1);
        assertThat(rules.get("document").get("bonusPercentPerNode").asInt()).isEqualTo(3);
        assertThat(rules.get("prototype").get("startLevel").asInt()).isEqualTo(40);
        assertThat(rules.get("prototype").get("pointsPerMapClear").asInt()).isEqualTo(1);
        assertThat(rules.get("prototype").get("levelCap").asInt()).isEqualTo(120);
    }

    @Test
    @DisplayName("drops.json: 시안 확률을 임시로 옮겼고, 꺼져 있다 (enabled=false)")
    void drops() throws Exception {
        JsonNode root = read("drops.json");
        assertThat(root.get("enabled").isBoolean()).isTrue();
        assertThat(root.get("enabled").asBoolean()).isFalse();
        assertThat(root.get("provisional").asBoolean()).isTrue();
        JsonNode skill = root.get("skill");
        assertThat(skill.get("normal").get("chance").asDouble()).isEqualTo(0.30);
        assertThat(skill.get("normal").get("chanceIfOwned").asDouble()).isEqualTo(0.10);
        assertThat(skill.get("swarm").get("chance").asDouble()).isEqualTo(0.07);
        assertThat(skill.get("swarm").get("chanceIfOwned").asDouble()).isEqualTo(0.02);
        assertThat(skill.get("boss").get("chance").asDouble()).isEqualTo(1.0);
        JsonNode support = root.get("support");
        assertThat(support.get("normal").get("chance").asDouble()).isEqualTo(0.20);
        assertThat(support.get("swarm").get("chance").asDouble()).isEqualTo(0.05);
        assertThat(support.get("boss").get("chance").asDouble()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("전투 데이터 어디에도 전투력 숫자 필드(recommendedBp·battlePower·bp)가 없다")
    void noBattlePowerFields() throws Exception {
        for (String name : new String[]{"skills.json", "supports.json", "constellation.json", "drops.json", "monsters.json", "floors.json"}) {
            List<String> fields = new ArrayList<>();
            collectFieldNames(read(name), fields);
            for (String field : fields) {
                String f = field.toLowerCase(Locale.ROOT);
                assertThat(f.contains("battlepower") || f.endsWith("bp")).as(name + " 의 " + field).isFalse();
            }
        }
    }

    private static void collectFieldNames(JsonNode node, List<String> out) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(out::add);
        }
        node.forEach(child -> collectFieldNames(child, out));
    }
}
