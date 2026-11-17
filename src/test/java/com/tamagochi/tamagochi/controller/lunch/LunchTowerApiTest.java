package com.tamagochi.tamagochi.controller.lunch;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 도시락·탑 API가 로그인 토큰으로 이어지는지: 만들기 → 챙기기 → 출발 → 맵 통과 → 먹기 → 귀환 */
class LunchTowerApiTest extends TowerLunchTestSupport {

    @Autowired MockMvc mvc;
    @Autowired AuthTokenService tokens;

    private ResultActions call(String bearer, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("도시락을 만들어 챙기고, 탑에서 맵을 통과하고 먹고 돌아온다")
    void lunchAndTowerFlow() throws Exception {
        User user = newUser(10, 100);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);
        String me = "{\"tossUserId\":\"" + id + "\"}";
        String riceball = "{\"tossUserId\":\"" + id + "\",\"id\":\"riceball\",\"count\":1}";

        mvc.perform(get("/api/lunch/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalog[0].id").value("riceball"))
                .andExpect(jsonPath("$.catalog[0].fruitCost").value(2))
                .andExpect(jsonPath("$.catalog[2].locked").value(true))
                .andExpect(jsonPath("$.catalog[2].unlock.id").value("hamster"))
                .andExpect(jsonPath("$.bagSlots").value(2))
                .andExpect(jsonPath("$.fruit").value(10))
                .andExpect(jsonPath("$.satiety").value(100))
                .andExpect(jsonPath("$.onExpedition").value(false));

        call(bearer, "/api/lunch/cook", riceball).andExpect(status().isOk())
                .andExpect(jsonPath("$.fruit").value(8))
                .andExpect(jsonPath("$.cupboard.riceball").value(1));
        call(bearer, "/api/lunch/pack", riceball).andExpect(status().isOk())
                .andExpect(jsonPath("$.bag.riceball").value(1))
                .andExpect(jsonPath("$.bagUsed").value(1));
        call(bearer, "/api/lunch/eat", riceball).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ON_EXPEDITION"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        call(bearer, "/api/tower/depart", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(true))
                .andExpect(jsonPath("$.currentFloor").value(1))
                .andExpect(jsonPath("$.mapSatietyCost").value(35));
        call(bearer, "/api/tower/clear-map", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.satiety").value(65))
                .andExpect(jsonPath("$.spentToday").value(35))
                .andExpect(jsonPath("$.currentMap").value(2))
                .andExpect(jsonPath("$.result.mapCleared").value(true))
                .andExpect(jsonPath("$.result.expeditionEnded").value(false));
        call(bearer, "/api/lunch/eat", riceball).andExpect(status().isOk())
                .andExpect(jsonPath("$.recovered").value(20))
                .andExpect(jsonPath("$.satiety").value(85));
        call(bearer, "/api/tower/return", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(false))
                .andExpect(jsonPath("$.result.endReason").value("RETURNED"));
        mvc.perform(get("/api/tower/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highestFloor").value(0))
                .andExpect(jsonPath("$.spentToday").value(35));
    }

    @Test
    @DisplayName("거절은 400과 이유 코드로 알리고, 토큰이 없으면 401, 남의 ID면 403이다")
    void errorsAndAuth() throws Exception {
        User user = newUser(1, 100);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);

        call(bearer, "/api/lunch/cook", "{\"tossUserId\":\"" + id + "\",\"id\":\"riceball\",\"count\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_FRUIT"));
        call(bearer, "/api/lunch/cook", "{\"tossUserId\":\"" + id + "\",\"id\":\"feast\",\"count\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCKED"));
        call(bearer, "/api/tower/clear-map", "{\"tossUserId\":\"" + id + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ON_EXPEDITION"));

        mvc.perform(get("/api/lunch/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tower/depart").contentType(MediaType.APPLICATION_JSON).content("{\"tossUserId\":\"" + id + "\"}"))
                .andExpect(status().isUnauthorized());
        call(bearer, "/api/tower/depart", "{\"tossUserId\":\"toss_someone_else\"}").andExpect(status().isForbidden());

        // 개발용 지급 (dev-tools가 켜진 환경에서만 있는 주소): 잠긴 도시락도 찬장에 들어온다
        call(bearer, "/api/dev/lunch?id=feast&count=2", "{\"tossUserId\":\"" + id + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupboard.feast").value(2));
    }
}
