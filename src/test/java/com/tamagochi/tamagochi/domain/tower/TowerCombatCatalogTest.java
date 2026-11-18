package com.tamagochi.tamagochi.domain.tower;

import com.tamagochi.tamagochi.domain.tower.TowerCombatCatalog.Combat;
import com.tamagochi.tamagochi.domain.tower.TowerCombatCatalog.Player;
import com.tamagochi.tamagochi.domain.tower.TowerMonsterCatalog.FloorMonsters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TowerCombatCatalogTest {

    private static String shippedPlayer() throws Exception {
        try (InputStream in = TowerCombatCatalog.class.getResourceAsStream(TowerCombatCatalog.PLAYER_RESOURCE)) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("전투 묶음이 읽힌다: 임시 표시, 몬스터는 monsters.json과 같고 층 순서, 스킬 9종·보조 18종")
    void shippedDataLoads() throws Exception {
        Combat combat = TowerCombatCatalog.combat();
        assertThat(combat.provisional()).isTrue();
        assertThat(TowerCombatCatalog.parsePlayer(shippedPlayer())).isEqualTo(combat.player()); // 파일을 다시 읽어도 같은 값

        assertThat(combat.monsters()).extracting(FloorMonsters::floor).containsExactly(1, 2);
        for (FloorMonsters m : combat.monsters()) {
            assertThat(m).isEqualTo(TowerMonsterCatalog.floor(m.floor()).orElseThrow());
        }

        assertThat((List<?>) combat.skills().get("skills")).hasSize(9);
        assertThat(((Map<?, ?>) combat.skills().get("arrow")).get("damage")).isEqualTo(28);
        assertThat((List<?>) combat.supports().get("supports")).hasSize(18);
        assertThatThrownBy(() -> combat.skills().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("끄적이 기본 수치(임시, 시안 자이로): HP 480·MP 440, 가까운 공격 25/0.73초/70px, 쏘기 11/0.4초, 배고픔 30 미만 ×0.75·×0.8")
    void shippedPlayerNumbers() {
        Player p = TowerCombatCatalog.player();
        assertThat(p.provisional()).isTrue();
        assertThat(p.source()).isEqualTo("prototype");
        assertThat(p.note()).contains("임시");
        assertThat(p.hp()).isEqualTo(480);
        assertThat(p.mp()).isEqualTo(440);
        assertThat(p.mpScaleMax()).isEqualTo(100);
        assertThat(p.melee()).isEqualTo(new TowerCombatCatalog.Melee(25, 0.73, 70, 1.05, 3, 330));
        assertThat(p.ranged()).isEqualTo(new TowerCombatCatalog.Ranged(11, 0.4, 387, 430, 0.9, 2, 190));
        assertThat(p.critical()).isEqualTo(new TowerCombatCatalog.Critical(0.15, 1.5, 1.3));
        assertThat(p.block()).isEqualTo(new TowerCombatCatalog.Block(0.25, 0.3, 0.25));
        assertThat(p.movement()).isEqualTo(new TowerCombatCatalog.Movement(175, 0.35));
        assertThat(p.regen()).isEqualTo(new TowerCombatCatalog.Regen(4, 14, 0.6));
        assertThat(p.hunger()).isEqualTo(new TowerCombatCatalog.Hunger(30, 0.75, 0.8));
        assertThat(p.defeat()).isEqualTo(new TowerCombatCatalog.Defeat("mapEntry", true, true, false));
        assertThat(p.damageFormula()).isNotBlank();
    }

    @Test
    @DisplayName("피해 계산 유틸: 포만감 30 미만이면 ×0.75, 치명타 ×1.5, 방어·저항 배수를 곱한다. 막으면 받는 피해 ×0.25")
    void damageHelpers() {
        Player p = TowerCombatCatalog.player();
        assertThat(p.hunger().hungry(30)).isFalse();
        assertThat(p.hunger().hungry(29)).isTrue();
        assertThat(p.outgoingDamage(p.melee().damage(), 100, 1, false)).isEqualTo(25.0);
        assertThat(p.outgoingDamage(p.melee().damage(), 30, 1, false)).isEqualTo(25.0);
        assertThat(p.outgoingDamage(p.melee().damage(), 29, 1, false)).isEqualTo(18.75);
        assertThat(p.outgoingDamage(p.melee().damage(), 100, 1, true)).isEqualTo(37.5);
        assertThat(p.outgoingDamage(p.melee().damage(), 100, 0.8, false)).isEqualTo(20.0);
        assertThat(p.outgoingDamage(p.ranged().damage(), 1, 1, true)).isEqualTo(11 * 0.75 * 1.5);
        assertThat(p.incomingDamage(24, false)).isEqualTo(24.0);
        assertThat(p.incomingDamage(24, true)).isEqualTo(6.0);
    }

    @Test
    @DisplayName("player.json: 깨진 값·빠진 필드·모르는 필드(전투력 숫자 포함)는 조용히 넘기지 않고 실패한다")
    void invalidPlayerFails() throws Exception {
        String good = shippedPlayer();
        String[] bad = {
                "",
                "[]",
                "{}",
                good + " trailing",
                good.replace("\"hp\": 480", "\"hp\": 0"),
                good.replace("\"hp\": 480", "\"hp\": \"480\""),
                good.replace("\"hp\": 480,", ""),
                good.replace("\"hp\": 480", "\"hp\": 480, \"hp\": 1"),
                good.replace("\"hp\": 480", "\"hp\": 480, \"bp\": 580"),
                good.replace("\"hp\": 480", "\"hp\": 480, \"battlePower\": 580"),
                good.replace("\"provisional\": true", "\"provisional\": \"yes\""),
                good.replace("\"damage\": 25", "\"damage\": -25"),
                good.replace("\"damage\": 25", "\"dmg\": 25"),
                good.replace("\"damage\": 11", "\"damage\": 11.5"),
                good.replace("\"cooldownSeconds\": 0.73", "\"cooldownSeconds\": 0"),
                good.replace("\"satietyBelow\": 30", "\"satietyBelow\": null"),
                good.replace("\"damageMultiplier\": 0.75", "\"damageMultiplier\": 1.2"),
                good.replace("\"speedMultiplier\": 0.8", "\"speedMultiplier\": 0"),
                good.replace("\"chance\": 0.15", "\"chance\": 15"),
                good.replace("\"restoreHp\": true", "\"restoreHp\": 1"),
                good.replace("\"restartAt\": \"mapEntry\"", "\"restartAt\": \"\""),
                good.replace("\"melee\": {", "\"melee2\": {")
        };
        for (String json : bad) {
            assertThat(json).as("바꿔치기가 먹혔는지").isNotEqualTo(good);
            assertThatThrownBy(() -> TowerCombatCatalog.parsePlayer(json)).as(json).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("스킬·보조 파일: 목록이 없거나 전투력 숫자 필드가 있으면 실패한다")
    void invalidDocumentFails() {
        assertThat(TowerCombatCatalog.parseDocument("{\"skills\": [{\"id\": \"fire\"}]}", "t.json", "skills")).containsKey("skills");
        String[] bad = {
                "",
                "[]",
                "{}",
                "{\"skills\": []}",
                "{\"skills\": {}}",
                "{\"skills\": [{\"id\": \"fire\"}]} x",
                "{\"skills\": [{\"id\": \"fire\", \"recommendedBp\": 100}]}",
                "{\"battlePower\": 1, \"skills\": [{\"id\": \"fire\"}]}"
        };
        for (String json : bad) {
            assertThatThrownBy(() -> TowerCombatCatalog.parseDocument(json, "t.json", "skills")).as(json)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
