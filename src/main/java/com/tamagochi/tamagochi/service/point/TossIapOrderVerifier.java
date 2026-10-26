package com.tamagochi.tamagochi.service.point;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Set;

/**
 * 앱인토스 서버 API(주문 상태 조회)로 결제 여부를 확인한다. mTLS 클라이언트 인증서가 필요하다.
 * 지급 시점의 주문은 PAYMENT_COMPLETED, 지급 완료 처리 뒤에는 PURCHASED 이므로 둘 다 결제된 주문으로 본다.
 * 같은 orderId의 중복 지급은 PointService가 막는다.
 */
@Component
@ConditionalOnProperty(name = "tamagotchi.iap.verify", havingValue = "toss", matchIfMissing = true)
@Slf4j
public class TossIapOrderVerifier implements IapOrderVerifier {

    private static final URI ORDER_STATUS_URI =
            URI.create("https://apps-in-toss-api.toss.im/api-partner/v1/apps-in-toss/order/get-order-status");
    private static final Set<String> PAID_STATUSES = Set.of("PAYMENT_COMPLETED", "PURCHASED");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tamagotchi.iap.keystore-path:}")
    private String keystorePath;

    @Value("${tamagotchi.iap.keystore-password:}")
    private String keystorePassword;

    @Value("${tamagotchi.iap.keystore-type:PKCS12}")
    private String keystoreType;

    private volatile HttpClient httpClient;

    @Override
    public boolean isPaid(String orderId, String sku) {
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of("orderId", orderId));
            HttpRequest request = HttpRequest.newBuilder(ORDER_STATUS_URI)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("IAP 주문 조회 실패 - orderId: {}, http: {}", orderId, response.statusCode());
                return false;
            }
            return isPaidResponse(objectMapper.readTree(response.body()), sku);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("결제 확인이 중단되었습니다.", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("IAP 주문 조회 오류 - orderId: {}", orderId, e);
            throw new IllegalStateException("결제 확인에 실패했습니다.", e);
        }
    }

    /** 응답이 SUCCESS 이고, 결제 완료 상태이며, 주문한 상품이 요청한 sku와 같을 때만 true */
    static boolean isPaidResponse(JsonNode root, String sku) {
        if (!"SUCCESS".equals(root.path("resultType").asText())) return false;
        JsonNode success = root.path("success");
        return PAID_STATUSES.contains(success.path("status").asText())
                && sku.equals(success.path("sku").asText());
    }

    private HttpClient client() throws Exception {
        HttpClient local = httpClient;
        if (local != null) return local;
        synchronized (this) {
            if (httpClient == null) {
                if (keystorePath.isBlank()) {
                    throw new IllegalStateException("IAP 클라이언트 인증서(tamagotchi.iap.keystore-path)가 설정되지 않았습니다.");
                }
                KeyStore keyStore = KeyStore.getInstance(keystoreType);
                try (FileInputStream in = new FileInputStream(keystorePath)) {
                    keyStore.load(in, keystorePassword.toCharArray());
                }
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, keystorePassword.toCharArray());
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(kmf.getKeyManagers(), null, null);
                httpClient = HttpClient.newBuilder()
                        .sslContext(ssl)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
            }
            return httpClient;
        }
    }
}
