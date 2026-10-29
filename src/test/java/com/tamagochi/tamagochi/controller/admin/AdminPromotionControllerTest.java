package com.tamagochi.tamagochi.controller.admin;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.GlobalExceptionHandler;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.promotion.PromotionGranter;
import com.tamagochi.tamagochi.service.promotion.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPromotionControllerTest {

    private PromotionService promotionService;
    private PromotionGranter granter;
    private PromotionGrantRepository grantRepository;
    private UserRepository userRepository;
    private MockMvc mvc;
    private PromotionGrant grant;

    @BeforeEach
    void setUp() {
        promotionService = mock(PromotionService.class);
        granter = mock(PromotionGranter.class);
        grantRepository = mock(PromotionGrantRepository.class);
        userRepository = mock(UserRepository.class);

        User user = User.builder().id(1L).tossUserId("toss_abc").nickname("n").build();
        when(userRepository.findByTossUserId("toss_abc")).thenReturn(Optional.of(user));
        grant = new PromotionGrant(user, "admin-test-1", LocalDate.of(1970, 1, 1), 0, 0, 1);
        ReflectionTestUtils.setField(grant, "id", 7L);
        when(promotionService.recordTest(eq(user), anyInt())).thenReturn(grant);
        when(grantRepository.findById(7L)).thenReturn(Optional.of(grant));
        when(granter.isConfigured()).thenReturn(true);
        when(granter.isTestCode()).thenReturn(true);

        AdminPromotionController controller =
                new AdminPromotionController(promotionService, granter, grantRepository, userRepository);
        ReflectionTestUtils.setField(controller, "adminKey", "secret-admin-key");
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private org.springframework.test.web.servlet.ResultActions call(String key, String body) throws Exception {
        var req = post("/api/admin/promotion/test-grant").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) req = req.header("X-Admin-Key", key);
        return mvc.perform(req);
    }

    @Test
    @DisplayName("관리자 키와 TEST_ 코드가 맞으면 테스트 지급을 일으키고 결과 상태를 돌려준다")
    void testGrant() throws Exception {
        grant.markGranted();

        call("secret-admin-key", "{\"tossUserId\":\"toss_abc\",\"amount\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"))
                .andExpect(jsonPath("$.grantId").value(7));

        verify(granter).grant(7L);
    }

    @Test
    @DisplayName("kind=evolution 이면 진화용 TEST_ 코드로 테스트 지급을 일으킨다")
    void evolutionKind() throws Exception {
        User user = User.builder().id(1L).tossUserId("toss_abc").nickname("n").build();
        PromotionGrant evo = PromotionGrant.evolutionTest(user, LocalDate.of(1970, 1, 1), 1);
        ReflectionTestUtils.setField(evo, "id", 8L);
        evo.markGranted();
        when(granter.isEvolutionConfigured()).thenReturn(true);
        when(granter.isEvolutionTestCode()).thenReturn(true);
        when(promotionService.recordEvolutionTest(any(User.class), anyInt())).thenReturn(evo);
        when(grantRepository.findById(8L)).thenReturn(Optional.of(evo));

        call("secret-admin-key", "{\"tossUserId\":\"toss_abc\",\"amount\":1,\"kind\":\"evolution\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("evolution"))
                .andExpect(jsonPath("$.status").value("GRANTED"));

        verify(granter).grant(8L);
    }

    @Test
    @DisplayName("kind=evolution 인데 진화 코드가 TEST_ 가 아니면 거절한다")
    void evolutionRealCodeRejected() throws Exception {
        when(granter.isEvolutionConfigured()).thenReturn(true);
        when(granter.isEvolutionTestCode()).thenReturn(false);

        call("secret-admin-key", "{\"tossUserId\":\"toss_abc\",\"kind\":\"evolution\"}").andExpect(status().isBadRequest());
        verify(granter, never()).grant(any());
    }

    @Test
    @DisplayName("관리자 키가 없거나 틀리면 403이고 지급을 시도하지 않는다")
    void wrongKey() throws Exception {
        call(null, "{\"tossUserId\":\"toss_abc\"}").andExpect(status().isForbidden());
        call("wrong", "{\"tossUserId\":\"toss_abc\"}").andExpect(status().isForbidden());
        verify(granter, never()).grant(any());
    }

    @Test
    @DisplayName("실제 프로모션 코드(TEST_ 가 아님)일 때는 거절한다")
    void realCodeRejected() throws Exception {
        when(granter.isTestCode()).thenReturn(false);

        call("secret-admin-key", "{\"tossUserId\":\"toss_abc\"}").andExpect(status().isBadRequest());
        verify(granter, never()).grant(any());
    }

    @Test
    @DisplayName("없는 유저는 거절한다")
    void unknownUser() throws Exception {
        call("secret-admin-key", "{\"tossUserId\":\"toss_nobody\"}").andExpect(status().isBadRequest());
        verify(granter, never()).grant(any());
    }

    @Test
    @DisplayName("테스트 금액은 최대 5원으로 제한한다")
    void amountIsCapped() throws Exception {
        grant.markFailed("x");
        call("secret-admin-key", "{\"tossUserId\":\"toss_abc\",\"amount\":9999}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(PromotionGrantStatus.FAILED.name()));
        verify(promotionService).recordTest(any(User.class), eq(5));
    }
}
