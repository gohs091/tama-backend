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
        String lunchbox = "{\"tossUserId\":\"" + id + "\",\"id\":\"lunchbox\",\"count\":1}";

        mvc.perform(get("/api/lunch/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalog[0].id").value("lunchbox"))
                .andExpect(jsonPath("$.catalog[0].fruitCost").value(3))
                .andExpect(jsonPath("$.catalog.length()").value(1))   // 도시락은 한 종류
                .andExpect(jsonPath("$.catalog[0].satiety").value(35))
                .andExpect(jsonPath("$.catalog[0].locked").value(false))
                .andExpect(jsonPath("$.catalog[0].unlock").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cupboard").isMap())             // 예전 모양 그대로 { id: 개수 }
                .andExpect(jsonPath("$.bag").isMap())
                .andExpect(jsonPath("$.cupboardCount").value(0))
                .andExpect(jsonPath("$.bagCount").value(0))
                .andExpect(jsonPath("$.bagSlots").value(2))
                .andExpect(jsonPath("$.fruit").value(10))
                .andExpect(jsonPath("$.satiety").value(100))
                .andExpect(jsonPath("$.onExpedition").value(false));

        call(bearer, "/api/lunch/cook", lunchbox).andExpect(status().isOk())
                .andExpect(jsonPath("$.fruit").value(7))
                .andExpect(jsonPath("$.cupboard.lunchbox").value(1));
        call(bearer, "/api/lunch/pack", lunchbox).andExpect(status().isOk())
                .andExpect(jsonPath("$.bag.lunchbox").value(1))
                .andExpect(jsonPath("$.bagUsed").value(1));
        call(bearer, "/api/lunch/eat", lunchbox).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ON_EXPEDITION"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        call(bearer, "/api/tower/depart", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(true))
                .andExpect(jsonPath("$.currentFloor").value(1))
                .andExpect(jsonPath("$.mapsPerFloor").value(5))   // 예전 필드 그대로, 값은 지금 층의 맵 수
                .andExpect(jsonPath("$.openFloorCap").value(17))
                .andExpect(jsonPath("$.expeditionSecondsLeft").value(3600))
                .andExpect(jsonPath("$.mapSatietyCost").value(35));
        call(bearer, "/api/tower/clear-map", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.satiety").value(65))
                .andExpect(jsonPath("$.spentToday").value(35))
                .andExpect(jsonPath("$.currentMap").value(2))
                .andExpect(jsonPath("$.result.mapCleared").value(true))
                .andExpect(jsonPath("$.result.expeditionEnded").value(false));
        call(bearer, "/api/lunch/eat", lunchbox).andExpect(status().isOk())
                .andExpect(jsonPath("$.recovered").value(35))
                .andExpect(jsonPath("$.satiety").value(100));
        call(bearer, "/api/tower/return", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(false))
                .andExpect(jsonPath("$.result.endReason").value("RETURNED"));
        mvc.perform(get("/api/tower/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highestFloor").value(0))
                .andExpect(jsonPath("$.expeditionSecondsLeft").doesNotExist())
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
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_FRUIT")); // 잠긴 도시락은 없다: 예전 id도 같은 도시락
        call(bearer, "/api/lunch/cook", "{\"tossUserId\":\"" + id + "\",\"id\":\"pizza\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_LUNCHBOX"));
        call(bearer, "/api/tower/clear-map", "{\"tossUserId\":\"" + id + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ON_EXPEDITION"));

        mvc.perform(get("/api/lunch/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tower/depart").contentType(MediaType.APPLICATION_JSON).content("{\"tossUserId\":\"" + id + "\"}"))
                .andExpect(status().isUnauthorized());
        call(bearer, "/api/tower/depart", "{\"tossUserId\":\"toss_someone_else\"}").andExpect(status().isForbidden());

        // 개발용 지급 (dev-tools가 켜진 환경에서만 있는 주소): 예전 id여도, id가 없어도 지금 도시락이 찬장에 들어온다
        call(bearer, "/api/dev/lunch?id=feast&count=2", "{\"tossUserId\":\"" + id + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupboard.lunchbox").value(2));
        call(bearer, "/api/dev/lunch", "{\"tossUserId\":\"" + id + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupboard.lunchbox").value(3))
                .andExpect(jsonPath("$.cupboardCount").value(3));
    }

    @Test
    @DisplayName("예전 앱의 요청: 예전 id(riceball·hearty·feast)여도, id가 없어도 200이고 지금 도시락 하나로 처리한다")
    void oldIdsAndMissingIdSucceed() throws Exception {
        User user = newUser(12, 30);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);
        String me = "{\"tossUserId\":\"" + id + "\"}";

        call(bearer, "/api/lunch/cook", "{\"tossUserId\":\"" + id + "\",\"id\":\"riceball\",\"count\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fruit").value(9))
                .andExpect(jsonPath("$.cupboard.lunchbox").value(1))
                .andExpect(jsonPath("$.cupboard.riceball").doesNotExist());
        call(bearer, "/api/lunch/cook", "{\"tossUserId\":\"" + id + "\",\"id\":\"hearty\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupboard.lunchbox").value(2));
        call(bearer, "/api/lunch/cook", "{\"tossUserId\":\"" + id + "\",\"id\":\"feast\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupboard.lunchbox").value(3));
        call(bearer, "/api/lunch/cook", me) // id도 count도 없다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fruit").value(0))
                .andExpect(jsonPath("$.cupboardCount").value(4));

        call(bearer, "/api/lunch/pack", "{\"tossUserId\":\"" + id + "\",\"id\":\"feast\",\"count\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bag.lunchbox").value(1));
        call(bearer, "/api/lunch/pack", "{\"tossUserId\":\"" + id + "\",\"id\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bagCount").value(2))
                .andExpect(jsonPath("$.bagUsed").value(2));
        call(bearer, "/api/lunch/unpack", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.bag.lunchbox").value(1))
                .andExpect(jsonPath("$.cupboard.lunchbox").value(3));
        call(bearer, "/api/lunch/pack", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.bagCount").value(2));

        call(bearer, "/api/tower/depart", me).andExpect(status().isOk());
        call(bearer, "/api/lunch/eat", "{\"tossUserId\":\"" + id + "\",\"id\":\"riceball\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recovered").value(35))
                .andExpect(jsonPath("$.satiety").value(65));
        call(bearer, "/api/lunch/eat", me).andExpect(status().isOk())
                .andExpect(jsonPath("$.recovered").value(35))
                .andExpect(jsonPath("$.satiety").value(100))
                .andExpect(jsonPath("$.bag").isEmpty())
                .andExpect(jsonPath("$.bagCount").value(0));
    }

    @Test
    @DisplayName("햄스터를 구하기 전에 count를 여러 개로 보내도 200이고 하나만 만든다. 구한 뒤에는 보낸 만큼 만든다")
    void cookCountIsClampedUntilHamsterRescue() throws Exception {
        User user = newUser(30, 50);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);
        String five = "{\"tossUserId\":\"" + id + "\",\"id\":\"lunchbox\",\"count\":5}";

        call(bearer, "/api/lunch/cook", five).andExpect(status().isOk())
                .andExpect(jsonPath("$.batchCooking").value(false))
                .andExpect(jsonPath("$.cupboard.lunchbox").value(1))
                .andExpect(jsonPath("$.cupboardCount").value(1))
                .andExpect(jsonPath("$.fruit").value(27))
                .andExpect(jsonPath("$.bagSlots").value(2))
                .andExpect(jsonPath("$.catalog[0].locked").value(false));
        call(bearer, "/api/lunch/cook", "{\"tossUserId\":\"" + id + "\",\"count\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUNT"));

        rescueHamster();
        call(bearer, "/api/lunch/cook", five).andExpect(status().isOk())
                .andExpect(jsonPath("$.batchCooking").value(true))
                .andExpect(jsonPath("$.cupboardCount").value(6))
                .andExpect(jsonPath("$.fruit").value(12))
                .andExpect(jsonPath("$.bagSlots").value(3))
                .andExpect(jsonPath("$.catalog[0].locked").value(false));
    }
}
