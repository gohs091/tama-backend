package com.tamagochi.tamagochi.controller.tamagotchi;

import com.tamagochi.tamagochi.controller.dto.CreateTamagotchiRequest;
import com.tamagochi.tamagochi.controller.dto.FeedRequest;
import com.tamagochi.tamagochi.controller.dto.TamagotchiResponse;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
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

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody CreateTamagotchiRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        Tamagotchi tamagotchi = tamagotchiService.create(user, request.getName());
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
}