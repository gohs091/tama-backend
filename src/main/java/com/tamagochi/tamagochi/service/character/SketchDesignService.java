package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.function.IntUnaryOperator;

/**
 * v6 스케치 디자인: 글 모델이 지금 모습(아기)·원본 낙서·잉크를 보고 "무엇으로 자랐는지" 한 줄을 후보 3개로 쓴다.
 * 후보마다 자라는 방향이 다르다 (날씬하게, 통통하게, 우아하게 등 정해 두지 않는다).
 * 완성작(FinalDesignService)처럼 후보는 서버(Redis)에 두고 앱에는 한글 한 줄만 보낸다: 사용자가 고른 번호로 그린다.
 * 번호 없이 그리면(예전 앱, 구간 끝 자동 진화) 지난번과 다른 후보를 무작위로 고른다.
 * 넣은 잉크가 바뀌면 후보를 새로 만든다 (한 줄에 잉크에서 온 것이 적혀 있다).
 */
@Service
@Slf4j
public class SketchDesignService {

    /** 앱에 보여 줄 후보 (그림 문장은 빼고 한글 한 줄만) */
    public record Choice(String line) {}

    /** 후보를 만들 때 필요한 재료: 지금 모습(밝은 바탕에 올린 PNG), 원본 낙서, 그림 분석, 진영, 넣은 잉크 설명(없으면 null) */
    public record Source(byte[] babyPng, byte[] doodle, String doodleMime, DoodleSubject origin, String faction, String inkTheme) {}

    static final Duration TTL = Duration.ofDays(30);
    static final int COUNT = 3;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final WebClient openAiClient;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.vision-model:gpt-4.1}")
    private String visionModel;

    public SketchDesignService(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.openAiClient = WebClient.builder().baseUrl("https://api.openai.com")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)).build();
    }

    /** owner = 유저 + 지금 끄적이 (ImageGenerationService.designOwner) */
    static String key(String owner) {
        return "sketch-designs:" + owner;
    }

    /** 후보 3개의 한글 한 줄. 저장된 후보가 있고 잉크가 같으면 그대로, 없거나 refresh면 새로 만든다. 실패하면 빈 목록 */
    public List<Choice> choices(String owner, Source source, boolean refresh) {
        ObjectNode saved = ensure(owner, source, refresh);
        if (saved == null) return List.of();
        save(owner, saved);
        return toChoices((ArrayNode) saved.get("candidates"));
    }

    /**
     * 그릴 후보 하나({"line","lineKo"}). index가 있으면 그 후보, 없거나 범위 밖이면 지난번과 다른 후보를 무작위로.
     * 저장된 후보가 없으면 지금 만든다. 글 모델이 실패하면 빈 값: 부르는 쪽이 기본 한 줄로 그린다.
     */
    public Optional<JsonNode> pick(String owner, Source source, Integer index) {
        ObjectNode saved = ensure(owner, source, false);
        if (saved == null) return Optional.empty();
        ArrayNode candidates = (ArrayNode) saved.get("candidates");
        int i = index != null && index >= 0 && index < candidates.size() ? index
                : pickIndex(candidates.size(), saved.path("last").asInt(-1), n -> ThreadLocalRandom.current().nextInt(n));
        saved.put("last", i);
        save(owner, saved);
        return Optional.of(candidates.get(i));
    }

    /** 지난번(last)과 다른 번호를 무작위로. 후보가 하나뿐이면 그 하나 */
    static int pickIndex(int size, int last, IntUnaryOperator random) {
        if (size <= 1) return 0;
        if (last < 0 || last >= size) return random.applyAsInt(size);
        int i = random.applyAsInt(size - 1);   // last를 뺀 나머지 중 하나
        return i >= last ? i + 1 : i;
    }

    /** 저장된 후보를 쓸 수 있는가: 다시 만들라는 요청이 아니고, 후보를 만들 때의 잉크가 지금 잉크와 같다 */
    static boolean reusable(JsonNode saved, String inkTheme, boolean refresh) {
        return !refresh && saved != null && saved.path("candidates").isArray() && !saved.path("candidates").isEmpty()
                && saved.path("ink").asText("").equals(inkTheme == null ? "" : inkTheme);
    }

    static List<Choice> toChoices(ArrayNode candidates) {
        List<Choice> out = new ArrayList<>();
        candidates.forEach(c -> out.add(new Choice(c.path("lineKo").asText(""))));
        return out;
    }

    private ObjectNode ensure(String owner, Source source, boolean refresh) {
        ObjectNode saved = load(owner).orElse(null);
        if (reusable(saved, source.inkTheme(), refresh)) return saved;
        ArrayNode fresh = design(source).orElse(null);
        if (fresh == null) return null;
        ObjectNode made = objectMapper.createObjectNode().put("ink", source.inkTheme() == null ? "" : source.inkTheme()).put("last", -1);
        made.set("candidates", fresh);
        return made;
    }

    Optional<ObjectNode> load(String owner) {
        try {
            String json = redis.opsForValue().get(key(owner));
            if (json == null) return Optional.empty();
            JsonNode node = objectMapper.readTree(json);
            return node.isObject() ? Optional.of((ObjectNode) node) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void save(String owner, ObjectNode saved) {
        try {
            redis.opsForValue().set(key(owner), saved.toString(), TTL);
        } catch (Exception e) {
            log.warn("스케치 디자인 저장 실패 (이번 그림은 그대로 그린다): {}", e.getMessage());
        }
    }

    private Optional<ArrayNode> design(Source s) {
        try {
            String mime = s.doodleMime() != null && s.doodleMime().contains("png") ? "image/png" : "image/jpeg";
            Map<String, Object> body = Map.of(
                    "model", visionModel,
                    "temperature", 0.9,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", ImagePromptV3.SKETCH_DESIGNER_V6),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", ImagePromptV3.sketchNoteV6(s.origin(), s.faction(), s.inkTheme())),
                                    Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(s.babyPng()))),
                                    Map.of("type", "image_url", "image_url", Map.of("url", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(s.doodle())))))));
            String json = openAiClient.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            return parse(objectMapper, objectMapper.readTree(json).path("choices").path(0).path("message").path("content").asText(""));
        } catch (Exception e) {
            log.warn("스케치 디자인 실패 (기본 한 줄로 그린다): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 모델 답에서 후보 배열을 꺼낸다. 한 줄이 빈 후보는 버리고, 3개를 넘으면 자른다 */
    static Optional<ArrayNode> parse(ObjectMapper om, String content) {
        try {
            JsonNode cands = om.readTree(content).path("candidates");
            if (!cands.isArray()) return Optional.empty();
            ArrayNode out = om.createArrayNode();
            for (JsonNode c : cands) {
                if (out.size() >= COUNT) break;
                if (c.path("line").asText("").isBlank() || c.path("lineKo").asText("").isBlank()) continue;
                out.add(c);
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
