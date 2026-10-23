package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DoodleSubjectServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private DoodleSubjectService service;

    @BeforeEach
    void setUp() {
        service = new DoodleSubjectService(mapper);
        ReflectionTestUtils.setField(service, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "visionModel", "gpt-4.1-mini");
    }

    private String completion(String content) throws Exception {
        return "{\"choices\":[{\"message\":{\"content\":" + mapper.writeValueAsString(content) + "}}]}";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void respondWith(Mono<String> response) {
        WebClient client = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(any(), any())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(response);
        ReflectionTestUtils.setField(service, "openAiClient", client);
    }

    @Test
    @DisplayName("비전 응답을 대상 정보로 파싱한다")
    void parsesSubject() throws Exception {
        respondWith(Mono.just(completion(
                "{\"subject\":\"a sword\",\"kind\":\"object\",\"parts\":\"a blade on top, a handle at the bottom\"}")));

        Optional<DoodleSubject> r = service.describe(new byte[]{1, 2, 3}, "image/png");

        assertThat(r).isPresent();
        assertThat(r.get().subject()).isEqualTo("a sword");
        assertThat(r.get().kind()).isEqualTo("object");
        assertThat(r.get().parts()).contains("blade");
        assertThat(r.get().isAbstract()).isFalse();
    }

    @Test
    @DisplayName("마크다운 코드블록으로 감싼 응답도 파싱한다")
    void parsesFencedJson() throws Exception {
        respondWith(Mono.just(completion("```json\n{\"subject\":\"a cat\",\"kind\":\"animal\",\"parts\":\"two ears\"}\n```")));
        assertThat(service.describe(new byte[]{1}, "image/jpeg").get().subject()).isEqualTo("a cat");
    }

    @Test
    @DisplayName("추상 판정이면 isAbstract가 true")
    void abstractKind() throws Exception {
        respondWith(Mono.just(completion("{\"subject\":\"an abstract shape\",\"kind\":\"abstract\",\"parts\":\"\"}")));
        assertThat(service.describe(new byte[]{1}, "image/png").get().isAbstract()).isTrue();
    }

    @Test
    @DisplayName("호출이 실패하면 빈 값 (일반 프롬프트로 진행)")
    void apiFailure_returnsEmpty() {
        respondWith(Mono.error(new IllegalStateException("401 Unauthorized")));
        assertThat(service.describe(new byte[]{1}, "image/png")).isEmpty();
    }

    @Test
    @DisplayName("JSON이 아닌 응답이면 빈 값")
    void garbage_returnsEmpty() throws Exception {
        respondWith(Mono.just(completion("I think it is a sword")));
        assertThat(service.describe(new byte[]{1}, "image/png")).isEmpty();
    }

    @Test
    @DisplayName("subject가 비어 있으면 빈 값")
    void blankSubject_returnsEmpty() throws Exception {
        respondWith(Mono.just(completion("{\"subject\":\"\",\"kind\":\"object\",\"parts\":\"\"}")));
        assertThat(service.describe(new byte[]{1}, "image/png")).isEmpty();
    }
}
