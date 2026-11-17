package com.tamagochi.tamagochi.controller.quest;

import com.tamagochi.tamagochi.controller.dto.StarterQuestRequest;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.quest.StarterQuestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 첫걸음 퀘스트: 항목별 진행과 고정 잉크 보상. 모든 응답은 퀘스트 전체 상태(StarterQuestService.View) */
@RestController
@RequestMapping("/api/quests/starter")
@RequiredArgsConstructor
public class StarterQuestController {

    private final StarterQuestService starterQuestService;
    private final UserIdentity userIdentity;

    @GetMapping
    public ResponseEntity<?> get(@RequestParam(required = false) String tossUserId) {
        return ResponseEntity.ok(starterQuestService.get(userIdentity.resolve(tossUserId)));
    }

    /** 끝낸 퀘스트의 보상(잉크)을 받는다. 한 번만 받을 수 있다 */
    @PostMapping("/{id}/claim")
    public ResponseEntity<?> claim(@PathVariable String id, @RequestBody StarterQuestRequest request) {
        return ResponseEntity.ok(starterQuestService.claim(userIdentity.resolve(request.getTossUserId()), id));
    }

    /** 서버가 알 수 없는 항목(보관함 열어 보기)을 앱이 끝냈다고 알린다. 그 밖의 항목은 받지 않는다 */
    @PostMapping("/{id}/done")
    public ResponseEntity<?> done(@PathVariable String id, @RequestBody StarterQuestRequest request) {
        return ResponseEntity.ok(starterQuestService.reportDone(userIdentity.resolve(request.getTossUserId()), id));
    }
}
