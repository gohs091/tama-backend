package com.tamagochi.tamagochi.config;

import com.tamagochi.tamagochi.controller.point.PointController;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.GlobalExceptionHandler;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.auth.AuthSettings;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.point.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 인터셉터 + 컨트롤러 + 예외 처리가 실제 요청 경로에서 이어지는지 확인한다 (DB 없이). */
class AuthFlowTest {

    private AuthTokenService tokens;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tokens = new AuthTokenService("flow-test-secret", 3600);

        UserRepository userRepository = mock(UserRepository.class);
        User me = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();
        when(userRepository.findByTossUserId("toss_me")).thenReturn(Optional.of(me));
        when(userRepository.findByTossUserId("toss_other"))
                .thenReturn(Optional.of(User.builder().id(2L).tossUserId("toss_other").nickname("o").build()));

        PointService pointService = mock(PointService.class);
        when(pointService.getWallet(me)).thenReturn(PointWallet.builder().user(me).balance(1234).build());

        AuthSettings settings = new AuthSettings(true);
        UserIdentity identity = new UserIdentity(userRepository, settings);
        mvc = MockMvcBuilders.standaloneSetup(new PointController(pointService, identity))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addMappedInterceptors(new String[]{"/api/**"}, new AuthInterceptor(settings, tokens))
                .build();
    }

    @Test
    @DisplayName("토큰 없이 부르면 401")
    void noToken() throws Exception {
        mvc.perform(get("/api/point/balance/toss_me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("위조된 토큰이면 401")
    void forgedToken() throws Exception {
        mvc.perform(get("/api/point/balance/toss_me").header("Authorization", "Bearer abc.def"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("자기 토큰으로 자기 데이터를 읽으면 200")
    void ownData() throws Exception {
        mvc.perform(get("/api/point/balance/toss_me").header("Authorization", "Bearer " + tokens.issue("toss_me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1234));
    }

    @Test
    @DisplayName("자기 토큰으로 남의 ID를 넣으면 403 (ID만 바꿔서 남의 데이터를 읽는 것을 막는다)")
    void otherUsersData() throws Exception {
        mvc.perform(get("/api/point/balance/toss_other").header("Authorization", "Bearer " + tokens.issue("toss_me")))
                .andExpect(status().isForbidden());
    }
}
