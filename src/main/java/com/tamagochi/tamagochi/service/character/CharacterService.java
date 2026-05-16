package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.controller.dto.CharacterFeaturesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api-key}")
    private String apiKey;

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";

    private static final String PROMPT = """
            손으로 그린 캐릭터 스케치를 분석해서 아래 JSON 형식으로만 응답하세요.
            설명이나 마크다운 없이 JSON만 반환하세요:
            {
              "primaryColor": "<몸체 주요 색상 hex, 예: #7EC8E3>",
              "secondaryColor": "<꼬리·갈기·윤곽 색상 hex>",
              "accentColor": "<발·뿔·강조 색상 hex>",
              "bodyShape": "<round | oval | blob | angular 중 하나>",
              "eyeStyle": "<round | dot | star | squint 중 하나>",
              "hasTail": <true | false>,
              "hasHorns": <true | false>,
              "hasWings": <true | false>,
              "hasEars": <true | false>,
              "personality": "<cute | cool | spooky | fluffy | energetic 중 하나>"
            }
            스케치가 단색(예: 검정)이면 primaryColor를 #B5D5FF(하늘색)로, secondaryColor를 #8AB8E8, accentColor를 #5A8AC0으로 하세요.
            """;

    public CharacterFeaturesResponse analyze(String imageBase64) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "max_tokens", 512,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "image",
                                                    "source", Map.of(
                                                            "type", "base64",
                                                            "media_type", "image/jpeg",
                                                            "data", imageBase64
                                                    )
                                            ),
                                            Map.of(
                                                    "type", "text",
                                                    "text", PROMPT
                                            )
                                    )
                            )
                    )
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    ANTHROPIC_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class
            );

            return parseFeatures(response.getBody());
        } catch (Exception e) {
            log.error("캐릭터 분석 실패", e);
            throw new IllegalStateException("캐릭터 분석에 실패했습니다.");
        }
    }

    private CharacterFeaturesResponse parseFeatures(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("content").get(0).path("text").asText().trim();

        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)```[a-z]*\\s*", "").replaceAll("```", "").trim();
        }

        return objectMapper.readValue(text, CharacterFeaturesResponse.class);
    }
}
