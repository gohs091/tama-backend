package com.tamagochi.tamagochi.controller.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.tower.TowerMap;
import com.tamagochi.tamagochi.domain.tower.TowerMapCatalog;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 맵 읽기 주소(M-1): 맵 한 장·층 전체, 맵이 없는 층의 거절, 그리고 예전 탑 응답이 그대로인지 */
class TowerMapApiTest extends TowerLunchTestSupport {

    @Autowired MockMvc mvc;
    @Autowired AuthTokenService tokens;

    @Test
    @DisplayName("GET /api/tower/maps/{floor}/{map}: 칸·충돌·포탈·스폰·보스 경기장·구출 자리를 준다. 전투력 숫자는 없다")
    void oneMap() throws Exception {
        User user = newUser(0, 100);
        String bearer = "Bearer " + tokens.issue(user.getTossUserId());
        TowerMap first = TowerMapCatalog.map(1, 1).orElseThrow();

        mvc.perform(get("/api/tower/maps/1/1").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floor").value(1))
                .andExpect(jsonPath("$.map").value(1))
                .andExpect(jsonPath("$.faction").value("nature-spirits"))
                .andExpect(jsonPath("$.seed").value(first.seed()))
                .andExpect(jsonPath("$.width").value(first.width()))
                .andExpect(jsonPath("$.height").value(first.height()))
                .andExpect(jsonPath("$.tileSize").value(32))
                .andExpect(jsonPath("$.tiles.length()").value(first.height()))
                .andExpect(jsonPath("$.tiles[0].length()").value(first.width()))
                .andExpect(jsonPath("$.tiles[0][0]").value(2))          // 테두리는 벽
                .andExpect(jsonPath("$.collision.length()").value(first.height()))
                .andExpect(jsonPath("$.collision[0][0]").value(1))
                .andExpect(jsonPath("$.entry.x").value(first.entry().x()))
                .andExpect(jsonPath("$.entry.y").value(first.entry().y()))
                .andExpect(jsonPath("$.exit.x").value(first.exit().x()))
                .andExpect(jsonPath("$.spawns.length()").value(9))
                .andExpect(jsonPath("$.spawns[0].role").value("normal"))
                .andExpect(jsonPath("$.bossArena").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rescues").isEmpty())
                .andExpect(jsonPath("$.theme.id").value("spirit-forest"))
                .andExpect(jsonPath("$.theme.tileTypes.length()").value(9))
                .andExpect(jsonPath("$.theme.tileTypes[2].key").value("wall"))
                .andExpect(jsonPath("$.theme.tileTypes[2].walkable").value(false))
                .andExpect(jsonPath("$.provisional").value(true))
                .andExpect(jsonPath("$..bp").isEmpty())
                .andExpect(jsonPath("$..recommendedBp").isEmpty());

        mvc.perform(get("/api/tower/maps/1/5").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spawns.length()").value(9))
                .andExpect(jsonPath("$.spawns[8].role").value("boss"))
                .andExpect(jsonPath("$.bossArena.width").isNumber())
                .andExpect(jsonPath("$.bossArena.center.x").isNumber())
                .andExpect(jsonPath("$.rescues.length()").value(2))
                .andExpect(jsonPath("$.rescues[0].npcId").value("ns-hamster"))
                .andExpect(jsonPath("$.rescues[1].npcId").value("ns-woodpecker"));

        mvc.perform(get("/api/tower/maps/2/5").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faction").value("deep-savers"))
                .andExpect(jsonPath("$.theme.id").value("deep-sea"))
                .andExpect(jsonPath("$.rescues.length()").value(1))
                .andExpect(jsonPath("$.rescues[0].npcId").value("ds-sea-otter"));
    }

    @Test
    @DisplayName("GET /api/tower/maps/{floor}: 그 층의 맵 5장. 맵이 없는 층·맵은 400 NO_MAP_DATA, 토큰이 없으면 401")
    void floorMapsAndMissing() throws Exception {
        User user = newUser(0, 100);
        String bearer = "Bearer " + tokens.issue(user.getTossUserId());

        mvc.perform(get("/api/tower/maps/2").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floor").value(2))
                .andExpect(jsonPath("$.faction").value("deep-savers"))
                .andExpect(jsonPath("$.mapsPerFloor").value(5))
                .andExpect(jsonPath("$.maps.length()").value(5))
                .andExpect(jsonPath("$.maps[0].map").value(1))
                .andExpect(jsonPath("$.maps[2].spawns.length()").value(15))
                .andExpect(jsonPath("$.maps[4].bossArena.height").isNumber());

        for (String path : new String[]{"/api/tower/maps/3", "/api/tower/maps/3/1", "/api/tower/maps/17/5",
                "/api/tower/maps/0", "/api/tower/maps/52/1", "/api/tower/maps/1/0", "/api/tower/maps/1/6", "/api/tower/maps/9999/1"}) {
            mvc.perform(get(path).header("Authorization", bearer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NO_MAP_DATA"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        mvc.perform(get("/api/tower/maps/1/abc").header("Authorization", bearer)).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/tower/maps/1/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("예전 탑 응답은 그대로다: 상태 읽기·출발의 필드가 그대로 있고 맵 데이터가 끼어들지 않는다")
    void oldTowerResponsesAreUnchanged() throws Exception {
        User user = newUser(0, 100);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);

        mvc.perform(get("/api/tower/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(false))
                .andExpect(jsonPath("$.mapsPerFloor").value(5))
                .andExpect(jsonPath("$.openFloorCap").value(17))
                .andExpect(jsonPath("$.tiles").doesNotExist());
        mvc.perform(post("/api/tower/depart").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tossUserId\":\"" + id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(true))
                .andExpect(jsonPath("$.currentFloor").value(1))
                .andExpect(jsonPath("$.currentMap").value(1))
                .andExpect(jsonPath("$.faction").value("nature-spirits"))
                .andExpect(jsonPath("$.currentMapMonsters.normal").value(9))
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.tiles").doesNotExist());
    }
}
