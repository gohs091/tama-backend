package com.tamagochi.tamagochi.controller.character;

import com.tamagochi.tamagochi.controller.dto.AnalyzeSketchRequest;
import com.tamagochi.tamagochi.controller.dto.CharacterFeaturesResponse;
import com.tamagochi.tamagochi.controller.dto.EvolveImageRequest;
import com.tamagochi.tamagochi.controller.dto.EvolveSketchRequest;
import com.tamagochi.tamagochi.controller.dto.EvolveFeaturesResponse;
import com.tamagochi.tamagochi.controller.dto.FinalDesignsRequest;
import com.tamagochi.tamagochi.service.character.FinalDesignService;
import com.tamagochi.tamagochi.controller.dto.GenerateImageRequest;
import com.tamagochi.tamagochi.controller.dto.GenerateImageResponse;
import com.tamagochi.tamagochi.controller.dto.ImageStatusResponse;
import com.tamagochi.tamagochi.controller.dto.LocalCompositeRequest;
import com.tamagochi.tamagochi.controller.dto.LocalCompositeResponse;
import com.tamagochi.tamagochi.controller.dto.SleepImageRequest;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.character.CharacterService;
import com.tamagochi.tamagochi.service.character.ImageGenerationService;
import com.tamagochi.tamagochi.service.character.ImageJobResult;
import com.tamagochi.tamagochi.service.character.ImageQuotaService;
import com.tamagochi.tamagochi.service.character.LocalImageCompositeService;
import com.tamagochi.tamagochi.service.character.SleepImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.land.InkLoadoutService;

