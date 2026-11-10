package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5 완성작 디자인: 글 모델이 스케치·원본 낙서·잉크를 보고 정수를 뽑아, 몸 구조가 다른 후보 3개를 만든다.
 * 후보 전체(그림 문장 재료)는 서버(Redis)에만 두고, 앱에는 한글 이름·한 줄·몸 종류만 보낸다.
 * 그림을 그릴 때 앱은 번호만 보내므로, 앱이 그림 문장을 바꿔 넣을 수 없다.
 */
@Service
@Slf4j
public class FinalDesignService {

    /** 앱에 보여 줄 후보 (그림 문장 재료는 빼고) */
    public record Choice(String name, String line, String body) {}

    static final Duration TTL = Duration.ofDays(30);
    static final int COUNT = 3;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final WebClient openAiClient;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.vision-model:gpt-4.1}")
    private String visionModel;

    public FinalDesignService(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.openAiClient = WebClient.builder().baseUrl("https://api.openai.com")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)).build();
    }

    /** owner = 유저 + 지금 끄적이 (ImageGenerationService.designOwner) */
    static String key(String owner) {
        return "final-designs:" + owner;
    }

    /** 저장된 후보가 있으면 그대로, 없거나 refresh면 새로 만든다. 실패하면 빈 목록 */
    public List<Choice> choices(String owner, byte[] sketchPng, byte[] doodle, String doodleMime,
                                String originSubject, String inkTheme, boolean refresh) {
        ArrayNode saved = refresh ? null : load(owner).orElse(null);
        if (saved == null) {
            saved = design(sketchPng, doodle, doodleMime, originSubject, inkTheme).orElse(null);
            if (saved == null) return List.of();
            redis.opsForValue().set(key(owner), saved.toString(), TTL);
        }
        return toChoices(saved);
    }

    /** 고른 후보(번호가 없거나 범위 밖이면 무작위). 저장된 후보가 없으면 빈 값 */
    public Optional<JsonNode> pick(String owner, Integer index) {
        return load(owner).map(arr -> {
            int i = index != null && index >= 0 && index < arr.size() ? index : ThreadLocalRandom.current().nextInt(arr.size());
            return arr.get(i);
        });
    }

    /** 새 끄적이를 키우거나 완성작이 되면 지난 후보를 지운다 */
    public void clear(String owner) {
        redis.delete(key(owner));
    }

    Optional<ArrayNode> load(String owner) {
        try {
            String json = redis.opsForValue().get(key(owner));
            if (json == null) return Optional.empty();
            JsonNode node = objectMapper.readTree(json);
            return node.isArray() && !node.isEmpty() ? Optional.of((ArrayNode) node) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static List<Choice> toChoices(ArrayNode arr) {
        List<Choice> out = new ArrayList<>();
        arr.forEach(c -> out.add(new Choice(c.path("nameKo").asText(""), c.path("lineKo").asText(""), c.path("body").asText(""))));
        return out;
    }

    private Optional<ArrayNode> design(byte[] sketchPng, byte[] doodle, String doodleMime, String originSubject, String inkTheme) {
        try {
            String user = "Origin: " + (originSubject == null || originSubject.isBlank() ? "the creature in the doodle" : originSubject) + ". " +
                    "Ink fed for this evolution: " + (inkTheme == null ? "none (let its own nature decide)" : inkTheme) + ". Design its FINAL form.";
            String mime = doodleMime != null && doodleMime.contains("png") ? "image/png" : "image/jpeg";
            Map<String, Object> body = Map.of(
                    "model", visionModel,
                    "temperature", 0.9,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", ImagePromptV3.FINAL_DESIGNER_V5),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", user),
                                    Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(sketchPng))),
                                    Map.of("type", "image_url", "image_url", Map.of("url", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(doodle)))))));
            String json = openAiClient.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            return parse(objectMapper, objectMapper.readTree(json).path("choices").path(0).path("message").path("content").asText(""));
        } catch (Exception e) {
            log.warn("완성작 디자인 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 모델 답에서 후보 배열을 꺼낸다. 이름·그림 재료가 빠진 후보는 버리고, 3개를 넘으면 자른다 */
    static Optional<ArrayNode> parse(ObjectMapper om, String content) {
        try {
            JsonNode cands = om.readTree(content).path("candidates");
            if (!cands.isArray()) return Optional.empty();
            ArrayNode out = om.createArrayNode();
            for (JsonNode c : cands) {
                if (out.size() >= COUNT) break;
                if (c.path("nameKo").asText("").isBlank() || c.path("newForm").asText("").isBlank() || c.path("bodyPlan").asText("").isBlank()) continue;
                out.add(c);
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
