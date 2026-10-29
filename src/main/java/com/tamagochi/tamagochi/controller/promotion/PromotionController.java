package com.tamagochi.tamagochi.controller.promotion;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.promotion.EvolutionPromotionService;
import com.tamagochi.tamagochi.service.promotion.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/promotion")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final EvolutionPromotionService evolutionPromotionService;

    /** 진화 달성 보상 안내(단계별 금액, 받았는지) */
    @GetMapping("/evolution/{tossUserId}")
    public ResponseEntity<?> evolutionInfo(@PathVariable String tossUserId) {
        return ResponseEntity.ok(evolutionPromotionService.info(userIdentity.resolve(tossUserId)));
    }
    private final UserIdentity userIdentity;

    /** 완전체 트레이닝 프로모션 안내(구간표, 오늘 남은 횟수) */
    @GetMapping("/training/{tossUserId}")
    public ResponseEntity<?> trainingInfo(@PathVariable String tossUserId) {
        User user = userIdentity.resolve(tossUserId);
        return ResponseEntity.ok(promotionService.info(user));
    }
}
