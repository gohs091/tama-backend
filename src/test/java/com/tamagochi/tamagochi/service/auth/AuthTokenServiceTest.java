package com.tamagochi.tamagochi.service.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenServiceTest {

    private static final byte[] SECRET = "test-secret-key-0123456789".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    private static AuthTokenService service(byte[] secret, long ttl, Instant now) {
        return new AuthTokenService(secret, ttl, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("발급한 토큰은 검증하면 같은 사용자를 돌려준다")
    void roundTrip() {
        AuthTokenService s = service(SECRET, 3600, NOW);
        String token = s.issue("toss_abc");
        assertThat(s.verify(token)).contains("toss_abc");
    }

    @Test
    @DisplayName("만료된 토큰은 거절한다")
    void expired() {
        String token = service(SECRET, 3600, NOW).issue("toss_abc");
        assertThat(service(SECRET, 3600, NOW.plusSeconds(3599)).verify(token)).contains("toss_abc");
        assertThat(service(SECRET, 3600, NOW.plusSeconds(3600)).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("내용을 바꾸면(다른 사용자로 위조하면) 서명이 맞지 않아 거절한다")
    void tamperedPayload() {
        AuthTokenService s = service(SECRET, 3600, NOW);
        String[] parts = s.issue("toss_abc").split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"toss_victim\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        assertThat(s.verify(forgedPayload + "." + parts[1])).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거절한다")
    void wrongKey() {
        String token = service("another-secret-key".getBytes(StandardCharsets.UTF_8), 3600, NOW).issue("toss_abc");
        assertThat(service(SECRET, 3600, NOW).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("형식이 이상하거나 비어 있는 토큰은 거절한다")
    void malformed() {
        AuthTokenService s = service(SECRET, 3600, NOW);
        assertThat(s.verify(null)).isEmpty();
        assertThat(s.verify("")).isEmpty();
        assertThat(s.verify("abc")).isEmpty();
        assertThat(s.verify(".")).isEmpty();
        assertThat(s.verify("a.")).isEmpty();
        assertThat(s.verify(".b")).isEmpty();
        assertThat(s.verify("!!!.@@@")).isEmpty();
    }
}
