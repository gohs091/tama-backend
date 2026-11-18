package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.tower.TowerFloorCatalog;
import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.tower.TowerProgressRepository;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 맵 통과 검증(H-1): 원정 id, 층·맵 순서, 맵 최소 체류 시간. 운영 기본값과 같은 20초로 켜고 시계를 옮겨 가며 확인한다
 * (다른 테스트는 application-test.yml의 0초를 쓴다). 이 클래스는 시계를 앞으로만 옮긴다
 */
@TestPropertySource(properties = "tower.clear-map.min-seconds=20")
class ClearMapValidationTest extends TowerLunchTestSupport {

    private static final int MIN = 20;

    @Autowired TowerProgressRepository progressRepository;
    @Autowired MockMvc mvc;
    @Autowired AuthTokenService tokens;

    private void pass(long seconds) {
        clock.now = clock.now.plusSeconds(seconds);
    }

    private TowerProgress progress(User user) {
        return progressRepository.findByUserId(user.getId()).orElseThrow();
    }

    private void patchProgress(User user, String field, Object value) {
        TowerProgress progress = progress(user);
        ReflectionTestUtils.setField(progress, field, value);
        progressRepository.save(progress);
    }

    /** 최소 시간을 기다리고 포만감을 채워 가며 맵을 n장 통과한다 (예전 앱처럼 runId 없이) */
    private TowerService.View clearMaps(User user, int n) {
        TowerService.View v = null;
        for (int i = 0; i < n; i++) {
            pass(MIN);
            fillSatiety(user);
            v = towerService.clearMap(user);
            assertThat(v.result().mapCleared()).isTrue();
        }
        return v;
    }

    @Test
    @DisplayName("출발하면 원정 id(runId)가 보이고 저장된다. 돌아오면 비워지고, 다시 떠나면 새 id를 받는다")
    void departIssuesRunId() {
        User user = newUser(0, 100);
        TowerService.View home = towerService.get(user);
        assertThat(home.runId()).isNull();
        assertThat(home.secondsUntilClear()).isNull();

        TowerService.View v = towerService.depart(user, null);
        assertThat(v.runId()).isNotBlank();
        assertThat(UUID.fromString(v.runId()).toString()).isEqualTo(v.runId());
        assertThat(v.secondsUntilClear()).isEqualTo(MIN);
        assertThat(progress(user).getRunId()).isEqualTo(v.runId());
        assertThat(progress(user).getMapEnteredAt()).isEqualTo(progress(user).getDepartedAt());
        assertThat(towerService.get(user).runId()).isEqualTo(v.runId());

        TowerService.View back = towerService.returnHome(user);
        assertThat(back.runId()).isNull();
        assertThat(back.secondsUntilClear()).isNull();
        assertThat(progress(user).getRunId()).isNull();
        assertThat(progress(user).getMapEnteredAt()).isNull();

        TowerService.View again = towerService.depart(user, null);
        assertThat(again.runId()).isNotBlank().isNotEqualTo(v.runId());
        // 지난 원정의 id로는 통과할 수 없다
        pass(MIN);
        assertCode(() -> towerService.clearMap(user, v.runId(), null, null), "RUN_MISMATCH");
        assertThat(towerService.clearMap(user, again.runId(), null, null).result().mapCleared()).isTrue();
    }

    @Test
    @DisplayName("최소 체류 20초: 19초에는 거절(MAP_TOO_SOON)하고 포만감도 그대로, 20초에는 통과한다. runId 없는 예전 앱 요청에도 적용된다")
    void minimumTimePerMap() {
        User user = newUser(0, 100);
        towerService.depart(user, null);

        assertCode(() -> towerService.clearMap(user), "MAP_TOO_SOON"); // 출발하자마자
        pass(19);
        assertThatThrownBy(() -> towerService.clearMap(user))
                .isInstanceOfSatisfying(CodedException.class, e -> assertThat(e.getCode()).isEqualTo("MAP_TOO_SOON"))
                .hasMessageContaining("아직 이 맵을 다 돌지 못했어요");
        TowerService.View v = towerService.get(user);
        assertThat(v.secondsUntilClear()).isEqualTo(1);
        assertThat(v.satiety()).isEqualTo(100);   // 거절된 통과는 포만감을 쓰지 않는다
        assertThat(v.spentToday()).isZero();
        assertThat(v.currentMap()).isEqualTo(1);
        assertThat(v.onExpedition()).isTrue();

        pass(1); // 20초
        assertThat(towerService.get(user).secondsUntilClear()).isZero();
        v = towerService.clearMap(user);
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.result().spentSatiety()).isEqualTo(35);
        assertThat(v.satiety()).isEqualTo(65);
        assertThat(v.currentMap()).isEqualTo(2);
        assertThat(v.secondsUntilClear()).isEqualTo(MIN); // 다음 맵의 시계가 새로 돈다

