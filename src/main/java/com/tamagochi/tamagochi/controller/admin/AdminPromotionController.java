package com.tamagochi.tamagochi.controller.admin;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.ForbiddenException;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.promotion.PromotionGranter;
import com.tamagochi.tamagochi.service.promotion.PromotionService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 토스 프로모션 지급 API 테스트용 관리자 엔드포인트. 콘솔은 지급 API 테스트를 1회 이상 해야 프로모션을 시작할 수 있게 한다.
 * 토스 앱 안의 사용자 키가 있어야 해서, 한 번 로그인한 계정의 ID로 테스트 지급을 직접 일으킨다.
 *
 * 안전장치: tamagotchi.admin.key(ADMIN_KEY)를 설정해야 만들어지고, X-Admin-Key 헤더가 맞아야 하며,
 * 프로모션 코드가 TEST_ 로 시작할 때만 동작한다(실제 포인트가 지급·차감되지 않는 코드).
 */
@RestController
@RequestMapping("/api/admin/promotion")
@RequiredArgsConstructor
@ConditionalOnExpression("!'${tamagotchi.admin.key:}'.isEmpty()")
public class AdminPromotionController {

    private static final int MAX_TEST_AMOUNT = 5;

    private final PromotionService promotionService;
    private final PromotionGranter granter;
    private final PromotionGrantRepository grantRepository;
    private final UserRepository userRepository;

    @Value("${tamagotchi.admin.key:}")
    private String adminKey;

    @Getter
    @NoArgsConstructor
    public static class TestGrantRequest {
        private String tossUserId;
        private int amount;
        /** "training"(기본) 또는 "evolution": 어느 프로모션 코드로 테스트할지 */
        private String kind;
    }

    @PostMapping("/test-grant")
    public ResponseEntity<?> testGrant(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                       @RequestBody TestGrantRequest request) {
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), adminKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("관리자 키가 필요해요.");
        }
        boolean evolution = "evolution".equalsIgnoreCase(request.getKind());
        boolean testable = evolution ? granter.isEvolutionConfigured() && granter.isEvolutionTestCode() : granter.isConfigured() && granter.isTestCode();
        if (!testable) {
            throw new IllegalStateException("프로모션 코드가 TEST_ 로 시작하는 테스트 코드일 때만 쓸 수 있어요.");
        }
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다. 토스 앱에서 한 번 로그인한 계정이어야 해요."));
        int amount = request.getAmount() <= 0 ? 1 : Math.min(request.getAmount(), MAX_TEST_AMOUNT);

        PromotionGrant grant = evolution ? promotionService.recordEvolutionTest(user, amount) : promotionService.recordTest(user, amount);
        granter.grant(grant.getId()); // 토스 호출까지 이 요청에서 바로 처리한다
        PromotionGrant after = grantRepository.findById(grant.getId()).orElse(grant);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", evolution ? "evolution" : "training");
        body.put("grantId", after.getId());
        body.put("status", after.getStatus());     // GRANTED(또는 PENDING)이면 지급 API가 정상 응답한 것
        body.put("amount", after.getAmount());
        body.put("attempts", after.getAttempts());
        body.put("lastError", after.getLastError());
        body.put("hasGrantKey", after.getGrantKey() != null);
        return ResponseEntity.ok(body);
    }
}
