package com.tamagochi.tamagochi.service.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * 앱인토스 파트너 서버 API 호출(mTLS). 클라이언트 인증서가 필요하다.
 * 주문 조회(IAP), 사용자 식별키 코드 교환 등에서 같이 쓴다.
 */
@Component
@Slf4j
public class TossPartnerApiClient {

    private static final String BASE_URL = "https://apps-in-toss-api.toss.im";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tamagotchi.iap.keystore-path:}")
    private String keystorePath;

    @Value("${tamagotchi.iap.keystore-password:}")
    private String keystorePassword;

    @Value("${tamagotchi.iap.keystore-type:PKCS12}")
    private String keystoreType;

    private volatile HttpClient httpClient;

    /** JSON 본문을 POST하고 응답 JSON을 돌려준다. HTTP 200이 아니면 예외. */
    public JsonNode post(String path, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("토스 파트너 API 실패 - path: {}, http: {}", path, response.statusCode());
                throw new IllegalStateException("토스 서버 응답 오류(" + response.statusCode() + ")");
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("토스 서버 호출이 중단되었습니다.", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("토스 파트너 API 오류 - path: {}", path, e);
            throw new IllegalStateException("토스 서버 호출에 실패했습니다.", e);
        }
    }

    private HttpClient client() throws Exception {
        HttpClient local = httpClient;
        if (local != null) return local;
        synchronized (this) {
            if (httpClient == null) {
                if (keystorePath.isBlank()) {
                    throw new IllegalStateException("토스 클라이언트 인증서(tamagotchi.iap.keystore-path)가 설정되지 않았습니다.");
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
