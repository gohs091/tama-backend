package com.tamagochi.tamagochi.controller.tamagotchi;

import com.tamagochi.tamagochi.controller.dto.*;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingResult;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tamagotchi")
@RequiredArgsConstructor
public class TamagotchiController {

    private final TamagotchiService tamagotchiService;
    private final UserRepository userRepository;

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody CreateTamagotchiRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));
        Tamagotchi tamagotchi = tamagotchiService.reset(user);
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody CreateTamagotchiRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        Tamagotchi tamagotchi = tamagotchiService.create(user, request.getName());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @PostMapping("/hatch")
    public ResponseEntity<?> hatch(@RequestBody FeedRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        tamagotchiService.hatch(user, request.getIdempotencyKey());
        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @PostMapping("/feed")
    public ResponseEntity<?> feed(@RequestBody FeedRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        tamagotchiService.feed(user, request.getIdempotencyKey());
        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        User user = userRepository.findByTossUserId(tossUserId)
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }
    @PostMapping("/sleep")
    public ResponseEntity<?> sleep(@RequestBody SleepRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        tamagotchiService.sleep(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/wakeup")
    public ResponseEntity<?> wakeUp(@RequestBody SleepRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        tamagotchiService.wakeUp(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/clean")
    public ResponseEntity<?> clean(@RequestBody CleanRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        tamagotchiService.clean(user, request.getIdempotencyKey());
        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

    @PostMapping("/training")
    public ResponseEntity<?> training(@RequestBody TrainingRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        TrainingResult result = tamagotchiService.training(
                user,
                request.getClickCount(),
                request.getIdempotencyKey()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/pill")
    public ResponseEntity<?> eatPill(@RequestBody PillRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        tamagotchiService.eatPill(user, request.getIdempotencyKey());
        Tamagotchi tamagotchi = tamagotchiService.getByUserId(user.getId());
        return ResponseEntity.ok(TamagotchiResponse.from(tamagotchi));
    }

}