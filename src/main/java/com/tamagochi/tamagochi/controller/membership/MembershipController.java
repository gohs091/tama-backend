package com.tamagochi.tamagochi.controller.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.membership.PainterMembershipService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 화가 회원(정기결제): 상태 조회, 결제 직후 지급, 토스 웹훅 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MembershipController {

    private final PainterMembershipService membershipService;
    private final UserIdentity userIdentity;

    @GetMapping("/api/membership/{tossUserId}")
    public ResponseEntity<?> status(@PathVariable String tossUserId) {
        return ResponseEntity.ok(membershipService.status(userIdentity.resolve(tossUserId)));
    }

    @Data
    public static class GrantRequest {
        private String tossUserId;
        private String orderId;
        private String sku;
    }

    /** 앱의 processProductGrant에서 부른다. 토스에 결제를 확인하고 회원으로 기록한다 */
    @PostMapping("/api/membership/grant")
    public ResponseEntity<?> grant(@RequestBody GrantRequest request) {
        return ResponseEntity.ok(membershipService.grant(userIdentity.resolve(request.getTossUserId()), request.getOrderId(), request.getSku()));
    }

    /**
     * 토스 콘솔에 등록하는 결제 알림(콜백) URL: https://{서버}/api/iap-webhook
     * 로그인 없이 토스 서버가 부른다(AuthWebConfig에서 제외). 항상 200으로 받아서 토스가 다시 보내지 않게 한다.
     */
    @PostMapping("/api/iap-webhook")
    public ResponseEntity<?> webhook(@RequestBody JsonNode body) {
        try {
            membershipService.onWebhook(body);
        } catch (Exception e) {
            log.error("토스 웹훅 처리 실패 - {}", body, e);
        }
        return ResponseEntity.ok(Map.of("result", "ok"));
    }
}