import java.util.List;
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
    private final UserIdentity userIdentity;
    private final InkLoadoutService inkLoadoutService;
    private final SleepImageService sleepImageService;
    private final com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository tamagotchiRepository;

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
     * 낙서(baby) 이미지 생성 — 손그림 base64 → Stability AI sketch control
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateImageResponse> generate(@RequestBody GenerateImageRequest request) {
        ResponseEntity<GenerateImageResponse> rejected = checkQuota(request.getTossUserId());
        if (rejected != null) return rejected;
        String jobId = UUID.randomUUID().toString();
        imageGenerationService.storeJob(jobId, ImageJobResult.pending()); // 큐에서 대기 중인 작업이 "없는 작업"으로 보이지 않게 먼저 저장
        imageGenerationService.generateAsync(jobId, request.getImageBase64(), request.getFaction(), request.getVariationSeed(), request.getTossUserId());
        return ResponseEntity.ok(new GenerateImageResponse(jobId));
    }

    /**
     * 스케치(child) / 완성작(adult) 이미지 생성 — S3 URL 또는 base64 → Stability AI
     * Request body: { imageUrl, imageBase64, faction, stage(baby|child|adult) }
     */
    @PostMapping("/generate-evolved")
    public ResponseEntity<GenerateImageResponse> generateEvolved(@RequestBody EvolveImageRequest request) {
        ResponseEntity<GenerateImageResponse> rejected = checkQuota(request.getTossUserId());
        if (rejected != null) return rejected;
        // 진화 후보(child·adult)는 단계마다 하루 몇 번만 (다시 그리기를 쓴 만큼 더)
        String stage = request.getStage() == null ? "" : request.getStage().toLowerCase();
        if ((stage.equals("child") || stage.equals("adult"))
                && !imageQuotaService.tryAcquireEvolved(request.getTossUserId(), stage, redrawsUsedToday(request.getTossUserId()))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String jobId = UUID.randomUUID().toString();
        imageGenerationService.storeJob(jobId, ImageJobResult.pending());
        if ("adult".equals(request.getStage()) && imageGenerationService.finalDesignEnabled()) {
            // v5 완성작: 고른 디자인 후보를 글로만 그린다 (스케치 그림은 후보가 없을 때 디자인을 만드는 데만 쓴다)
            imageGenerationService.generateFinalAsync(jobId, request.getTossUserId(), request.getImageBase64(),
                    request.getReferenceImageBase64(), request.getDesignIndex());
            return ResponseEntity.ok(new GenerateImageResponse(jobId));
        }
        imageGenerationService.generateEvolvedAsync(
                jobId,
                request.getImageUrl(),
                request.getImageBase64(),
                request.getFaction(),
                request.getStage(),
                request.getReferenceImageBase64(),
                absorbedInk(request.getTossUserId(), request.getStage()),
                request.getTossUserId()
        );
        return ResponseEntity.ok(new GenerateImageResponse(jobId));
    }

    /** v5 완성작 디자인 후보에 대한 응답. enabled=false면 예전 방식(바로 그리기)이다 */
    public record FinalDesignsResponse(boolean enabled, List<FinalDesignService.Choice> designs) {}

    /**
     * v5 완성작 후보 3개(한글 이름·한 줄·몸 종류). 그림을 그리기 전에 앱이 보여 주고 사용자가 고른다.
     * 후보는 서버에 저장해 두고 같은 끄적이면 다시 만들지 않는다 (글 모델 호출이라 이미지 한도와 따로 센다).
     * Request body: { tossUserId, imageBase64(낙서), referenceImageBase64(스케치), refresh }
     */
    @PostMapping("/final-designs")
    public ResponseEntity<FinalDesignsResponse> finalDesigns(@RequestBody FinalDesignsRequest request) {
        if (request.getTossUserId() == null || request.getTossUserId().isBlank()) return ResponseEntity.badRequest().build();
        if (!imageGenerationService.finalDesignEnabled()) return ResponseEntity.ok(new FinalDesignsResponse(false, List.of()));
        if (request.getImageBase64() == null || request.getReferenceImageBase64() == null) return ResponseEntity.badRequest().build();
        List<FinalDesignService.Choice> designs = imageGenerationService.finalChoices(request.getTossUserId(),
                request.getImageBase64(), request.getReferenceImageBase64(), request.isRefresh());
        if (designs.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        return ResponseEntity.ok(new FinalDesignsResponse(true, designs));
    }

    /**
     * 자는 모습 이미지 생성. 첫 번째는 잉크 1000개를 내고, 이후 단계는 무료.
     * Request body: { tossUserId, imageBase64, faction, stage, idempotencyKey }
     */
    @PostMapping("/generate-sleep")
    public ResponseEntity<GenerateImageResponse> generateSleep(@RequestBody SleepImageRequest request) {
        ResponseEntity<GenerateImageResponse> rejected = checkQuota(request.getTossUserId());
        if (rejected != null) return rejected;
        User user = userIdentity.resolve(request.getTossUserId());
        SleepImageService.Start start = sleepImageService.begin(user, request.getStage(), request.getIdempotencyKey(), request.isRedo());
        String jobId = UUID.randomUUID().toString();
        imageGenerationService.storeJob(jobId, ImageJobResult.pending());
        imageGenerationService.generateSleepAsync(jobId, request.getImageBase64(), request.getFaction(), request.getTossUserId(),
                () -> sleepImageService.abort(user, start, request.getIdempotencyKey()));
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
    /** 끄적랜드에서 고른 대표 잉크 문장. 고른 게 없거나 유저를 못 찾으면 null (잉크 없이 진화) */
    private String absorbedInk(String tossUserId, String stage) {
        try {
            return inkLoadoutService.promptFor(userIdentity.resolve(tossUserId), stage);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** 오늘 쓴 진화 후보 다시 그리기 횟수 (유저나 끄적이를 못 찾으면 0) */
    private int redrawsUsedToday(String tossUserId) {
        try {
            var user = userIdentity.resolve(tossUserId);
            int per = com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi.CANDIDATE_REDRAWS_PER_DAY;
            return tamagotchiRepository.findActiveByUserId(user.getId())
                    .map(t -> per - t.candidateRedrawsLeft(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), per)).orElse(0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

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
        return ResponseEntity.ok(new ImageStatusResponse(result.getStatus(), result.getImageDataUrl(), result.getError(), result.getSpecies(), result.getFacing(), result.isSolid()));
    }
}
