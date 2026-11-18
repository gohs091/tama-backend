package com.tamagochi.tamagochi.controller.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.tower.TowerMonsterCatalog;
import com.tamagochi.tamagochi.domain.tower.TowerMonsterCatalog.FloorMonsters;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 전투 데이터 읽기 주소(H-2): 끄적이 기본 수치·층별 몬스터·스킬·보조, 그리고 예전 탑 응답이 그대로인지 */
class TowerCombatApiTest extends TowerLunchTestSupport {

    @Autowired MockMvc mvc;
    @Autowired AuthTokenService tokens;

    @Test
    @DisplayName("GET /api/tower/combat: 200, 1~2층 몬스터 수치가 monsters.json과 같고 끄적이 기본 수치·스킬·보조가 온다. 전투력 숫자는 없다")
    void combat() throws Exception {
        User user = newUser(0, 100);
        String bearer = "Bearer " + tokens.issue(user.getTossUserId());

        ResultActions res = mvc.perform(get("/api/tower/combat").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provisional").value(true))
                .andExpect(jsonPath("$.monsters.length()").value(TowerMonsterCatalog.monsters().byFloor().size()));

        // 몬스터: 파일에서 읽은 값 그대로
        for (int i = 0; i < 2; i++) {
            FloorMonsters m = TowerMonsterCatalog.floor(i + 1).orElseThrow();
            String at = "$.monsters[" + i + "]";
            res.andExpect(jsonPath(at + ".floor").value(m.floor()))
                    .andExpect(jsonPath(at + ".normal.hp").value(m.normal().hp()))
                    .andExpect(jsonPath(at + ".normal.attack").value(m.normal().attack()))
                    .andExpect(jsonPath(at + ".swarm.hp").value(m.swarm().hp()))
                    .andExpect(jsonPath(at + ".swarm.attack").value(m.swarm().attack()))
                    .andExpect(jsonPath(at + ".boss.hp").value(m.boss().hp()))
                    .andExpect(jsonPath(at + ".boss.attack").value(m.boss().attack()));
        }
        // 정한 숫자(임시)도 한 번 못 박아 둔다
        res.andExpect(jsonPath("$.monsters[0].normal.hp").value(100))
                .andExpect(jsonPath("$.monsters[0].normal.attack").value(24))
                .andExpect(jsonPath("$.monsters[0].swarm.hp").value(30))
                .andExpect(jsonPath("$.monsters[0].boss.hp").value(520))
                .andExpect(jsonPath("$.monsters[0].boss.attack").value(38))
                .andExpect(jsonPath("$.monsters[1].normal.hp").value(109))
                .andExpect(jsonPath("$.monsters[1].swarm.attack").value(9))
                .andExpect(jsonPath("$.monsters[1].boss.hp").value(564))
                .andExpect(jsonPath("$.monsters[1].boss.attack").value(42));

        // 끄적이 기본 수치 (임시)
        res.andExpect(jsonPath("$.player.provisional").value(true))
                .andExpect(jsonPath("$.player.source").value("prototype"))
                .andExpect(jsonPath("$.player.note").isNotEmpty())
                .andExpect(jsonPath("$.player.hp").value(480))
                .andExpect(jsonPath("$.player.mp").value(440))
                .andExpect(jsonPath("$.player.mpScaleMax").value(100))
                .andExpect(jsonPath("$.player.melee.damage").value(25))
                .andExpect(jsonPath("$.player.melee.cooldownSeconds").value(0.73))
                .andExpect(jsonPath("$.player.melee.rangePx").value(70))
                .andExpect(jsonPath("$.player.melee.mpCost").value(3))
                .andExpect(jsonPath("$.player.ranged.damage").value(11))
                .andExpect(jsonPath("$.player.ranged.cooldownSeconds").value(0.4))
                .andExpect(jsonPath("$.player.ranged.rangePx").value(387))
                .andExpect(jsonPath("$.player.ranged.projectileSpeed").value(430))
                .andExpect(jsonPath("$.player.critical.chance").value(0.15))
                .andExpect(jsonPath("$.player.block.damageTakenMultiplier").value(0.25))
                .andExpect(jsonPath("$.player.movement.speedPxPerSecond").value(175))
                .andExpect(jsonPath("$.player.regen.hpPerSecond").value(4))
                .andExpect(jsonPath("$.player.hunger.satietyBelow").value(30))
                .andExpect(jsonPath("$.player.hunger.damageMultiplier").value(0.75))
                .andExpect(jsonPath("$.player.hunger.speedMultiplier").value(0.8))
                .andExpect(jsonPath("$.player.defeat.restartAt").value("mapEntry"))
                .andExpect(jsonPath("$.player.defeat.restoreHp").value(true))
                .andExpect(jsonPath("$.player.defeat.restoreMp").value(true))
                .andExpect(jsonPath("$.player.defeat.losesAnything").value(false))
                .andExpect(jsonPath("$.player.damageFormula").isNotEmpty());

        // 스킬·보조: 파일 내용 그대로
        res.andExpect(jsonPath("$.skills.provisional").value(true))
                .andExpect(jsonPath("$.skills.skills.length()").value(9))
                .andExpect(jsonPath("$.skills.skills[0].id").value("fire"))
                .andExpect(jsonPath("$.skills.skills[0].modifiersPercent.damage").value(117))
                .andExpect(jsonPath("$.skills.charged.baseDamage").value(45))
                .andExpect(jsonPath("$.skills.arrow.damage").value(28))
                .andExpect(jsonPath("$.skills.summon.hitDamage").value(22))
                .andExpect(jsonPath("$.supports.supports.length()").value(18))
                .andExpect(jsonPath("$.supports.supports[0].id").value("split"))
                .andExpect(jsonPath("$.supports.supports[0].prototypeValues.extraShots").value(2))
                .andExpect(jsonPath("$.supports.slots.rows.length()").value(3));

        // 전투력 숫자는 어디에도 없다
        res.andExpect(jsonPath("$..bp").isEmpty())
                .andExpect(jsonPath("$..recommendedBp").isEmpty())
                .andExpect(jsonPath("$..battlePower").isEmpty());
        assertThat(res.andReturn().getResponse().getContentAsString().toLowerCase()).doesNotContain("bp\":");
    }

    @Test
    @DisplayName("GET /api/tower/combat: 누가 불러도, 몇 번을 불러도 같은 답이다. 토큰이 없으면 401")
    void sameForEveryone() throws Exception {
        User a = newUser(0, 100);
        User b = newUser(0, 10);
        String first = mvc.perform(get("/api/tower/combat").header("Authorization", "Bearer " + tokens.issue(a.getTossUserId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String again = mvc.perform(get("/api/tower/combat").header("Authorization", "Bearer " + tokens.issue(a.getTossUserId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String other = mvc.perform(get("/api/tower/combat").header("Authorization", "Bearer " + tokens.issue(b.getTossUserId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(again).isEqualTo(first);
        assertThat(other).isEqualTo(first);

        mvc.perform(get("/api/tower/combat")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("예전 탑 응답은 그대로다: 상태 읽기·출발·맵 주소에 전투 데이터가 끼어들지 않는다")
    void oldTowerResponsesAreUnchanged() throws Exception {
        User user = newUser(0, 100);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);

        mvc.perform(get("/api/tower/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(false))
                .andExpect(jsonPath("$.mapsPerFloor").value(5))
                .andExpect(jsonPath("$.openFloorCap").value(17))
                .andExpect(jsonPath("$.player").doesNotExist())
                .andExpect(jsonPath("$.monsters").doesNotExist())
                .andExpect(jsonPath("$.skills").doesNotExist());
        mvc.perform(post("/api/tower/depart").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tossUserId\":\"" + id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(true))
                .andExpect(jsonPath("$.currentFloor").value(1))
                .andExpect(jsonPath("$.currentMap").value(1))
                .andExpect(jsonPath("$.currentMapMonsters.normal").value(9))
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.player").doesNotExist())
                .andExpect(jsonPath("$.monsters").doesNotExist());
        mvc.perform(get("/api/tower/maps/1/1").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floor").value(1))
                .andExpect(jsonPath("$.spawns.length()").value(9));
    }
}
