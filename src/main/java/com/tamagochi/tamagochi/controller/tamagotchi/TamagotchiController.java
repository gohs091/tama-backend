package com.tamagochi.tamagochi.controller.tamagotchi;

import com.tamagochi.tamagochi.controller.dto.*;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingResult;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tamagotchi")
@RequiredArgsConstructor
public class TamagotchiController {

    private final TamagotchiService tamagotchiService;
    private final UserIdentity userIdentity;

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody CreateTamagotchiRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        Tamagotchi tamagotchi = tamagotchiService.reset(user);
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
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
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
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

    @PostMapping("/training")
    public ResponseEntity<?> training(@RequestBody TrainingRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        TrainingResult result = tamagotchiService.training(
                user,
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