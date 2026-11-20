package com.tamagochi.tamagochi.controller.tower;

import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.tower.TowerInventoryService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 탑 장비: 가방과 낀 장비 다섯 칸, 그리고 낙서(가진 낙서와 버튼 A·B의 지정). 읽기와 통째 저장뿐이다 (TowerInventoryService) */
@RestController
@RequestMapping("/api/tower/inventory")
@RequiredArgsConstructor
public class TowerInventoryController {

    private final TowerInventoryService inventoryService;
    private final UserIdentity userIdentity;

    @Getter
    @NoArgsConstructor
    public static class SaveRequest {
        private String tossUserId;
        private List<TowerInventoryService.Item> bag;
        private Map<String, TowerInventoryService.Item> equip;
        /** 없으면(낙서를 모르는 옛 앱) 저장된 낙서를 그대로 둔다 */
        private TowerInventoryService.Doodles doodles;
    }

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        return ResponseEntity.ok(inventoryService.get(userIdentity.resolve(tossUserId)));
    }

    @PutMapping
    public ResponseEntity<?> save(@RequestBody SaveRequest r) {
        return ResponseEntity.ok(inventoryService.save(userIdentity.resolve(r.getTossUserId()),
                new TowerInventoryService.Data(r.getBag(), r.getEquip(), r.getDoodles())));
    }
}
