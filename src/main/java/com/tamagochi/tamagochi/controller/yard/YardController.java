package com.tamagochi.tamagochi.controller.yard;

import com.tamagochi.tamagochi.controller.dto.YardRequest;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.yard.YardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 끄적이 마당: 화분·소품·배경. 모든 응답은 마당 전체 상태(YardService.View) */
@RestController
@RequestMapping("/api/yard")
@RequiredArgsConstructor
public class YardController {

    private final YardService yardService;
    private final UserIdentity userIdentity;

    @GetMapping("/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        return ResponseEntity.ok(yardService.get(userIdentity.resolve(tossUserId)));
    }

    @PostMapping("/harvest")
    public ResponseEntity<?> harvest(@RequestBody YardRequest request) {
        return ResponseEntity.ok(yardService.harvest(userIdentity.resolve(request.getTossUserId())));
    }

    @PostMapping("/boost")
    public ResponseEntity<?> boost(@RequestBody YardRequest request) {
        return ResponseEntity.ok(yardService.boost(userIdentity.resolve(request.getTossUserId())));
    }

    @PostMapping("/prop/buy")
    public ResponseEntity<?> buyProp(@RequestBody YardRequest request) {
        return ResponseEntity.ok(yardService.buyProp(userIdentity.resolve(request.getTossUserId()), request.getPropId(), request.getSlot()));
    }

    @PostMapping("/place")
    public ResponseEntity<?> place(@RequestBody YardRequest request) {
        return ResponseEntity.ok(yardService.place(userIdentity.resolve(request.getTossUserId()), request.getSlot(), request.getPropId()));
    }

    @PostMapping("/background/buy")
    public ResponseEntity<?> buyBackground(@RequestBody YardRequest request) {
        return ResponseEntity.ok(yardService.buyBackground(userIdentity.resolve(request.getTossUserId()), request.getBackground()));
    }

    @PostMapping("/background/use")
    public ResponseEntity<?> useBackground(@RequestBody YardRequest request) {
        return ResponseEntity.ok(yardService.useBackground(userIdentity.resolve(request.getTossUserId()), request.getBackground()));
    }
}
