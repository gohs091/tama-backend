package com.tamagochi.tamagochi.controller.tamagotchi;

import com.tamagochi.tamagochi.controller.dto.EvolveRequest;
import com.tamagochi.tamagochi.controller.dto.TamagotchiResponse;
import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.land.AdventureService;
import com.tamagochi.tamagochi.service.promotion.EvolutionPromotionService;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로컬 개발 전용. tamagotchi.dev-tools=true 일 때만 등록된다 (운영 설정은 false). */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tamagotchi.dev-tools", havingValue = "true")
public class DevController {

    private final TamagotchiService tamagotchiService;
    private final UserIdentity userIdentity;
    private final AdventureService adventureService;
    private final EvolutionPromotionService evolutionPromotionService;

    /** 게이지·컨디션과 무관하게 다음 단계로 올린다. */
    @PostMapping("/evolve")
    public ResponseEntity<?> evolve(@RequestBody EvolveRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        Tamagotchi pet = tamagotchiService.forceEvolve(user);
        evolutionPromotionService.onEvolved(user, pet.getEvolutionStage()); // 진화 달성 보상 화면 확인용
        return ResponseEntity.ok(TamagotchiResponse.from(pet));
    }

    /** 다음 레벨까지 남은 경험치를 바로 넣는다. 진화 가능 구간·진화 후보 화면 확인용 */
    @PostMapping("/level-up")
    public ResponseEntity<?> levelUp(@RequestBody EvolveRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchiService.devLevelUp(user)));
    }

    /** 열린 구역마다 minutes분(기본 4시간) 탐험한 것으로 치고 잉크를 발견한다. 보관함·대표 잉크 화면 확인용 */
    @PostMapping("/land/explore")
    public ResponseEntity<?> landExplore(@RequestBody EvolveRequest request,
                                         @RequestParam(defaultValue = "240") int minutes) {
        User user = userIdentity.resolve(request.getTossUserId());
        LandCatalog.regions().stream().filter(LandCatalog.Region::open)
                .forEach(r -> adventureService.devExplore(user, r.id(), minutes));
        return ResponseEntity.ok().build();
    }
}
