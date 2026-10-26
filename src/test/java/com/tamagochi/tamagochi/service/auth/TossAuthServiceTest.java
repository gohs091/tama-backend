package com.tamagochi.tamagochi.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.exception.UnauthorizedException;
import com.tamagochi.tamagochi.service.toss.TossPartnerApiClient;
import com.tamagochi.tamagochi.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TossAuthServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PATH = "/api-partner/v1/apps-in-toss/users/anon-key/exchange";

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    @Test
    @DisplayName("교환 응답에서 anonKey를 꺼내고 기존 형식(toss_해시)의 사용자 ID를 만든다")
    void extractsAnonKey() throws Exception {
        String key = TossAuthService.extractAnonKey(json("{\"resultType\":\"SUCCESS\",\"success\":{\"anonKey\":\"abc123\"}}"));
        assertThat(key).isEqualTo("abc123");
        assertThat(TossAuthService.userIdFromAnonKey(key)).isEqualTo("toss_abc123");
    }

    @Test
    @DisplayName("실패 응답이나 anonKey가 없는 응답은 로그인 실패로 본다")
    void failedExchange() throws Exception {
        assertThatThrownBy(() -> TossAuthService.extractAnonKey(
                json("{\"resultType\":\"FAIL\",\"success\":null,\"error\":{\"errorCode\":\"4011\"}}")))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> TossAuthService.extractAnonKey(json("{\"resultType\":\"SUCCESS\",\"success\":{}}")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("코드 교환에 성공하면 사용자를 만들고 토큰을 발급한다")
    void loginSuccess() throws Exception {
        TossPartnerApiClient client = mock(TossPartnerApiClient.class);
        UserService userService = mock(UserService.class);
        AuthTokenService tokens = mock(AuthTokenService.class);
        when(client.post(eq(PATH), any())).thenReturn(json("{\"resultType\":\"SUCCESS\",\"success\":{\"anonKey\":\"k1\"}}"));
        when(tokens.issue("toss_k1")).thenReturn("TOKEN");
        when(tokens.getTtlSeconds()).thenReturn(43200L);
        TossAuthService service = new TossAuthService(client, userService, tokens, new AuthSettings(true));

        TossAuthService.LoginResult result = service.login("one-time-code");

        assertThat(result.tossUserId()).isEqualTo("toss_k1");
        assertThat(result.token()).isEqualTo("TOKEN");
        assertThat(result.expiresInSeconds()).isEqualTo(43200L);
        verify(client).post(PATH, Map.of("code", "one-time-code"));
        verify(userService).createUser("toss_k1", "toss_k1");
    }

    @Test
    @DisplayName("코드가 비었거나 로그인이 꺼져 있으면 토스 서버를 부르지 않는다")
    void rejectsWithoutCallingToss() {
        TossPartnerApiClient client = mock(TossPartnerApiClient.class);
        AuthTokenService tokens = mock(AuthTokenService.class);
        TossAuthService enabled = new TossAuthService(client, mock(UserService.class), tokens, new AuthSettings(true));
        TossAuthService disabled = new TossAuthService(client, mock(UserService.class), tokens, new AuthSettings(false));

        assertThatThrownBy(() -> enabled.login(" ")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> enabled.login(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> disabled.login("code")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(client);
    }
}
