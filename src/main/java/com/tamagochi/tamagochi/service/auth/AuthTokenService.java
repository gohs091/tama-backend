package com.tamagochi.tamagochi.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * 로그인 후 발급하는 서명 토큰. 형식: base64url(payload).base64url(HMAC-SHA256).
 * payload는 {"sub": tossUserId, "exp": 만료 epoch 초}. 서명 키는 tamagotchi.auth.token-secret(환경변수 AUTH_TOKEN_SECRET)이고,
 * 비어 있으면 서버가 켜질 때마다 새로 만든다(재시작하면 기존 토큰이 무효가 되고, 앱이 자동으로 다시 로그인한다).
 */
@Component
@Slf4j
public class AuthTokenService {

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long ttlSeconds;
    private final Clock clock;

    @Autowired
    public AuthTokenService(@Value("${tamagotchi.auth.token-secret:}") String configuredSecret,
                            @Value("${tamagotchi.auth.token-ttl-seconds:43200}") long ttlSeconds) {
        this(resolveSecret(configuredSecret), ttlSeconds, Clock.systemUTC());
    }

    AuthTokenService(byte[] secret, long ttlSeconds, Clock clock) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    private static byte[] resolveSecret(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        log.warn("tamagotchi.auth.token-secret 이 비어 있어 임시 키를 만듭니다. 서버를 재시작하면 기존 토큰이 무효가 됩니다.");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return random;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String issue(String tossUserId) {
        try {
            long exp = clock.instant().getEpochSecond() + ttlSeconds;
            String payload = ENCODER.encodeToString(
                    objectMapper.writeValueAsBytes(Map.of("sub", tossUserId, "exp", exp)));
            return payload + "." + ENCODER.encodeToString(sign(payload));
        } catch (Exception e) {
            throw new IllegalStateException("토큰을 만들지 못했습니다.", e);
        }
    }

    /** 서명이 맞고 만료되지 않았으면 토큰 주인(tossUserId)을 돌려준다. */
    public Optional<String> verify(String token) {
        if (token == null) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Optional.empty();
        String payload = token.substring(0, dot);
        try {
            byte[] given = DECODER.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(sign(payload), given)) return Optional.empty();
            JsonNode node = objectMapper.readTree(DECODER.decode(payload));
            String sub = node.path("sub").asText("");
            if (sub.isBlank() || node.path("exp").asLong(0) <= clock.instant().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(sub);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String payload) throws Exception {
        Mac mac = Mac.getInstance(HMAC);
        mac.init(new SecretKeySpec(secret, HMAC));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }
}
