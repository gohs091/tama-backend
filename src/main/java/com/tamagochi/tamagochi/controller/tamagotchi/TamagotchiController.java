package com.tamagochi.tamagochi.controller.tamagotchi;

import com.tamagochi.tamagochi.controller.dto.*;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingResult;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.tamagotchi.EvolutionGaugeClaimService;
import com.tamagochi.tamagochi.service.tamagotchi.RedrawService;
import com.tamagochi.tamagochi.service.promotion.EvolutionPromotionService;
import lombok.extern.slf4j.Slf4j;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tamagotchi")
@RequiredArgsConstructor
@Slf4j
public class TamagotchiController {

    private final TamagotchiService tamagotchiService;
    private final EvolutionGaugeClaimService evolutionGaugeClaimService;
    private final UserIdentity userIdentity;
    private final RedrawService redrawService;
    private final EvolutionPromotionService evolutionPromotionService;

    /** 진화 달성 보상(스케치 10원·완성작 50원). 보상 기록이 실패해도 진화 응답은 그대로 돌려준다 */
    private void rewardEvolution(User user, Tamagotchi tamagotchi) {
        try {
            evolutionPromotionService.onEvolved(user, tamagotchi.getEvolutionStage());
        } catch (Exception e) {
            log.warn("진화 달성 보상 기록 실패 - userId: {}", user.getId(), e);
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody CreateTamagotchiRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        Tamagotchi tamagotchi = tamagotchiService.reset(user);
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    /** 오늘 남은 다시 그리기 횟수 */
    @GetMapping("/redraw/{tossUserId}")
    public ResponseEntity<?> redrawStatus(@PathVariable String tossUserId) {
        return ResponseEntity.ok(redrawService.status(userIdentity.resolve(tossUserId)));
    }

    /** 갓 태어난 끄적이가 마음에 안 들면 알로 되돌린다 (하루 2번). 응답은 남은 횟수 */
    @PostMapping("/redraw")
    public ResponseEntity<?> redraw(@RequestBody EvolveRequest request) {
        return ResponseEntity.ok(redrawService.redraw(userIdentity.resolve(request.getTossUserId())));
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody CreateTamagotchiRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        Tamagotchi tamagotchi = tamagotchiService.create(user, request.getName(), request.getFaction());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @PostMapping("/image")
    public ResponseEntity<?> saveImage(@RequestBody SaveImageRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        tamagotchiService.saveImage(user, request.getStage(), request.getImageBase64());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/hatch")
    public ResponseEntity<?> hatch(@RequestBody FeedRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        tamagotchiService.hatch(user, request.getIdempotencyKey());
        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @PostMapping("/evolve")
    public ResponseEntity<?> evolve(@RequestBody EvolveRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        Tamagotchi tamagotchi = tamagotchiService.evolve(user);
        rewardEvolution(user, tamagotchi);
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    /** 광고를 본 뒤, 마지막 훈련으로 받을 수 있게 된 진화 게이지를 받는다 (광고 1번 = 훈련 1번의 게이지) */
    @PostMapping("/evo-gauge/claim")
    public ResponseEntity<?> claimEvoGauge(@RequestBody EvolveRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        return ResponseEntity.ok(TamagotchiResponse.from(evolutionGaugeClaimService.claim(user)));
    }

    @PostMapping("/feed")
    public ResponseEntity<?> feed(@RequestBody FeedRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        tamagotchiService.feed(user, request.getIdempotencyKey());
        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        User user = userIdentity.resolve(tossUserId);

        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }
    @PostMapping("/sleep")
    public ResponseEntity<?> sleep(@RequestBody SleepRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        tamagotchiService.sleep(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/wakeup")
    public ResponseEntity<?> wakeUp(@RequestBody SleepRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        tamagotchiService.wakeUp(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/clean")
    public ResponseEntity<?> clean(@RequestBody CleanRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        tamagotchiService.clean(user, request.getIdempotencyKey());
        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @PostMapping("/training/start")
    public ResponseEntity<?> trainingStart(@RequestBody TrainingStartRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        TrainingSession session = tamagotchiService.startTraining(user);
        return ResponseEntity.ok(TrainingStartResponse.of(session));
    }

    @PostMapping("/training")
    public ResponseEntity<?> training(@RequestBody TrainingRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        TrainingResult result = tamagotchiService.training(
                user,
                request.getSessionId(),
                request.getClickCount(),
                request.getIdempotencyKey()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/pill")
    public ResponseEntity<?> eatPill(@RequestBody PillRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        tamagotchiService.eatPill(user, request.getIdempotencyKey());
        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

}