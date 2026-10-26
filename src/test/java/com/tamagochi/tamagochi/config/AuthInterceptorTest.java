package com.tamagochi.tamagochi.config;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.ForbiddenException;
import com.tamagochi.tamagochi.exception.UnauthorizedException;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.auth.AuthSettings;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private final AuthTokenService tokens = mock(AuthTokenService.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MockHttpServletRequest request(String method, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/tamagotchi/feed");
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }

    @Test
    @DisplayName("유효한 토큰이면 통과하고 토큰 주인을 요청에 담는다")
    void validToken() {
        when(tokens.verify("good")).thenReturn(Optional.of("toss_me"));
        AuthInterceptor interceptor = new AuthInterceptor(new AuthSettings(true), tokens);
        MockHttpServletRequest request = request("POST", "Bearer good");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute(UserIdentity.AUTH_USER_ATTRIBUTE)).isEqualTo("toss_me");
    }

    @Test
    @DisplayName("토큰이 없거나 틀리면 401")
    void missingOrInvalidToken() {
        when(tokens.verify("bad")).thenReturn(Optional.empty());
        when(tokens.verify(null)).thenReturn(Optional.empty());
        AuthInterceptor interceptor = new AuthInterceptor(new AuthSettings(true), tokens);

        assertThatThrownBy(() -> interceptor.preHandle(request("POST", null), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> interceptor.preHandle(request("POST", "Bearer bad"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> interceptor.preHandle(request("POST", "Basic xyz"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("CORS 사전 요청(OPTIONS)과 로그인이 꺼진 환경은 토큰 없이 통과한다")
    void bypass() {
        AuthInterceptor on = new AuthInterceptor(new AuthSettings(true), tokens);
        AuthInterceptor off = new AuthInterceptor(new AuthSettings(false), tokens);

        assertThat(on.preHandle(request("OPTIONS", null), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(off.preHandle(request("POST", null), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    @DisplayName("요청의 사용자 ID가 토큰 주인과 같을 때만 사용자를 돌려준다")
    void identityMustMatchToken() {
        User me = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();
        when(userRepository.findByTossUserId("toss_me")).thenReturn(Optional.of(me));
        UserIdentity identity = new UserIdentity(userRepository, new AuthSettings(true));

        MockHttpServletRequest request = request("POST", null);
        request.setAttribute(UserIdentity.AUTH_USER_ATTRIBUTE, "toss_me");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(identity.resolve("toss_me")).isSameAs(me);
        assertThatThrownBy(() -> identity.resolve("toss_other")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("토큰 정보가 없으면(로그인 안 한 요청) 어떤 ID도 거절한다")
    void identityWithoutLogin() {
        UserIdentity identity = new UserIdentity(userRepository, new AuthSettings(true));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request("POST", null)));

        assertThatThrownBy(() -> identity.requireSame("toss_me")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("로그인이 꺼진 환경에서는 요청의 ID를 그대로 쓴다")
    void identityWhenAuthDisabled() {
        User me = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();
        when(userRepository.findByTossUserId("toss_me")).thenReturn(Optional.of(me));
        UserIdentity identity = new UserIdentity(userRepository, new AuthSettings(false));

        assertThat(identity.resolve("toss_me")).isSameAs(me);
    }
}
