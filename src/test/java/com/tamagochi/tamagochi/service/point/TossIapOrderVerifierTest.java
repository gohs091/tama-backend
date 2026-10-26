package com.tamagochi.tamagochi.service.point;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TossIapOrderVerifierTest {

    private static boolean paid(String json, String sku) throws Exception {
        return TossIapOrderVerifier.isPaidResponse(new ObjectMapper().readTree(json), sku);
    }

    private static String ok(String status, String sku) {
        return "{\"resultType\":\"SUCCESS\",\"success\":{\"orderId\":\"o1\",\"status\":\"" + status + "\",\"sku\":\"" + sku + "\"}}";
    }

    @Test
    @DisplayName("결제 완료·구매 완료 주문은 같은 상품일 때만 인정한다")
    void paidStatuses() throws Exception {
        assertThat(paid(ok("PAYMENT_COMPLETED", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797"), "ait.0000032667.c26f773b.ab0e80b6a6.3031873797")).isTrue();
        assertThat(paid(ok("PURCHASED", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797"), "ait.0000032667.c26f773b.ab0e80b6a6.3031873797")).isTrue();
    }

    @Test
    @DisplayName("다른 상품으로 결제한 주문은 거절한다 (싼 상품 결제로 비싼 상품 요청 방지)")
    void skuMismatch() throws Exception {
        assertThat(paid(ok("PURCHASED", "ait.0000032667.a43f662f.e3e8089345.3031847883"), "ait.0000032667.d39fae7b.06eeca7735.3031999667")).isFalse();
    }

    @Test
    @DisplayName("진행 중·실패·환불·다른 앱 주문은 거절한다")
    void notPaidStatuses() throws Exception {
        for (String status : new String[]{"ORDER_IN_PROGRESS", "FAILED", "REFUNDED", "NOT_FOUND", "MINIAPP_MISMATCH", "ERROR"}) {
            assertThat(paid(ok(status, "ait.0000032667.c26f773b.ab0e80b6a6.3031873797"), "ait.0000032667.c26f773b.ab0e80b6a6.3031873797")).as(status).isFalse();
        }
    }

    @Test
    @DisplayName("resultType이 SUCCESS가 아니면 거절한다")
    void failResult() throws Exception {
        assertThat(paid("{\"resultType\":\"FAIL\",\"success\":null,\"error\":{\"errorCode\":\"4095\"}}", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797")).isFalse();
        assertThat(paid("{}", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797")).isFalse();
    }
}
