package com.tamagochi.tamagochi.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.exception.UnauthorizedException;
import com.tamagochi.tamagochi.service.toss.TossPartnerApiClient;
import com.tamagochi.tamagochi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 토스 앱에서 받은 일회용 코드를 토스 서버에서 사용자 식별키(anonKey)로 교환해 로그인시킨다.
 * 사용자 ID는 클라이언트가 보낸 값이 아니라 이 교환 결과로만 정해진다.
 */
@Service
@RequiredArgsConstructor
public class TossAuthService {

    private static final String EXCHANGE_PATH = "/api-partner/v1/apps-in-toss/users/anon-key/exchange";

    private final TossPartnerApiClient tossClient;
    private final UserService userService;
    private final AuthTokenService tokenService;
    private final AuthSettings authSettings;

    public record LoginResult(String tossUserId, String token, long expiresInSeconds) {
    }

    public LoginResult login(String code) {
        if (!authSettings.isEnabled()) {
            throw new IllegalStateException("로그인이 꺼져 있어요.");
        }
        if (code == null || code.isBlank()) {
            throw new UnauthorizedException("로그인 코드가 없어요.");
        }
        String anonKey = extractAnonKey(tossClient.post(EXCHANGE_PATH, Map.of("code", code)));
        String tossUserId = userIdFromAnonKey(anonKey);
        userService.createUser(tossUserId, tossUserId);
        return new LoginResult(tossUserId, tokenService.issue(tossUserId), tokenService.getTtlSeconds());
    }

    /** 기존 사용자 ID 형식(toss_해시)을 유지한다. 토스가 주는 익명키는 SDK getAnonymousKey의 hash와 같은 값이다. */
    static String userIdFromAnonKey(String anonKey) {
        return "toss_" + anonKey;
    }

    static String extractAnonKey(JsonNode root) {
        if (!"SUCCESS".equals(root.path("resultType").asText())) {
            throw new UnauthorizedException("로그인에 실패했어요.");
        }
        String anonKey = root.path("success").path("anonKey").asText("");
        if (anonKey.isBlank()) {
            throw new UnauthorizedException("로그인에 실패했어요.");
        }
        return anonKey;
    }
}
