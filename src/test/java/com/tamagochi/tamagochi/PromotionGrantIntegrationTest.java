package com.tamagochi.tamagochi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.toss.TossPromotionApi;
import com.tamagochi.tamagochi.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * grant 모드에서 트레이닝 한 판이 커밋된 뒤 다른 스레드에서 토스 지급이 일어나 GRANTED가 되는지,
 * 앱 전체 연결(이벤트 → 리스너 → 지급 서비스)로 확인한다. 토스 API는 목으로 대신한다.
 */
@SpringBootTest(properties = {
        "tamagotchi.promotion.mode=grant",
        "tamagotchi.promotion.code=TEST_CODE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrainingFlowIntegrationTest.FixedClockConfig.class)
class PromotionGrantIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @MockitoBean StringRedisTemplate redisTemplate;
    @MockitoBean TossPromotionApi tossApi;

    @Autowired MockMvc mvc;
    @Autowired TrainingFlowIntegrationTest.MutableClock clock;
    @Autowired AuthTokenService tokens;
    @Autowired UserService userService;
    @Autowired TamagotchiRepository tamagotchiRepository;
    @Autowired PromotionGrantRepository grantRepository;

    private String userId;
    private Long userDbId;
    private String bearer;

    private static TossPromotionApi.Response response(String json) throws Exception {
        return TossPromotionApi.Response.parse(MAPPER.readTree(json));
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        when(tossApi.getKey()).thenReturn(response("{\"resultType\":\"SUCCESS\",\"success\":{\"key\":\"KEY-1\"}}"));
        when(tossApi.execute(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(response("{\"resultType\":\"SUCCESS\",\"success\":{\"key\":\"KEY-1\"}}"));
        when(tossApi.result(anyString(), anyString(), anyString()))
                .thenReturn(response("{\"resultType\":\"SUCCESS\",\"success\":\"SUCCESS\"}"));

        userId = "toss_grant_" + System.nanoTime();
        User user = userService.createUser(userId, "g");
        userDbId = user.getId();
        tamagotchiRepository.save(Tamagotchi.builder()
                .user(user).name("끄적이").evolutionStage(EvolutionStage.ADULT)
                .hunger(20).strength(80).build());
        bearer = "Bearer " + tokens.issue(userId);
    }

    private String json(String body) {
        return body.replace("USER", userId);
    }

    @Test
    @DisplayName("대상이 된 트레이닝은 커밋 뒤 비동기로 토스에 지급되어 GRANTED가 된다")
    void grantsAfterCommit() throws Exception {
        String sessionId = MAPPER.readTree(mvc.perform(post("/api/tamagotchi/training/start")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"tossUserId\":\"USER\"}")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("sessionId").asText();

        clock.now = clock.now.plusSeconds(11);
        mvc.perform(post("/api/tamagotchi/training")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"tossUserId\":\"USER\",\"sessionId\":\"" + sessionId
                                + "\",\"clickCount\":85,\"idempotencyKey\":\"k-" + System.nanoTime() + "\"}")))
                .andExpect(status().isOk());

        // 사용자 키는 우리 ID에서 toss_ 를 뗀 값, 프로모션 코드와 금액(2원)을 맞게 보낸다
        verify(tossApi, timeout(5000)).execute(eq(userId.substring("toss_".length())), eq("TEST_CODE"), eq("KEY-1"), eq(2));

        PromotionGrant grant = awaitStatus(PromotionGrantStatus.GRANTED);
        assertThat(grant.getAmount()).isEqualTo(2);
        assertThat(grant.getGrantKey()).isEqualTo("KEY-1");
        assertThat(grant.getAttempts()).isZero();
    }

    private PromotionGrant awaitStatus(PromotionGrantStatus expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<PromotionGrant> all = grantRepository.findAll();
            for (PromotionGrant g : all) {
                if (g.getUser().getId().equals(userDbId) && g.getStatus() == expected) return g;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("지급 상태가 " + expected + " 로 바뀌지 않았어요");
    }
}
