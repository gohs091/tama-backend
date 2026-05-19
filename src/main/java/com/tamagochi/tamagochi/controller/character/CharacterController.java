package com.tamagochi.tamagochi.controller.character;

import com.tamagochi.tamagochi.controller.dto.AnalyzeSketchRequest;
import com.tamagochi.tamagochi.controller.dto.CharacterFeaturesResponse;
import com.tamagochi.tamagochi.controller.dto.EvolveSketchRequest;
import com.tamagochi.tamagochi.controller.dto.EvolveFeaturesResponse;
import com.tamagochi.tamagochi.service.character.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/character")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

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
}
