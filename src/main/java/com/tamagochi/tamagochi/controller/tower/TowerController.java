package com.tamagochi.tamagochi.controller.tower;

import com.tamagochi.tamagochi.controller.dto.TowerRequest;
import com.tamagochi.tamagochi.domain.tower.TowerCombatCatalog;
import com.tamagochi.tamagochi.domain.tower.TowerMap;
import com.tamagochi.tamagochi.domain.tower.TowerMapCatalog;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.tower.TowerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 탑 원정: 출발·맵 통과·귀환. 그 응답은 모두 탑 전체 상태(TowerService.View). 맵 데이터(M-1)와 전투 데이터(H-2)는 읽기 전용 주소로 따로 준다 */
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

    /** 한 층의 맵 묶음 (M-1). maps는 맵 번호 순서 */
    public record FloorMaps(int floor, String faction, int mapsPerFloor, List<TowerMap> maps) {}

    /**
     * 그 층의 맵 전부 (읽기 전용 카탈로그, M-1). 사람마다 다르지 않고, 같은 층이면 언제나 같은 답이다.
     * 아직 맵이 없는 층(지금은 3층부터)은 400 NO_MAP_DATA
     */
    @GetMapping("/maps/{floor:\\d{1,4}}")
    public ResponseEntity<?> floorMaps(@PathVariable int floor) {
        List<TowerMap> maps = TowerMapCatalog.floorMaps(floor);
        if (maps.isEmpty()) throw new CodedException("NO_MAP_DATA", "아직 맵이 없는 층이에요.");
        return ResponseEntity.ok(new FloorMaps(floor, maps.get(0).faction(), maps.size(), maps));
    }

    /** 맵 한 장 (M-1): 칸 배치·충돌·포탈·스폰·보스 경기장·구출 자리. 없는 층·맵은 400 NO_MAP_DATA */
    @GetMapping("/maps/{floor:\\d{1,4}}/{map:\\d{1,4}}")
    public ResponseEntity<?> map(@PathVariable int floor, @PathVariable int map) {
        return ResponseEntity.ok(TowerMapCatalog.map(floor, map)
                .orElseThrow(() -> new CodedException("NO_MAP_DATA", "아직 맵이 없는 층이에요.")));
    }

    /**
     * 전투 데이터 묶음 (읽기 전용 카탈로그, H-2): 끄적이 기본 수치·층별 몬스터·스킬·보조. 전부 임시 값이다.
     * 사람마다 다르지 않고 언제나 같은 답이다. 서버는 이 수치로 한 대 한 대를 검증하지 않는다
     */
    @GetMapping("/combat")
    public ResponseEntity<?> combat() {
        return ResponseEntity.ok(TowerCombatCatalog.combat());
    }

    @PostMapping("/depart")
    public ResponseEntity<?> depart(@RequestBody TowerRequest r) {
        return ResponseEntity.ok(towerService.depart(userIdentity.resolve(r.getTossUserId()), r.getFloor()));
    }

    /**
     * 지금 맵을 통과했다고 알린다. 포만감 소모와 다음 맵·층은 서버가 정한다 (한 번에 한 칸).
     * runId·floor·map을 함께 보내면 서버의 지금 원정·층·맵과 같은지 확인한다
     */
    @PostMapping("/clear-map")
    public ResponseEntity<?> clearMap(@RequestBody TowerRequest r) {
        return ResponseEntity.ok(towerService.clearMap(userIdentity.resolve(r.getTossUserId()),
                r.getRunId(), r.getFloor(), r.getMap()));
    }

    @PostMapping("/return")
    public ResponseEntity<?> returnHome(@RequestBody TowerRequest r) {
        return ResponseEntity.ok(towerService.returnHome(userIdentity.resolve(r.getTossUserId())));
    }
}
