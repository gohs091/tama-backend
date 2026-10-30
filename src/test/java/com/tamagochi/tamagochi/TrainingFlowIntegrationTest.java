package com.tamagochi.tamagochi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 앱(메모리 DB)에서 로그인 토큰 → 트레이닝 시작 → 제출 → 프로모션 기록까지 이어지는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrainingFlowIntegrationTest.FixedClockConfig.class)
class TrainingFlowIntegrationTest {

    /** 테스트에서 시간을 움직이는 시계 (10초를 실제로 기다리지 않기 위해) */
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-26T03:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @MockitoBean
    @SuppressWarnings("unused")
    StringRedisTemplate redisTemplate; // 포인트 중복 방지용 Redis는 테스트에서 쓰지 않는다 (앱이 주입받는 것은 StringRedisTemplate)

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;
    @Autowired AuthTokenService tokens;
    @Autowired UserService userService;
    @Autowired TamagotchiRepository tamagotchiRepository;

    private String userId;
    private String bearer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        userId = "toss_it_" + System.nanoTime();
        User user = userService.createUser(userId, "it");
        tamagotchiRepository.save(Tamagotchi.builder()
                .user(user).name("끄적이").evolutionStage(EvolutionStage.ADULT)
                .hunger(20).strength(80).build());
        bearer = "Bearer " + tokens.issue(userId);
    }

    private MvcResult startTraining() throws Exception {
        return mvc.perform(post("/api/tamagotchi/training/start")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tossUserId\":\"" + userId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions submit(String sessionId, int taps) throws Exception {
        return mvc.perform(post("/api/tamagotchi/training")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tossUserId\":\"" + userId + "\",\"sessionId\":\"" + sessionId
                        + "\",\"clickCount\":" + taps + ",\"idempotencyKey\":\"k-" + System.nanoTime() + "\"}"));
    }

    @Test
    @DisplayName("로그인 → 시작 → 너무 이른 제출은 거절 → 5초 뒤 제출은 성공하고 프로모션이 기록되며 → 같은 세션 재제출은 거절")
    void fullFlow() throws Exception {
        JsonNode started = objectMapper.readTree(startTraining().getResponse().getContentAsString());
        String sessionId = started.path("sessionId").asText();
        assertThat(sessionId).isNotBlank();
        assertThat(started.path("minSeconds").asLong()).isEqualTo(5);
        assertThat(started.path("enemy").path("key").asText()).isIn("crow", "dog", "person", "train"); // 스파링 상대를 시작할 때 정한다
        assertThat(started.path("enemy").path("taps").asInt()).isBetween(15, 40);

        submit(sessionId, 90).andExpect(status().isBadRequest()); // 아직 5초가 안 지남

        clock.now = clock.now.plusSeconds(11);
        submit(sessionId, 90).andExpect(status().isOk())
                .andExpect(jsonPath("$.promotion.result").value("RECORDED"))
                .andExpect(jsonPath("$.promotion.tier").value(2))
                .andExpect(jsonPath("$.promotion.amount").value(2))
                .andExpect(jsonPath("$.promotion.remainingToday").value(2))
                .andExpect(jsonPath("$.pendingExp").value(15)) // 완성작도 레벨은 계속 오른다 (탭 90번은 60번으로 잘린다 = 별 3개 = 기본 15, 광고 보면 15 더)
                .andExpect(jsonPath("$.mightGain").value(1))   // 트레이닝 한 판 = 위력 +1
                .andExpect(jsonPath("$.win").isBoolean())
                .andExpect(jsonPath("$.inEvolutionZone").value(false));

        submit(sessionId, 90).andExpect(status().isBadRequest()); // 같은 세션은 한 번만
    }

    @Test
    @DisplayName("프로모션 안내는 구간표와 오늘 남은 횟수를 알려주고, 남의 ID로는 볼 수 없다")
    void promotionInfo() throws Exception {
        mvc.perform(get("/api/promotion/training/" + userId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.remainingToday").value(3))
                .andExpect(jsonPath("$.tiers[0].minTaps").value(30))
                .andExpect(jsonPath("$.tiers[1].amount").value(2));

        // 한 번 대상이 되면 남은 횟수가 줄어든다
        String sessionId = objectMapper.readTree(startTraining().getResponse().getContentAsString()).path("sessionId").asText();
        clock.now = clock.now.plusSeconds(11);
        submit(sessionId, 90).andExpect(status().isOk());
        mvc.perform(get("/api/promotion/training/" + userId).header("Authorization", bearer))
                .andExpect(jsonPath("$.remainingToday").value(2));

        mvc.perform(get("/api/promotion/training/toss_someone_else").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("훈련하면 경험치가 바로 오르지 않고 대기하며, 광고를 본 뒤 받아야 오르고(레벨 업), 광고 보너스는 더 없고, 한 번만 받는다")
    void expIsClaimedAfterAd() throws Exception {
        // 이 테스트의 캐릭터를 낙서로 만든다 (기본 setUp은 완성작)
        var user = userService.createUser("toss_claim_" + System.nanoTime(), "claim");
        tamagotchiRepository.save(Tamagotchi.builder().user(user).name("끄적이")
                .evolutionStage(EvolutionStage.BABY).hunger(20).strength(80).build());
        String tossId = user.getTossUserId();
        String claimBearer = "Bearer " + tokens.issue(tossId);
        String body = "{\"tossUserId\":\"" + tossId + "\"}";

        // 훈련 한 판 (탭 90번 → 60번으로 잘림 = 별 3개): 기본 경험치 15를 바로 받고(이기면 +8), 광고로 15를 더 받을 수 있다
        String sessionId = objectMapper.readTree(mvc.perform(post("/api/tamagotchi/training/start")
                        .header("Authorization", claimBearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("sessionId").asText();
        clock.now = clock.now.plusSeconds(11);
        mvc.perform(post("/api/tamagotchi/training").header("Authorization", claimBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tossUserId\":\"" + tossId + "\",\"sessionId\":\"" + sessionId
                                + "\",\"clickCount\":90,\"idempotencyKey\":\"k-" + System.nanoTime() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingExp").value(15))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.evoGain").value(3)); // 업데이트 전 앱용 값
        mvc.perform(get("/api/tamagotchi/" + tossId).header("Authorization", claimBearer))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.pendingExp").value(15))
                .andExpect(jsonPath("$.evolutionLevel").value(6))
                .andExpect(jsonPath("$.weightDg").value(99))
                .andExpect(jsonPath("$.might").value(1));

        // 받기: 토큰 필요, 남의 ID는 403
        mvc.perform(post("/api/tamagotchi/evo-gauge/claim").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tamagotchi/evo-gauge/claim").header("Authorization", claimBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tossUserId\":\"toss_someone_else\"}"))
                .andExpect(status().isForbidden());

        // 광고를 보고 받으면 기본 경험치 15만 더 오른다 (30 이상 → Lv.1 → 2, 레벨 업 위력 +5)
        mvc.perform(post("/api/tamagotchi/evo-gauge/claim").header("Authorization", claimBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.might").value(6))
                .andExpect(jsonPath("$.pendingExp").value(0))
                .andExpect(jsonPath("$.evoGauge").value(2)); // 업데이트 전 앱용: 10칸 × 1/5

        // 같은 훈련의 경험치는 두 번 받을 수 없다
        mvc.perform(post("/api/tamagotchi/evo-gauge/claim").header("Authorization", claimBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("세션 없이 낸 결과는 거절한다")
    void withoutSession() throws Exception {
        submit("", 90).andExpect(status().isBadRequest());
        submit("not-a-session", 90).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰 없이는 401, 남의 ID로는 403")
    void authIsEnforced() throws Exception {
        mvc.perform(post("/api/tamagotchi/training/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tossUserId\":\"" + userId + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/tamagotchi/training/start")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tossUserId\":\"toss_someone_else\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 엔드포인트는 토큰 없이 부를 수 있다 (코드가 없으면 401)")
    void loginEndpointIsOpen() throws Exception {
        mvc.perform(post("/api/auth/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isUnauthorized());
    }
}
