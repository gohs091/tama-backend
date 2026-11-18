package com.tamagochi.tamagochi.controller.tamagotchi;

import com.tamagochi.tamagochi.controller.dto.EvolveRequest;
import com.tamagochi.tamagochi.controller.dto.TamagotchiResponse;
import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.gear.GearService;
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
    private final com.tamagochi.tamagochi.service.yard.YardService yardService;
    private final com.tamagochi.tamagochi.service.lunch.LunchService lunchService;
    private final GearService gearService;

    /** 도시락을 찬장에 준다 (기본 1개). 열매가 들지 않는다. id는 없어도 되고 예전 id여도 같은 도시락이다. 도시락·탑 화면 확인용 */
    @PostMapping("/lunch")
    public ResponseEntity<?> lunch(@RequestBody EvolveRequest request, @RequestParam(required = false) String id,
                                   @RequestParam(defaultValue = "1") int count) {
        return ResponseEntity.ok(lunchService.devGive(userIdentity.resolve(request.getTossUserId()), id, count));
    }

    /**
     * 기본 무기를 열매 없이 준다 (기본 나무 붓). 이미 있으면 그대로다.
     * 무기 칸이 비어 있으면 바로 낀다. 장비 화면 확인용
     */
    @PostMapping("/gear")
    public ResponseEntity<?> gear(@RequestBody EvolveRequest request, @RequestParam(required = false) String id) {
        return ResponseEntity.ok(gearService.devGive(userIdentity.resolve(request.getTossUserId()), id));
    }

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

    /** 열매를 준다 (기본 50). 방 꾸미기·농장 화면 확인용 */
    @PostMapping("/fruit")
    public ResponseEntity<?> fruit(@RequestBody EvolveRequest request, @RequestParam(defaultValue = "50") int amount) {
        return ResponseEntity.ok(yardService.devAddFruit(userIdentity.resolve(request.getTossUserId()), amount));
    }
}
