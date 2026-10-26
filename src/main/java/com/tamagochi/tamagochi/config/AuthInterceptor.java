package com.tamagochi.tamagochi.config;

import com.tamagochi.tamagochi.exception.UnauthorizedException;
import com.tamagochi.tamagochi.service.auth.AuthSettings;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** /api/** 요청에 유효한 로그인 토큰(Authorization: Bearer ...)이 있는지 확인하고 토큰 주인을 요청에 담는다. */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final AuthSettings authSettings;
    private final AuthTokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!authSettings.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : null;
        String userId = tokenService.verify(token)
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요해요."));
        request.setAttribute(UserIdentity.AUTH_USER_ATTRIBUTE, userId);
        return true;
    }
}
