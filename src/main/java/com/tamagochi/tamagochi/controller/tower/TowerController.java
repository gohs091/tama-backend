package com.tamagochi.tamagochi.controller.tower;

import com.tamagochi.tamagochi.controller.dto.TowerRequest;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.tower.TowerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 탑 원정: 출발·맵 통과·귀환. 모든 응답은 탑 전체 상태(TowerService.View) */
@RestController
@RequestMapping("/api/tower")
@RequiredArgsConstructor
public class TowerController {

    private final TowerService towerService;
    private final UserIdentity userIdentity;

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        return ResponseEntity.ok(towerService.get(userIdentity.resolve(tossUserId)));
    }

    @PostMapping("/depart")
    public ResponseEntity<?> depart(@RequestBody TowerRequest r) {
        return ResponseEntity.ok(towerService.depart(userIdentity.resolve(r.getTossUserId()), r.getFloor()));
    }

    /** 지금 맵을 통과했다고 알린다. 포만감 소모와 다음 맵·층은 서버가 정한다 */
    @PostMapping("/clear-map")
    public ResponseEntity<?> clearMap(@RequestBody TowerRequest r) {
        return ResponseEntity.ok(towerService.clearMap(userIdentity.resolve(r.getTossUserId())));
    }

    @PostMapping("/return")
    public ResponseEntity<?> returnHome(@RequestBody TowerRequest r) {
        return ResponseEntity.ok(towerService.returnHome(userIdentity.resolve(r.getTossUserId())));
    }
}
