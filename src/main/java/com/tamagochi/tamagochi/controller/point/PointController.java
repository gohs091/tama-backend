package com.tamagochi.tamagochi.controller.point;

import com.tamagochi.tamagochi.controller.dto.EarnPointRequest;
import com.tamagochi.tamagochi.controller.dto.InkBathRequest;
import com.tamagochi.tamagochi.controller.dto.PointBalanceResponse;
import com.tamagochi.tamagochi.controller.dto.PointTransactionResponse;
import com.tamagochi.tamagochi.controller.dto.PurchasePointRequest;
import com.tamagochi.tamagochi.domain.point.PointTransaction;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.point.InkBathService;
import com.tamagochi.tamagochi.service.point.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/point")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;
    private final UserIdentity userIdentity;
    private final InkBathService inkBathService;

    @PostMapping("/earn")
    public ResponseEntity<?> earn(@RequestBody EarnPointRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        PointTransaction transaction = pointService.earnAdReward(
                user,
                request.getIdempotencyKey()
        );
        return ResponseEntity.ok(PointTransactionResponse.from(transaction));
    }

    @PostMapping("/purchase")
    public ResponseEntity<?> purchase(@RequestBody PurchasePointRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());

        PointTransaction transaction = pointService.purchasePoint(user, request.getOrderId(), request.getSku());
        return ResponseEntity.ok(PointTransactionResponse.from(transaction));
    }

    @GetMapping("/balance/{tossUserId}")
    public ResponseEntity<?> balance(@PathVariable String tossUserId) {
        User user = userIdentity.resolve(tossUserId);

        PointWallet wallet = pointService.getWallet(user);
        return ResponseEntity.ok(PointBalanceResponse.from(wallet));
    }

    @GetMapping("/ink-bath/{tossUserId}")
    public ResponseEntity<?> inkBathStatus(@PathVariable String tossUserId) {
        return ResponseEntity.ok(inkBathService.status(userIdentity.resolve(tossUserId)));
    }

    @PostMapping("/ink-bath/start")
    public ResponseEntity<?> inkBathStart(@RequestBody InkBathRequest request) {
        return ResponseEntity.ok(inkBathService.start(userIdentity.resolve(request.getTossUserId())));
    }

    @PostMapping("/ink-bath/stop")
    public ResponseEntity<?> inkBathStop(@RequestBody InkBathRequest request) {
        return ResponseEntity.ok(inkBathService.stop(userIdentity.resolve(request.getTossUserId())));
    }
}
