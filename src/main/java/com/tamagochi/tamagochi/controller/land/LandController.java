package com.tamagochi.tamagochi.controller.land;

import com.tamagochi.tamagochi.controller.dto.AdventureStartRequest;
import com.tamagochi.tamagochi.controller.dto.AdventureStopRequest;
import com.tamagochi.tamagochi.controller.dto.InkLoadoutRequest;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.land.AdventureService;
import com.tamagochi.tamagochi.service.land.InkLoadoutService;
import com.tamagochi.tamagochi.service.land.LandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 끄적랜드: 지도(구역 목록), 구역 정보, 보관함, 대표 잉크, 모험 시작·상태·멈추기 */
@RestController
@RequestMapping("/api/land")
@RequiredArgsConstructor
public class LandController {

    private final UserIdentity userIdentity;
    private final LandService landService;
    private final AdventureService adventureService;
    private final InkLoadoutService inkLoadoutService;

    public record Overview(List<LandService.RegionView> regions, AdventureService.Status adventure) {}

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> overview(@PathVariable String tossUserId) {
        User user = userIdentity.resolve(tossUserId);
        // 모험 상태를 먼저 읽어야 끝난 모험의 탐험 시간이 지도에 반영된다
        AdventureService.Status adventure = adventureService.status(user);
        return ResponseEntity.ok(new Overview(landService.regions(user), adventure));
    }

    @GetMapping("/{tossUserId}/regions/{regionId}")
    public ResponseEntity<?> region(@PathVariable String tossUserId, @PathVariable String regionId) {
        return ResponseEntity.ok(landService.region(userIdentity.resolve(tossUserId), regionId));
    }

    @GetMapping("/{tossUserId}/inventory")
    public ResponseEntity<?> inventory(@PathVariable String tossUserId) {
        return ResponseEntity.ok(landService.inventory(userIdentity.resolve(tossUserId)));
    }

    @GetMapping("/{tossUserId}/adventure")
    public ResponseEntity<?> adventure(@PathVariable String tossUserId) {
        return ResponseEntity.ok(adventureService.status(userIdentity.resolve(tossUserId)));
    }

    @PostMapping("/adventure/start")
    public ResponseEntity<?> start(@RequestBody AdventureStartRequest request) {
        return ResponseEntity.ok(adventureService.start(userIdentity.resolve(request.getTossUserId()), request.getRegionId()));
    }

    @PostMapping("/adventure/stop")
    public ResponseEntity<?> stop(@RequestBody AdventureStopRequest request) {
        return ResponseEntity.ok(adventureService.stop(userIdentity.resolve(request.getTossUserId())));
    }

    @GetMapping("/{tossUserId}/loadout")
    public ResponseEntity<?> loadout(@PathVariable String tossUserId) {
        return ResponseEntity.ok(inkLoadoutService.get(userIdentity.resolve(tossUserId)));
    }

    @PostMapping("/loadout")
    public ResponseEntity<?> setLoadout(@RequestBody InkLoadoutRequest request) {
        return ResponseEntity.ok(inkLoadoutService.set(userIdentity.resolve(request.getTossUserId()),
                request.getMainInkId(), request.getFamilyInkIds()));
    }
}
