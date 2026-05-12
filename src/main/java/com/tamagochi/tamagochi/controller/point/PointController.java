package com.tamagochi.tamagochi.controller.point;

import com.tamagochi.tamagochi.controller.dto.EarnPointRequest;
import com.tamagochi.tamagochi.controller.dto.PointBalanceResponse;
import com.tamagochi.tamagochi.controller.dto.PointTransactionResponse;
import com.tamagochi.tamagochi.domain.point.PointTransaction;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/point")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;
    private final UserRepository userRepository;

    @PostMapping("/earn")
    public ResponseEntity<?> earn(@RequestBody EarnPointRequest request) {
        User user = userRepository.findByTossUserId(request.getTossUserId())
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        PointTransaction transaction = pointService.earnPoint(
                user,
                request.getAmount(),
                request.getIdempotencyKey()
        );
        return ResponseEntity.ok(PointTransactionResponse.from(transaction));
    }

    @GetMapping("/balance/{tossUserId}")
    public ResponseEntity<?> balance(@PathVariable String tossUserId) {
        User user = userRepository.findByTossUserId(tossUserId)
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));

        PointWallet wallet = pointService.getWallet(user);
        return ResponseEntity.ok(PointBalanceResponse.from(wallet));
    }
}