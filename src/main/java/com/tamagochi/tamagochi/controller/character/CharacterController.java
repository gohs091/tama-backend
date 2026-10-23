package com.tamagochi.tamagochi.controller.character;

import com.tamagochi.tamagochi.controller.dto.AnalyzeSketchRequest;
import com.tamagochi.tamagochi.controller.dto.CharacterFeaturesResponse;
import com.tamagochi.tamagochi.controller.dto.EvolveImageRequest;
import com.tamagochi.tamagochi.controller.dto.EvolveSketchRequest;
import com.tamagochi.tamagochi.controller.dto.EvolveFeaturesResponse;
import com.tamagochi.tamagochi.controller.dto.GenerateImageRequest;
import com.tamagochi.tamagochi.controller.dto.GenerateImageResponse;
import com.tamagochi.tamagochi.controller.dto.ImageStatusResponse;
import com.tamagochi.tamagochi.controller.dto.LocalCompositeRequest;
import com.tamagochi.tamagochi.controller.dto.LocalCompositeResponse;
import com.tamagochi.tamagochi.service.character.CharacterService;
import com.tamagochi.tamagochi.service.character.ImageGenerationService;
import com.tamagochi.tamagochi.service.character.ImageJobResult;
import com.tamagochi.tamagochi.service.character.ImageQuotaService;
import com.tamagochi.tamagochi.service.character.LocalImageCompositeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/character")
@RequiredArgsConstructor
@Slf4j
public class CharacterController {

    private final CharacterService characterService;
    private final ImageGenerationService imageGenerationService;
    private final LocalImageCompositeService localImageCompositeService;
    private final ImageQuotaService imageQuotaService;

    @PostMapping("/analyze")
    public ResponseEntity<CharacterFeaturesResponse> analyze(@RequestBody AnalyzeSketchRequest request) {
        CharacterFeaturesResponse features = characterService.analyze(request.getImageBase64());
        return ResponseEntity.ok(features);
    }

    @PostMapping("/evolve")
    public ResponseEntity<EvolveFeaturesResponse> evolve(@RequestBody EvolveSketchRequest request) {
        EvolveFeaturesResponse features = characterService.evolve(request.getImageBase64(), request.getFaction());
        return ResponseEntity.ok(features);
    }

    /**
     * 유체(baby) 이미지 생성 — 손그림 base64 → Stability AI sketch control
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateImageResponse> generate(@RequestBody GenerateImageRequest request) {
        ResponseEntity<GenerateImageResponse> rejected = checkQuota(request.getTossUserId());
        if (rejected != null) return rejected;
        String jobId = UUID.randomUUID().toString();
        imageGenerationService.storeJob(jobId, ImageJobResult.pending()); // 큐에서 대기 중인 작업이 "없는 작업"으로 보이지 않게 먼저 저장
        imageGenerationService.generateAsync(jobId, request.getImageBase64(), request.getFaction(), request.getVariationSeed());
        return ResponseEntity.ok(new GenerateImageResponse(jobId));
    }

    /**
     * 성체(child) / 완전체(adult) 이미지 생성 — S3 URL 또는 base64 → Stability AI
     * Request body: { imageUrl, imageBase64, faction, stage(baby|child|adult) }
     */
    @PostMapping("/generate-evolved")
    public ResponseEntity<GenerateImageResponse> generateEvolved(@RequestBody EvolveImageRequest request) {
        ResponseEntity<GenerateImageResponse> rejected = checkQuota(request.getTossUserId());
        if (rejected != null) return rejected;
        String jobId = UUID.randomUUID().toString();
        imageGenerationService.storeJob(jobId, ImageJobResult.pending());
        imageGenerationService.generateEvolvedAsync(
                jobId,
                request.getImageUrl(),
                request.getImageBase64(),
                request.getFaction(),
                request.getStage(),
                request.getReferenceImageBase64()
        );
        return ResponseEntity.ok(new GenerateImageResponse(jobId));
    }

    /**
     * 비용 0원 로컬 합성 이미지 생성 — Java AWT 순수 처리
     * Request body: { imageBase64, faction }
     * Response: { imageDataUrl: "data:image/png;base64,..." }
     */
    @PostMapping("/generate-local")
    public ResponseEntity<LocalCompositeResponse> generateLocal(@RequestBody LocalCompositeRequest request) {
        try {
            String dataUrl = localImageCompositeService.generate(request.getImageBase64(), request.getFaction());
            return ResponseEntity.ok(LocalCompositeResponse.ok(dataUrl));
        } catch (Exception e) {
            log.error("로컬 이미지 합성 실패", e);
            return ResponseEntity.internalServerError().body(LocalCompositeResponse.fail(e.getMessage()));
        }
    }

    /** 유저 식별자가 없으면 400, 일일 생성 한도를 넘으면 429. 통과하면 null. */
    private ResponseEntity<GenerateImageResponse> checkQuota(String tossUserId) {
        if (tossUserId == null || tossUserId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!imageQuotaService.tryAcquire(tossUserId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return null;
    }

    /**
     * 비동기 작업 상태 조회 (generate, generate-evolved 공용)
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<ImageStatusResponse> status(@PathVariable String jobId) {
        ImageJobResult result = imageGenerationService.getJob(jobId);
        return ResponseEntity.ok(new ImageStatusResponse(result.getStatus(), result.getImageDataUrl(), result.getError()));
    }
}
