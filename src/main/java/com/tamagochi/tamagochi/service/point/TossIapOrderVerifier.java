package com.tamagochi.tamagochi.service.point;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.service.toss.TossPartnerApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 앱인토스 서버 API(주문 상태 조회)로 결제 여부를 확인한다.
 * 지급 시점의 주문은 PAYMENT_COMPLETED, 지급 완료 처리 뒤에는 PURCHASED 이므로 둘 다 결제된 주문으로 본다.
 * 같은 orderId의 중복 지급은 PointService가 막는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tamagotchi.iap.verify", havingValue = "toss", matchIfMissing = true)
public class TossIapOrderVerifier implements IapOrderVerifier {

    private static final String ORDER_STATUS_PATH = "/api-partner/v1/apps-in-toss/order/get-order-status";
    private static final Set<String> PAID_STATUSES = Set.of("PAYMENT_COMPLETED", "PURCHASED");

    private final TossPartnerApiClient tossClient;

    @Override
    public boolean isPaid(String orderId, String sku) {
        return isPaidResponse(tossClient.post(ORDER_STATUS_PATH, Map.of("orderId", orderId)), sku);
    }

    /** 응답이 SUCCESS 이고, 결제 완료 상태이며, 주문한 상품이 요청한 sku와 같을 때만 true */
    static boolean isPaidResponse(JsonNode root, String sku) {
        if (!"SUCCESS".equals(root.path("resultType").asText())) return false;
        JsonNode success = root.path("success");
        return PAID_STATUSES.contains(success.path("status").asText())
                && sku.equals(success.path("sku").asText());
    }
}
