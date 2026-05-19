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
    private static final String ENDPOINT = "/v2beta/stable-image/control/sketch";

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

    @Async("imageExecutor")
    public void generateAsync(String jobId, String imageBase64, String faction) {
        jobStore.put(jobId, ImageJobResult.pending());
        try {
            String factionKeyword = getFactionKeyword(faction);
            String prompt = "A cute minimalist monster inspired by user's drawing shape, set in " + factionKeyword
                    + ", crayon style, naive art, soft texture, standalone on center, antique feel";

            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", imageBytes)
                    .header("Content-Disposition", "form-data; name=\"image\"; filename=\"sketch.jpg\"")
                    .header("Content-Type", "image/jpeg");
            builder.part("prompt", prompt);
            builder.part("control_strength", "0.4");
            builder.part("output_format", "jpeg");

            byte[] responseBytes = webClient.post()
                    .uri(ENDPOINT)
                    .header("Authorization", "Bearer " + stabilityApiKey)
                    .header("Accept", "image/*")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (responseBytes == null || responseBytes.length == 0) {
                jobStore.put(jobId, ImageJobResult.failed("Empty response from Stability AI"));
                return;
            }

            String base64Image = Base64.getEncoder().encodeToString(responseBytes);
            String dataUrl = "data:image/jpeg;base64," + base64Image;
            jobStore.put(jobId, ImageJobResult.done(dataUrl));
            log.info("Image generation completed for job: {}", jobId);

        } catch (Exception e) {
            log.error("Image generation failed for job: {}", jobId, e);
            jobStore.put(jobId, ImageJobResult.failed(e.getMessage()));
        }
    }

    private String getFactionKeyword(String faction) {
        return switch (faction) {
            case "nature-spirits"     -> "enchanted forest with glowing plants";
            case "deep-savers"        -> "deep ocean underwater world";
            case "nightmare-soldiers" -> "dark misty battlefield with shadows";
            case "wind-guardians"     -> "floating sky islands above clouds";
            case "metal-empire"       -> "industrial steampunk city";
            case "virus-busters"      -> "bright digital cyberspace";
            case "dragons-roar"       -> "volcanic dragon lair with lava";
            case "jungle-troopers"    -> "dense tropical jungle";
            case "dark-area"          -> "cosmic void filled with stars";
            case "trash"              -> "colorful chaotic junkyard";
            default                   -> "magical fantasy world";
        };
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StabilityApiResponse {
        private String image; // base64
        private String finish_reason;
    }
}
