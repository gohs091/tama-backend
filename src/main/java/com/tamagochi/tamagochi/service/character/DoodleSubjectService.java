package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 손그림이 무엇인지 OpenAI 비전 모델로 분석한다 (이미지 생성에 쓰는 OpenAI 키를 그대로 사용).
 * 실패하면 빈 값을 돌려주고, 호출하는 쪽은 대상 없이 일반 프롬프트로 진행한다.
 */
@Service
@Slf4j
public class DoodleSubjectService {

    private static final String PROMPT = """
            Look at this rough doodle drawn by a player and say what it depicts.
            Respond with JSON only, no markdown:
            {"subject":"<short English noun phrase, e.g. 'a sword', 'a cat', 'a bow with a string and an arrow'>",
             "kind":"<animal | creature | object | plant | person | abstract>",
             "subjectKo":"<the single most fitting short Korean noun for the subject, e.g. '칼', '고양이', '활', '컵'>",
             "parts":"<comma-separated list of the 2-5 key parts with rough positions, e.g. 'a long curved blade on top, a horizontal crossguard in the middle, a short handle at the bottom'>"}
            If you cannot tell what it is, use subject "an abstract shape" and kind "abstract".
            """;

    private final ObjectMapper objectMapper;
    private WebClient openAiClient;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    // 8회 반복 비교에서 gpt-4.1-mini는 칼 그림을 25% 오인(crossbow), gpt-4.1은 안정적이었다
    @Value("${openai.vision-model:gpt-4.1}")
    private String visionModel;

    public DoodleSubjectService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.openAiClient = WebClient.builder().baseUrl("https://api.openai.com").build();
    }

    public Optional<DoodleSubject> describe(byte[] imageBytes, String mimeType) {
        try {
            String mime = mimeType != null && mimeType.contains("png") ? "image/png" : "image/jpeg";
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> body = Map.of(
                    "model", visionModel,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", PROMPT),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))));

            String json = openAiClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return parse(json);
        } catch (Exception e) {
            log.warn("손그림 대상 분석 실패, 일반 프롬프트 사용: {}", e.getMessage());
            return Optional.empty();
        }
    }

    Optional<DoodleSubject> parse(String chatCompletionJson) throws Exception {
        JsonNode root = objectMapper.readTree(chatCompletionJson);
        String text = root.path("choices").path(0).path("message").path("content").asText("").trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)```[a-z]*\\s*", "").replace("```", "").trim();
        }
        DoodleSubject subject = objectMapper.readValue(text, DoodleSubject.class);
        if (subject == null || subject.subject() == null || subject.subject().isBlank()) return Optional.empty();
        return Optional.of(subject);
    }
}
