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
             "kind":"<animal | creature | tool | object | plant | person | abstract>",
             "subjectKo":"<the single most fitting short Korean noun for the subject, e.g. '칼', '고양이', '활', '컵'>",
             "parts":"<comma-separated list of the 2-5 key parts with rough positions, e.g. 'a long curved blade on top, a horizontal crossguard in the middle, a short handle at the bottom'>",
             "traits":"<the 2-3 most distinctive, unusual or characteristic features of THIS particular drawing that make it different from a generic one, e.g. 'one ear much bigger than the other, a zigzag tail, a very large head'>",
             "bodyPlan":"<one of: biped | quadruped | winged | serpent | vehicle | blob | floating | other>",
             "attach":"<only when kind is tool, otherwise empty: which body part of a cute creature this tool should become, one of ears | shell | hands | wings>"}
            Use kind "tool" for hand-held or worn tools, weapons, armor pieces and gear (a sword, a shield, a helmet, a bow, an axe, a hammer, a pencil, an umbrella, a key, a frying pan). A shield is always a tool, even when it has an emblem or a cross on it. Use kind "object" for complete finished objects such as vehicles, buildings, furniture and machines (a car, a train, a house, a robot).
            For a tool choose attach by its shape: ears = long, narrow, pointed tools (sword, spear, knife, pencil, arrow); shell = wide, flat or round tools (shield, pan, lid, umbrella); hands = tools held in a fist or that come in pairs (hammer, racket, glove); wings = flat fan-like tools (fan, paddle, leaf-shaped blade).
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