        // 연달아 보내도 한 칸만 간다
        assertCode(() -> towerService.clearMap(user), "MAP_TOO_SOON");
        assertThat(towerService.get(user).currentMap()).isEqualTo(2);
        assertThat(towerService.get(user).satiety()).isEqualTo(65);
    }

    @Test
    @DisplayName("runId·층·맵을 보내는 새 앱 요청에도 최소 시간이 적용된다: 19초 거절, 20초 통과")
    void minimumTimeWithRunId() {
        User user = newUser(0, 100);
        String runId = towerService.depart(user, null).runId();
        pass(19);
        assertCode(() -> towerService.clearMap(user, runId, 1, 1), "MAP_TOO_SOON");
        pass(1);
        TowerService.View v = towerService.clearMap(user, runId, 1, 1);
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.currentMap()).isEqualTo(2);
        assertThat(v.runId()).isEqualTo(runId); // 원정 내내 같은 id
    }

    @Test
    @DisplayName("잘못된 runId는 거절한다(RUN_MISMATCH). 아무것도 바뀌지 않는다")
    void wrongRunIdRejected() {
        User user = newUser(0, 100);
        String runId = towerService.depart(user, null).runId();
        pass(MIN);

        assertCode(() -> towerService.clearMap(user, UUID.randomUUID().toString(), null, null), "RUN_MISMATCH");
        assertCode(() -> towerService.clearMap(user, "not-a-uuid", 1, 1), "RUN_MISMATCH");
        TowerService.View v = towerService.get(user);
        assertThat(v.satiety()).isEqualTo(100);
        assertThat(v.currentMap()).isEqualTo(1);
        assertThat(v.spentToday()).isZero();
        assertThat(v.runId()).isEqualTo(runId);

        assertThat(towerService.clearMap(user, runId, null, null).result().mapCleared()).isTrue();
    }

    @Test
    @DisplayName("층·맵은 서버의 지금 값과 같아야 한다(MAP_MISMATCH). 건너뛸 수 없고, 맞게 보내면 한 칸만 간다")
    void floorAndMapMustMatch() {
        User user = newUser(0, 100);
        String runId = towerService.depart(user, null).runId();
        pass(MIN);

        assertCode(() -> towerService.clearMap(user, runId, 1, 2), "MAP_MISMATCH");    // 맵을 건너뜀
        assertCode(() -> towerService.clearMap(user, runId, 1, 5), "MAP_MISMATCH");    // 마지막 맵으로 건너뜀
        assertCode(() -> towerService.clearMap(user, runId, 2, 1), "MAP_MISMATCH");    // 층을 건너뜀
        assertCode(() -> towerService.clearMap(user, null, 17, null), "MAP_MISMATCH"); // runId 없이 층만 틀림
        assertCode(() -> towerService.clearMap(user, null, null, 0), "MAP_MISMATCH");
        TowerService.View v = towerService.get(user);
        assertThat(v.currentFloor()).isEqualTo(1);
        assertThat(v.currentMap()).isEqualTo(1);
        assertThat(v.satiety()).isEqualTo(100);
        assertThat(v.highestFloor()).isZero();

        v = towerService.clearMap(user, runId, 1, 1);
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.currentFloor()).isEqualTo(1);
        assertThat(v.currentMap()).isEqualTo(2);

        // 방금 통과한 맵을 다시 보내면(중복 요청) 거절한다 — 시간이 지난 뒤에도
        pass(MIN);
        assertCode(() -> towerService.clearMap(user, runId, 1, 1), "MAP_MISMATCH");
        assertThat(towerService.clearMap(user, null, null, 2).currentMap()).isEqualTo(3); // 맵만 보내도 된다
    }

    @Test
    @DisplayName("배포 전에 떠난 원정(runId·맵 시각 없음): runId 없이 받고, 이번 한 번은 바로 통과시킨 뒤 시계를 시작한다")
    void legacyExpeditionInProgress() {
        User user = newUser(0, 100);
        towerService.depart(user, null);
        patchProgress(user, "runId", null);
        patchProgress(user, "mapEnteredAt", null);

        TowerService.View v = towerService.get(user);
        assertThat(v.onExpedition()).isTrue();
        assertThat(v.runId()).isNull();
        assertThat(v.secondsUntilClear()).isZero();
        assertCode(() -> towerService.clearMap(user, UUID.randomUUID().toString(), null, null), "RUN_MISMATCH");

        v = towerService.clearMap(user); // 기다리지 않아도 통과한다
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.currentMap()).isEqualTo(2);
        assertThat(v.satiety()).isEqualTo(65);
        assertThat(v.runId()).isNull();                   // 그 원정은 끝까지 runId 없이 간다
        assertThat(v.secondsUntilClear()).isEqualTo(MIN); // 이제 시계가 돈다
        assertThat(progress(user).getMapEnteredAt()).isNotNull();

        assertCode(() -> towerService.clearMap(user), "MAP_TOO_SOON");
        pass(19);
        assertCode(() -> towerService.clearMap(user), "MAP_TOO_SOON");
        pass(1);
        assertThat(towerService.clearMap(user).currentMap()).isEqualTo(3);
    }

    @Test
    @DisplayName("포만감·맵 수는 그대로: 20초씩 기다리며 5장을 통과하면 1층을 돌파하고 포만감 35씩 쓴다. 층이 바뀌어도 시계가 새로 돈다")
    void satietyAndMapCountUnchanged() {
        User user = newUser(0, 100);
        String runId = towerService.depart(user, 1).runId();
        TowerService.View v = clearMaps(user, 4);
        assertThat(v.currentFloor()).isEqualTo(1);
        assertThat(v.currentMap()).isEqualTo(5);
        assertThat(v.result().floorCleared()).isFalse();

        v = clearMaps(user, 1);
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.result().firstClear()).isTrue();
        assertThat(v.highestFloor()).isEqualTo(1);
        assertThat(v.currentFloor()).isEqualTo(2);
        assertThat(v.currentMap()).isEqualTo(1);
        assertThat(v.spentToday()).isEqualTo(35 * 5);
        assertThat(v.runId()).isEqualTo(runId);
        assertThat(v.secondsUntilClear()).isEqualTo(MIN);
        fillSatiety(user);
        assertCode(() -> towerService.clearMap(user, runId, 2, 1), "MAP_TOO_SOON");
    }

    @Test
    @DisplayName("포만감이 모자라면 시간이 지나도 예전처럼 거절(도시락 있음)하거나 원정이 끝난다(가방 빔). 끝나면 runId가 비워진다")
    void satietyRulesStillApply() {
        User withLunch = newUser(3, 100);
        lunchService.cook(withLunch, "lunchbox", 1);
        lunchService.pack(withLunch, "lunchbox", 1);
        towerService.depart(withLunch, null);
        pass(MIN);
        towerService.clearMap(withLunch); // 65
        pass(MIN);
        towerService.clearMap(withLunch); // 30, 가방에 도시락
        assertCode(() -> towerService.clearMap(withLunch), "MAP_TOO_SOON"); // 너무 이르면 시간 거절이 먼저다
        pass(MIN);
        assertCode(() -> towerService.clearMap(withLunch), "NOT_ENOUGH_SATIETY");
        assertThat(towerService.get(withLunch).onExpedition()).isTrue();
        assertThat(towerService.get(withLunch).satiety()).isEqualTo(30);

        User empty = newUser(0, 100);
        towerService.depart(empty, null);
        pass(MIN);
        towerService.clearMap(empty); // 65
        pass(MIN);
        TowerService.View v = towerService.clearMap(empty); // 30, 가방 빔
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.result().endReason()).isEqualTo(TowerService.SATIETY_EMPTY);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.runId()).isNull();
        assertThat(v.secondsUntilClear()).isNull();
        assertThat(progress(empty).getRunId()).isNull();
        assertThat(progress(empty).getMapEnteredAt()).isNull();
    }

    @Test
    @DisplayName("17층 상한은 그대로: 17층 마지막 맵을 통과하면 18층으로 가지 않고 원정이 끝나며 runId가 비워진다")
    void openFloorCapUnchanged() {
        User user = newUser(0, 100);
        towerService.get(user);
        patchProgress(user, "highestFloor", 16);
        assertCode(() -> towerService.depart(user, 18), "FLOOR_LOCKED");
        String runId = towerService.depart(user, 17).runId();
        int maps = TowerFloorCatalog.mapsPerFloor(17);

        clearMaps(user, maps - 1);
        pass(MIN);
        fillSatiety(user);
        assertCode(() -> towerService.clearMap(user, runId, 18, 1), "MAP_MISMATCH");
        TowerService.View v = towerService.clearMap(user, runId, 17, maps);
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.result().endReason()).isEqualTo(TowerService.TOP_FLOOR_CLEARED);
        assertThat(v.highestFloor()).isEqualTo(17);
        assertThat(v.currentFloor()).isEqualTo(17);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.runId()).isNull();
        assertCode(() -> towerService.clearMap(user, runId, null, null), "NOT_ON_EXPEDITION");
    }

    @Test
    @DisplayName("원정 제한 시간이 지나면 runId와 맵 시각이 비워진다 (가방·층·맵은 그대로)")
    void timeoutClearsRun() {
        User user = newUser(3, 100);
        lunchService.cook(user, "lunchbox", 1);
        lunchService.pack(user, "lunchbox", 1);
        String runId = towerService.depart(user, null).runId();
        pass(MIN);
        towerService.clearMap(user, runId, 1, 1);

        pass(60 * 60);
        TowerService.View v = towerService.clearMap(user, runId, 1, 2); // 만료가 먼저다: 거절이 아니라 귀가로 알린다
        assertThat(v.result().endReason()).isEqualTo(TowerService.TIME_UP);
        assertThat(v.result().mapCleared()).isFalse();
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.runId()).isNull();
        assertThat(v.secondsUntilClear()).isNull();
        assertThat(v.currentMap()).isEqualTo(2);
        assertThat(v.bagCount()).isEqualTo(1);
        assertThat(v.satiety()).isEqualTo(65);
        assertThat(progress(user).getRunId()).isNull();
        assertThat(progress(user).getMapEnteredAt()).isNull();
    }

    private ResultActions call(String bearer, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("API: 출발 응답에 runId·secondsUntilClear가 실리고, 이른 통과·잘못된 runId·층·맵은 500이 아니라 400 코드로 거절한다")
    void api() throws Exception {
        User user = newUser(0, 100);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);
        String legacy = "{\"tossUserId\":\"" + id + "\"}"; // 지금 운영 앱이 보내는 모양

        String runId = com.jayway.jsonpath.JsonPath.read(call(bearer, "/api/tower/depart", legacy)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onExpedition").value(true))
                .andExpect(jsonPath("$.runId").isString())
                .andExpect(jsonPath("$.secondsUntilClear").value(MIN))
                .andExpect(jsonPath("$.mapsPerFloor").value(5))          // 예전 필드는 그대로
                .andExpect(jsonPath("$.expeditionSecondsLeft").value(3600))
                .andExpect(jsonPath("$.recommendedBp").doesNotExist())   // 전투력 숫자는 싣지 않는다
                .andExpect(jsonPath("$.battlePower").doesNotExist())
                .andExpect(jsonPath("$.bp").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.runId");
        String full = "{\"tossUserId\":\"" + id + "\",\"runId\":\"" + runId + "\",\"floor\":1,\"map\":1}";

        call(bearer, "/api/tower/clear-map", legacy).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAP_TOO_SOON"))
                .andExpect(jsonPath("$.message").value("아직 이 맵을 다 돌지 못했어요."));
        pass(19);
        call(bearer, "/api/tower/clear-map", full).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAP_TOO_SOON"));
        pass(1);
        call(bearer, "/api/tower/clear-map", "{\"tossUserId\":\"" + id + "\",\"runId\":\"wrong\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RUN_MISMATCH"));
        call(bearer, "/api/tower/clear-map", "{\"tossUserId\":\"" + id + "\",\"runId\":12345}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RUN_MISMATCH"));
        call(bearer, "/api/tower/clear-map", "{\"tossUserId\":\"" + id + "\",\"runId\":\"" + runId + "\",\"floor\":1,\"map\":3}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MAP_MISMATCH"));
        call(bearer, "/api/tower/clear-map", "{\"tossUserId\":\"" + id + "\",\"floor\":2}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MAP_MISMATCH"));

        call(bearer, "/api/tower/clear-map", full).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.mapCleared").value(true))
                .andExpect(jsonPath("$.satiety").value(65))
                .andExpect(jsonPath("$.currentMap").value(2))
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.secondsUntilClear").value(MIN));

        pass(MIN);
        call(bearer, "/api/tower/clear-map", legacy).andExpect(status().isOk()) // 예전 앱 요청도 시간만 지나면 통과한다
                .andExpect(jsonPath("$.currentMap").value(3))
                .andExpect(jsonPath("$.result.expeditionEnded").value(true))    // 포만감 30, 가방 빔
                .andExpect(jsonPath("$.runId").value(nullValue()))
                .andExpect(jsonPath("$.secondsUntilClear").value(nullValue()));
    }
}
