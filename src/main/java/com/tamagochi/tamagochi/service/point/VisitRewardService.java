package com.tamagochi.tamagochi.service.point;

import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 접속 보상. 앱에 들어오면 잉크를 주고, 그 뒤 4시간이 지나 다시 들어오면 또 준다 (잉크통 충전을 대신한다).
 * 시간은 서버 시계로 잰다. 같은 구간에서 두 번 주지 않도록 직전 보상 시각으로 멱등 키를 만든다.
 */
@Service
@RequiredArgsConstructor
public class VisitRewardService {

    public static final int AMOUNT = 100;
    public static final Duration INTERVAL = Duration.ofHours(4);

    private final PointWalletRepository pointWalletRepository;
    private final PointService pointService;
    private final Clock clock;
    private final com.tamagochi.tamagochi.service.membership.PainterMembershipService membershipService;

    /** granted: 이번에 받았는지. nextInSeconds: 다음 보상까지 남은 시간 */
    public record Status(boolean granted, int amount, int balance, long nextInSeconds) {}

    @Transactional
    public Status claim(User user) {
        PointWallet wallet = pointWalletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("지갑이 존재하지 않습니다."));
        Instant now = clock.instant();
        Instant last = wallet.getVisitRewardAt();
        if (last != null) {
            Duration since = Duration.between(last, now);
            if (since.compareTo(INTERVAL) < 0) {
                return new Status(false, 0, wallet.getBalance(), INTERVAL.minus(since).toSeconds());
            }
        }
        String key = "visit-" + user.getId() + "-" + (last == null ? 0 : last.toEpochMilli());
        int amount = membershipService.isPainter(user.getId()) ? AMOUNT * 2 : AMOUNT; // 화가 회원: 잉크 보상 2배
        pointService.earnPoint(user, amount, key);
        wallet.markVisitReward(now);
        return new Status(true, amount, wallet.getBalance(), INTERVAL.toSeconds());
    }
}
