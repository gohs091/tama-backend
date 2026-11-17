package com.tamagochi.tamagochi.controller.lunch;

import com.tamagochi.tamagochi.controller.dto.LunchRequest;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.lunch.LunchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 도시락: 만들기·가방 챙기기·탑에서 먹기. 모든 응답은 도시락 전체 상태(LunchService.View) */
@RestController
@RequestMapping("/api/lunch")
@RequiredArgsConstructor
public class LunchController {

    private final LunchService lunchService;
    private final UserIdentity userIdentity;

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        return ResponseEntity.ok(lunchService.get(userIdentity.resolve(tossUserId)));
    }

    @PostMapping("/cook")
    public ResponseEntity<?> cook(@RequestBody LunchRequest r) {
        return ResponseEntity.ok(lunchService.cook(userIdentity.resolve(r.getTossUserId()), r.getId(), r.getCount()));
    }

    @PostMapping("/pack")
    public ResponseEntity<?> pack(@RequestBody LunchRequest r) {
        return ResponseEntity.ok(lunchService.pack(userIdentity.resolve(r.getTossUserId()), r.getId(), r.getCount()));
    }

    @PostMapping("/unpack")
    public ResponseEntity<?> unpack(@RequestBody LunchRequest r) {
        return ResponseEntity.ok(lunchService.unpack(userIdentity.resolve(r.getTossUserId()), r.getId(), r.getCount()));
    }

    @PostMapping("/eat")
    public ResponseEntity<?> eat(@RequestBody LunchRequest r) {
        return ResponseEntity.ok(lunchService.eat(userIdentity.resolve(r.getTossUserId()), r.getId()));
    }
}
