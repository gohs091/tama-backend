package com.tamagochi.tamagochi.controller.farm;

import com.tamagochi.tamagochi.controller.dto.FarmRequest;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.farm.FarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 끄적이 농장: 밭·물·오늘의 퀘스트. 모든 응답은 농장 전체 상태(FarmService.View) */
@RestController
@RequestMapping("/api/farm")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;
    private final UserIdentity userIdentity;

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        return ResponseEntity.ok(farmService.get(userIdentity.resolve(tossUserId)));
    }

    @PostMapping("/plant")
    public ResponseEntity<?> plant(@RequestBody FarmRequest r) {
        return ResponseEntity.ok(farmService.plant(userIdentity.resolve(r.getTossUserId()), plot(r), r.getCrop()));
    }

    @PostMapping("/clear")
    public ResponseEntity<?> clear(@RequestBody FarmRequest r) {
        return ResponseEntity.ok(farmService.clear(userIdentity.resolve(r.getTossUserId()), plot(r)));
    }

    @PostMapping("/harvest")
    public ResponseEntity<?> harvest(@RequestBody FarmRequest r) {
        return ResponseEntity.ok(farmService.harvest(userIdentity.resolve(r.getTossUserId()), r.getPlot()));
    }

    @PostMapping("/unlock")
    public ResponseEntity<?> unlock(@RequestBody FarmRequest r) {
        return ResponseEntity.ok(farmService.unlock(userIdentity.resolve(r.getTossUserId()), plot(r)));
    }

    @PostMapping("/quest/claim")
    public ResponseEntity<?> claim(@RequestBody FarmRequest r) {
        return ResponseEntity.ok(farmService.claimQuest(userIdentity.resolve(r.getTossUserId()), r.getQuest()));
    }

    @PostMapping("/bonus")
    public ResponseEntity<?> bonus(@RequestBody FarmRequest r) {
        return ResponseEntity.ok(farmService.claimBonus(userIdentity.resolve(r.getTossUserId())));
    }

    @PostMapping("/water/ad")
    public ResponseEntity<?> adWater(@RequestBody FarmRequest r) {
        return ResponseEntity.ok(farmService.adWater(userIdentity.resolve(r.getTossUserId())));
    }

    private static int plot(FarmRequest r) {
        if (r.getPlot() == null) throw new IllegalArgumentException("밭을 골라 주세요.");
        return r.getPlot();
    }
}
