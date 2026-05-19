package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.controller.dto.CharacterFeaturesResponse;
import com.tamagochi.tamagochi.controller.dto.EvolveFeaturesResponse;
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

    private static final String ANALYZE_PROMPT = """
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

    private static final String EVOLVE_PROMPT_TEMPLATE = """
            손으로 그린 스케치를 기반으로, 배경 팩션 "%s"의 세계관에 맞는 3단계 진화형 캐릭터를 JSON으로 반환하세요.
            설명이나 마크다운 없이 아래 형식 JSON만 반환하세요:
            {
              "baby":  { "primaryColor":"<hex>","secondaryColor":"<hex>","accentColor":"<hex>","bodyShape":"<round|oval|blob|angular>","eyeStyle":"<round|dot|star|squint>","hasTail":<bool>,"hasHorns":<bool>,"hasWings":<bool>,"hasEars":<bool>,"personality":"<cute|cool|spooky|fluffy|energetic>" },
              "child": { ... },
              "adult": { ... }
            }
            팩션별 진화 방향:
            - nature-spirits: 자연친화적, 초록/갈색 계열, 귀엽게→힘차게
            - deep-savers: 바다 계열, 청록/파랑, 물방울→용감한 수호자
            - nightmare-soldiers: 어둡고 강한, 검정/보라, 귀여운 악마→무서운 전사
            - wind-guardians: 하늘, 하얀/하늘색, 날개가 성장
            - metal-empire: 금속/은색, 차갑고 강함, 기계적 형태
            - virus-busters: 밝은 핑크/흰색, 에너지 방어막, 활기찬
            - dragons-roar: 빨강/주황, 비늘과 뿔이 성장
            - jungle-troopers: 초록/카키, 강인함, 귀여운 병사→용사
            - dark-area: 흑자주/보라, 신비로움, 별과 달 모티브
            - trash: 다채로운 형광색, 카오스, 쓰레기 귀여움
            유체(baby): 그림 특징 유지, 작고 귀엽게, 팩션 색감 20%%
            성체(child): 특징 강화, 팩션 색감 50%%, 액세서리 추가
            완전체(adult): 최강 형태, 팩션 색감 80%%, 특징 극대화
            스케치가 단색(검정 등)이면 팩션 대표 색상 사용.
            """;

    public CharacterFeaturesResponse analyze(String imageBase64) {
        String responseBody = callClaude(imageBase64, ANALYZE_PROMPT, 512);
        try {
            return parseJson(responseBody, CharacterFeaturesResponse.class);
        } catch (Exception e) {
            log.error("캐릭터 분석 실패", e);
            throw new IllegalStateException("캐릭터 분석에 실패했습니다.");
        }
    }

    public EvolveFeaturesResponse evolve(String imageBase64, String faction) {
        String prompt = String.format(EVOLVE_PROMPT_TEMPLATE, faction);
        String responseBody = callClaude(imageBase64, prompt, 1024);
        try {
            return parseJson(responseBody, EvolveFeaturesResponse.class);
        } catch (Exception e) {
            log.error("진화 특성 생성 실패", e);
            throw new IllegalStateException("진화 특성 생성에 실패했습니다.");
        }
    }

    private String callClaude(String imageBase64, String prompt, int maxTokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", maxTokens,
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
                                                "text", prompt
                                        )
                                )
                        )
                )
        );

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    ANTHROPIC_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Claude API 호출 실패", e);
            throw new IllegalStateException("Claude API 호출에 실패했습니다.");
        }
    }

    private <T> T parseJson(String responseBody, Class<T> type) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("content").get(0).path("text").asText().trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)```[a-z]*\\s*", "").replaceAll("```", "").trim();
        }
        return objectMapper.readValue(text, type);
    }
}
