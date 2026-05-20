package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ImageGenerationService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ImageJobResult> jobStore = new ConcurrentHashMap<>();

    @Value("${stability.api-key}")
    private String stabilityApiKey;

    private static final String STABILITY_URL = "https://api.stability.ai";
    private static final String SKETCH_ENDPOINT = "/v2beta/stable-image/control/sketch";

    private static final String NEGATIVE_PROMPT =
            "photorealistic, hyperrealistic, 3d render, complex mechanical parts, " +
            "human anatomy, sharp metallic armor, dark, scary, ugly, blurry, high tech";

    public ImageGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(STABILITY_URL)
                .build();
    }

    public void storeJob(String jobId, ImageJobResult result) {
        jobStore.put(jobId, result);
    }

    public ImageJobResult getJob(String jobId) {
        return jobStore.getOrDefault(jobId, ImageJobResult.failed("Job not found"));
    }

    // ----------------------------------------------------------------
    // 유체(baby) 생성: 손그림 base64 → Stability AI sketch control
    // ----------------------------------------------------------------
    @Async("imageExecutor")
    public void generateAsync(String jobId, String imageBase64, String faction) {
        jobStore.put(jobId, ImageJobResult.pending());
        try {
            String prompt = buildFactionPrompt(faction, "baby");
            byte[] imageBytes = decodeBase64Image(imageBase64);
            byte[] responseBytes = callStabilitySketch(imageBytes, prompt, "0.4");

            if (responseBytes == null || responseBytes.length == 0) {
                jobStore.put(jobId, ImageJobResult.failed("Empty response from Stability AI"));
                return;
            }
            jobStore.put(jobId, ImageJobResult.done(toDataUrl(responseBytes)));
            log.info("Baby image generation done for job: {}", jobId);

        } catch (Exception e) {
            log.error("Image generation failed for job: {}", jobId, e);
            jobStore.put(jobId, ImageJobResult.failed(e.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    // 성체/완전체 생성: S3 URL 또는 base64 → Stability AI sketch control
    // stage: baby | child(성체) | adult(완전체)
    // ----------------------------------------------------------------
    @Async("imageExecutor")
    public void generateEvolvedAsync(String jobId, String imageUrl, String imageBase64,
                                     String faction, String stage) {
        jobStore.put(jobId, ImageJobResult.pending());
        try {
            byte[] imageBytes;
            if (imageUrl != null && !imageUrl.isBlank()) {
                imageBytes = downloadFromUrl(imageUrl);
            } else if (imageBase64 != null && !imageBase64.isBlank()) {
                imageBytes = decodeBase64Image(imageBase64);
            } else {
                jobStore.put(jobId, ImageJobResult.failed("imageUrl 또는 imageBase64 중 하나는 필수입니다."));
                return;
            }

            // 진화 단계별 control_strength 조정
            String controlStrength = switch (stage) {
                case "child" -> "0.55"; // 성체: 조금 더 자유롭게
                case "adult" -> "0.65"; // 완전체: 더 극적으로
                default      -> "0.40"; // baby
            };

            String prompt = buildFactionPrompt(faction, stage);
            byte[] responseBytes = callStabilitySketch(imageBytes, prompt, controlStrength);

            if (responseBytes == null || responseBytes.length == 0) {
                jobStore.put(jobId, ImageJobResult.failed("Empty response from Stability AI"));
                return;
            }
            jobStore.put(jobId, ImageJobResult.done(toDataUrl(responseBytes)));
            log.info("Evolved ({}) image generation done for job: {}", stage, jobId);

        } catch (Exception e) {
            log.error("Evolved image generation failed for job: {}", jobId, e);
            jobStore.put(jobId, ImageJobResult.failed(e.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    // Stability AI sketch endpoint 공통 호출
    // ----------------------------------------------------------------
    private byte[] callStabilitySketch(byte[] imageBytes, String prompt, String controlStrength) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", imageBytes)
                .header("Content-Disposition", "form-data; name=\"image\"; filename=\"sketch.jpg\"")
                .header("Content-Type", "image/jpeg");
        builder.part("prompt", prompt);
        builder.part("negative_prompt", NEGATIVE_PROMPT);
        builder.part("control_strength", controlStrength);
        builder.part("output_format", "jpeg");

        return webClient.post()
                .uri(SKETCH_ENDPOINT)
                .header("Authorization", "Bearer " + stabilityApiKey)
                .header("Accept", "image/*")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    // ----------------------------------------------------------------
    // 팩션 × 단계 프롬프트
    // ----------------------------------------------------------------
    private String buildFactionPrompt(String faction, String stage) {
        String factionScene = getFactionScene(faction);
        String stageModifier = switch (stage) {
            case "child" -> " evolved form, slightly larger and stronger, faction colors more prominent,";
            case "adult"  -> " ultimate evolved form, maximum power, fully evolved, dominant faction colors, spectacular features,";
            default       -> "";
        };
        return "A cute minimalist monster inspired by user's drawing shape, set in " + factionScene
                + "," + stageModifier + " crayon style, naive art, soft texture, standalone on center, antique feel";
    }

    private String getFactionScene(String faction) {
        return switch (faction) {
            case "nature-spirits"     -> "a sunny vibrant grassland, desert canyons and wildflowers background";
            case "deep-savers"        -> "a deep blue ocean world, glowing coral reefs and gentle bubbles background";
            case "nightmare-soldiers" -> "a spooky whimsical night, glowing pumpkins and a soft purple misty forest background";
            case "wind-guardians"     -> "a breezy sky world, floating soft clouds and tall rustling green trees background";
            case "metal-empire"       -> "a whimsical retro robot city, playful gears and brass pipes background";
            case "virus-busters"      -> "a holy glowing cyber sanctuary, neon blue data streams and sparkles background";
            case "dragons-roar"       -> "a warm volcanic valley, friendly glowing lava streams and smoky rocks background";
            case "jungle-troopers"    -> "a dense lush jungle, giant tropical leaves and glowing fireflies background";
            case "dark-area"          -> "a cosmic void filled with stars, swirling nebulae and moonlight background";
            case "trash"              -> "a colorful chaotic junkyard, rainbow trash heaps and playful critters background";
            default                   -> "a magical fantasy world, sparkling crystals and dreamy light background";
        };
    }

    // ----------------------------------------------------------------
    // 유틸
    // ----------------------------------------------------------------
    private byte[] decodeBase64Image(String base64) {
        String clean = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        return Base64.getDecoder().decode(clean);
    }

    private byte[] downloadFromUrl(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("S3 이미지 다운로드 실패: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String toDataUrl(byte[] imageBytes) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StabilityApiResponse {
        private String image;
        private String finish_reason;
    }
}
