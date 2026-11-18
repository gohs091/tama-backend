package com.tamagochi.tamagochi.controller.gear;

import com.tamagochi.tamagochi.controller.dto.GearRequest;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.gear.GearService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 기본 무기: 목록·구매(열매 정가)·장착. 모든 응답은 장비 전체 상태(GearService.View).
 * 전투력 숫자는 싣지 않는다. 기존 탑·도시락 주소는 건드리지 않는다
 */
@RestController
@RequestMapping("/api/gear")
@RequiredArgsConstructor
public class GearController {

    private final GearService gearService;
    private final UserIdentity userIdentity;

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        return ResponseEntity.ok(gearService.get(userIdentity.resolve(tossUserId)));
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestBody GearRequest r) {
        return ResponseEntity.ok(gearService.buy(userIdentity.resolve(r.getTossUserId()), r.getId()));
    }

    /** id를 비우면 벗어서 맨손이 된다 */
    @PostMapping("/equip")
    public ResponseEntity<?> equip(@RequestBody GearRequest r) {
        return ResponseEntity.ok(gearService.equip(userIdentity.resolve(r.getTossUserId()), r.getId()));
    }
}
