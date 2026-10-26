package com.tamagochi.tamagochi.service.toss;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 앱인토스 프로모션(토스 포인트) 서버 API: 지급 키 발급(get-key), 지급(execute-promotion), 결과 조회(execution-result).
 * 비즈니스 오류도 HTTP 200으로 오기 때문에 resultType을 보고 판단한다.
 */
@Component
@RequiredArgsConstructor
public class TossPromotionApi {

    private static final String BASE = "/api-partner/v1/apps-in-toss/promotion";
    private static final String ANON_KEY_HEADER = "x-anon-key";

    private final TossPartnerApiClient client;

    /** 토스 응답의 공통 모양 */
    public record Response(String resultType, String errorCode, String reason, JsonNode success) {

        public boolean ok() {
            return "SUCCESS".equals(resultType);
        }

        public static Response parse(JsonNode root) {
            return new Response(
                    root.path("resultType").asText(""),
                    root.path("error").path("errorCode").asText(""),
                    root.path("error").path("reason").asText(""),
                    root.path("success"));
        }
    }

    /** 지급 키를 발급받는다. 성공하면 success.key */
    public Response getKey() {
        return Response.parse(client.post(BASE + "/execute-promotion/get-key", Map.of()));
    }

    /** 지급을 요청한다. 같은 key로 다시 요청하면 4113(이미 지급됨)이 온다. */
    public Response execute(String anonKey, String promotionCode, String key, int amount) {
        return Response.parse(client.post(BASE + "/execute-promotion",
                Map.of("promotionCode", promotionCode, "key", key, "amount", amount),
                Map.of(ANON_KEY_HEADER, anonKey)));
    }

    /** 지급 결과를 조회한다. 성공하면 success가 PENDING / SUCCESS / FAILED 중 하나 */
    public Response result(String anonKey, String promotionCode, String key) {
        return Response.parse(client.post(BASE + "/execution-result",
                Map.of("promotionCode", promotionCode, "key", key),
                Map.of(ANON_KEY_HEADER, anonKey)));
    }
}
