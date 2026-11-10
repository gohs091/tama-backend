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
 * 완성작 진화 전에 "디자인"을 먼저 한다 (텍스트 모델).
 * 원본·잉크·진영을 그대로 그림 모델에 던지면 재료를 하나씩 붙인 콜라주가 나와 불쾌한 골짜기가 생긴다.
 * 그래서 지금 모습 이미지와 재료를 보고 콘셉트 하나·배색·몸 구조·시선 포인트·부위로 정리한 설계서를 만들고,
 * 그림 모델은 그 설계서대로 그린다. 실패하면 빈 값 (호출하는 쪽은 기존 프롬프트로 그린다).
 */
@Service
@Slf4j
public class EvolutionDesignService {

    private final ObjectMapper objectMapper;
    private final WebClient openAiClient;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.vision-model:gpt-4.1}")
    private String model;

    public EvolutionDesignService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.openAiClient = WebClient.builder().baseUrl("https://api.openai.com").build();
    }

    /**
     * @param currentPng 지금 모습(스케치) 이미지
     * @param origin     처음 그린 그림 분석 (없으면 null)
     * @param inkText    흡수한 잉크 문장 (없으면 null)
     */
    public Optional<JsonNode> designFinal(byte[] currentPng, String mimeType, DoodleSubject origin, String inkText,
                                          String factionMotifs, String factionColors) {
        if (currentPng == null || currentPng.length == 0 || openAiApiKey == null || openAiApiKey.isBlank()) return Optional.empty();
        try {
            String mime = mimeType != null && mimeType.contains("png") ? "image/png" : "image/jpeg";
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(currentPng);
            String user = "Origin: " + (origin == null || origin.subject() == null ? "unknown" : origin.subject()) + "\n" +
                    "Stage: FINAL fully evolved form (a big, impressive evolution)\n" +
                    "Ink data: " + (inkText == null || inkText.isBlank() ? "none (just mature its own features)" : inkText) + "\n" +
                    "Faction theme: " + factionMotifs + " (colors: " + factionColors + ")";
            Map<String, Object> body = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", ImagePromptV3.finalDesigner(origin)),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", user),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))));
            String json = openAiClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(40))
                    .block();
            String content = objectMapper.readTree(json).path("choices").path(0).path("message").path("content").asText("");
            JsonNode brief = objectMapper.readTree(content);
            if (brief.path("concept").asText("").isBlank()) return Optional.empty();
            log.info("완성작 디자인 - concept: {}", brief.path("concept").asText());
            return Optional.of(brief);
        } catch (Exception e) {
            log.warn("완성작 디자인 실패, 기존 프롬프트 사용: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
