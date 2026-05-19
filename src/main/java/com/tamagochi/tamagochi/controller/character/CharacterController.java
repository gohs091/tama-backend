package com.tamagochi.tamagochi.controller.character;

import com.tamagochi.tamagochi.controller.dto.AnalyzeSketchRequest;
import com.tamagochi.tamagochi.controller.dto.CharacterFeaturesResponse;
import com.tamagochi.tamagochi.controller.dto.EvolveSketchRequest;
import com.tamagochi.tamagochi.controller.dto.EvolveFeaturesResponse;
import com.tamagochi.tamagochi.controller.dto.GenerateImageRequest;
import com.tamagochi.tamagochi.controller.dto.GenerateImageResponse;
import com.tamagochi.tamagochi.controller.dto.ImageStatusResponse;
import com.tamagochi.tamagochi.service.character.CharacterService;
import com.tamagochi.tamagochi.service.character.ImageGenerationService;
import com.tamagochi.tamagochi.service.character.ImageJobResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/character")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;
    private final ImageGenerationService imageGenerationService;

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

    @PostMapping("/generate")
    public ResponseEntity<GenerateImageResponse> generate(@RequestBody GenerateImageRequest request) {
        String jobId = UUID.randomUUID().toString();
        imageGenerationService.generateAsync(jobId, request.getImageBase64(), request.getFaction());
        return ResponseEntity.ok(new GenerateImageResponse(jobId));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<ImageStatusResponse> status(@PathVariable String jobId) {
        ImageJobResult result = imageGenerationService.getJob(jobId);
        return ResponseEntity.ok(new ImageStatusResponse(result.getStatus(), result.getImageDataUrl(), result.getError()));
    }
}
