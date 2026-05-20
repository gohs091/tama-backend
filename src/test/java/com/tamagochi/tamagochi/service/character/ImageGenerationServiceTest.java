package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageGenerationServiceTest {

    private ImageGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ImageGenerationService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "stabilityApiKey", "test-key");
    }

    // ----------------------------------------------------------------
    // getJob: 존재하지 않는 jobId → FAILED 반환
    // ----------------------------------------------------------------
    @Test
    @DisplayName("존재하지 않는 jobId 조회 시 FAILED 반환")
    void getJob_unknownId_returnsFailed() {
        ImageJobResult result = service.getJob("non-existent-job");
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getError()).isEqualTo("Job not found");
    }

    // ----------------------------------------------------------------
    // storeJob / getJob 순환 검증
    // ----------------------------------------------------------------
    @Test
    @DisplayName("storeJob → getJob 라운드트립")
    void storeAndGet_roundTrip() {
        String jobId = "job-123";
        service.storeJob(jobId, ImageJobResult.done("data:image/jpeg;base64,abc"));
        ImageJobResult result = service.getJob(jobId);
        assertThat(result.getStatus()).isEqualTo("DONE");
        assertThat(result.getImageDataUrl()).isEqualTo("data:image/jpeg;base64,abc");
    }

    // ----------------------------------------------------------------
    // generateAsync: API 키 미설정(빈 문자열)이면 Stability AI 호출 실패 → FAILED
    // 이 테스트는 실제 네트워크 없이 에러 경로를 검증한다.
    // ----------------------------------------------------------------
    @Test
    @DisplayName("Stability AI 호출 실패 시 jobStore에 FAILED 저장")
    void generateAsync_apiError_storesFailed() throws InterruptedException {
        ReflectionTestUtils.setField(service, "stabilityApiKey", "invalid-key-test");
        String base64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String jobId = "fail-job";

        // 실제 API를 호출하지 않도록 WebClient를 교체
        WebClient mockWebClient = buildMockWebClient(null /* 빈 응답 시뮬레이션 */);
        ReflectionTestUtils.setField(service, "webClient", mockWebClient);

        service.generateAsync(jobId, base64, "nature-spirits");

        // @Async이므로 잠깐 대기
        Thread.sleep(300);
        ImageJobResult result = service.getJob(jobId);
        // 빈 응답은 FAILED 처리
        assertThat(result.getStatus()).isEqualTo("FAILED");
    }

    // ----------------------------------------------------------------
    // generateAsync: 정상 응답 → DONE
    // ----------------------------------------------------------------
    @Test
    @DisplayName("Stability AI 정상 응답 시 jobStore에 DONE 저장")
    void generateAsync_successResponse_storesDone() throws InterruptedException {
        byte[] fakeImageBytes = "fake-jpeg-data".getBytes();
        WebClient mockWebClient = buildMockWebClient(fakeImageBytes);
        ReflectionTestUtils.setField(service, "webClient", mockWebClient);

        String base64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String jobId = "success-job";

        service.generateAsync(jobId, base64, "dragons-roar");
        Thread.sleep(300);

        ImageJobResult result = service.getJob(jobId);
        assertThat(result.getStatus()).isEqualTo("DONE");
        assertThat(result.getImageDataUrl()).startsWith("data:image/jpeg;base64,");
    }

    // ----------------------------------------------------------------
    // generateEvolvedAsync: imageUrl도 imageBase64도 없으면 FAILED
    // ----------------------------------------------------------------
    @Test
    @DisplayName("imageUrl·imageBase64 모두 null이면 즉시 FAILED")
    void generateEvolvedAsync_noInput_storesFailed() throws InterruptedException {
        String jobId = "evolved-no-input";
        service.generateEvolvedAsync(jobId, null, null, "virus-busters", "adult");
        Thread.sleep(200);
        ImageJobResult result = service.getJob(jobId);
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getError()).contains("imageUrl");
    }

    // ----------------------------------------------------------------
    // generateEvolvedAsync: stage=adult → DONE (mock)
    // ----------------------------------------------------------------
    @Test
    @DisplayName("stage=adult 정상 응답 시 DONE 저장")
    void generateEvolvedAsync_adultStage_storesDone() throws InterruptedException {
        byte[] fakeImageBytes = "fake-adult-jpeg".getBytes();
        WebClient mockWebClient = buildMockWebClient(fakeImageBytes);
        ReflectionTestUtils.setField(service, "webClient", mockWebClient);

        String base64 = Base64.getEncoder().encodeToString(new byte[]{10, 20, 30});
        String jobId = "evolved-adult";

        service.generateEvolvedAsync(jobId, null, base64, "metal-empire", "adult");
        Thread.sleep(300);

        ImageJobResult result = service.getJob(jobId);
        assertThat(result.getStatus()).isEqualTo("DONE");
    }

    // ----------------------------------------------------------------
    // 헬퍼: WebClient mock 구성
    // ----------------------------------------------------------------
    @SuppressWarnings({"unchecked", "rawtypes"})
    private WebClient buildMockWebClient(byte[] responseBody) {
        WebClient mockClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        // raw type으로 선언해 제네릭 캡처 충돌 회피
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(mockClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(any(), any())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(headersSpec);
        when(headersSpec.header(any(), any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(byte[].class)).thenReturn(
                responseBody != null ? Mono.just(responseBody) : Mono.empty()
        );
        return mockClient;
    }
}
