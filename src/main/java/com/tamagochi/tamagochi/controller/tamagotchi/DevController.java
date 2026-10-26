package com.tamagochi.tamagochi.controller.tamagotchi;

import com.tamagochi.tamagochi.controller.dto.EvolveRequest;
import com.tamagochi.tamagochi.controller.dto.TamagotchiResponse;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로컬 개발 전용. tamagotchi.dev-tools=true 일 때만 등록된다 (운영 설정은 false). */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tamagotchi.dev-tools", havingValue = "true")
public class DevController {

    private final TamagotchiService tamagotchiService;
    private final UserIdentity userIdentity;

    /** 게이지·컨디션과 무관하게 다음 단계로 올린다. */
    @PostMapping("/evolve")
    public ResponseEntity<?> evolve(@RequestBody EvolveRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchiService.forceEvolve(user)));
    }
}
