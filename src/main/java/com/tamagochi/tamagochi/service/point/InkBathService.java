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
import java.time.LocalDate;

/**
 * 잉크통 충전. 끄적이를 잉크통에 담가 두면 시간에 따라 잉크가 쌓인다.
 * 하루(KST) 한 번만 시작할 수 있고, 최대 4시간까지 쌓이며, 중간에 꺼내면 그날은 끝이다.
 * 시간은 모두 서버 시계로 계산한다(클라이언트 값은 신뢰하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class InkBathService {

    public static final int MAX_MINUTES = 4 * 60;
    public static final int INK_PER_HOUR = 30;

    private final PointWalletRepository pointWalletRepository;
    private final PointService pointService;
    private final Clock clock;

    /** 잉크통 상태. 진행 중이면 지금까지 쌓인 잉크와 남은 시간, 아니면 오늘 시작할 수 있는지 */
    public record Status(boolean running, boolean canStart, int elapsedMinutes, int remainingMinutes,
                         int accruedInk, int maxMinutes, int inkPerHour, int paidInk, int balance) {}

    @Transactional
    public Status status(User user) {
        PointWallet wallet = wallet(user);
        // 어제 시작해 정산하지 않고 둔 통은 먼저 정산한다
        int paid = 0;
        if (wallet.bathRunning() && (finished(wallet) || !today().equals(wallet.getBathDate()))) {
            paid = settle(user, wallet);
        }
        return build(wallet, paid);
    }

    @Transactional
    public Status start(User user) {
        PointWallet wallet = wallet(user);
        if (wallet.bathRunning() && (finished(wallet) || !today().equals(wallet.getBathDate()))) {
            settle(user, wallet);
        }
        if (wallet.bathRunning()) {
            throw new IllegalStateException("이미 잉크통에 들어가 있어요.");
        }
        LocalDate today = today();
        if (today.equals(wallet.getBathDate())) {
            throw new IllegalStateException("잉크통 충전은 하루에 한 번만 할 수 있어요.");
        }
        wallet.startBath(today, clock.instant());
        return build(wallet, 0);
    }

    /** 꺼내기. 지금까지 쌓인 잉크를 받고, 그날 충전은 끝난다 */
    @Transactional
    public Status stop(User user) {
        PointWallet wallet = wallet(user);
        int paid = wallet.bathRunning() ? settle(user, wallet) : 0;
        return build(wallet, paid);
    }

    static int inkFor(long minutes) {
        long capped = Math.min(Math.max(minutes, 0), MAX_MINUTES);
        return (int) (capped * INK_PER_HOUR / 60);
    }

    private int settle(User user, PointWallet wallet) {
        int ink = inkFor(elapsedMinutes(wallet));
        // 같은 세션을 두 번 정산하지 않도록 세션마다 고정 키를 쓴다
        String key = "ink-bath-" + user.getId() + "-" + wallet.getBathStartedAt().toEpochMilli();
        if (ink > 0) pointService.earnPoint(user, ink, key);
        wallet.settleBath();
        return ink;
    }

    private Status build(PointWallet wallet, int paidInk) {
        boolean running = wallet.bathRunning();
        long elapsed = running ? Math.min(elapsedMinutes(wallet), MAX_MINUTES) : 0;
        boolean canStart = !running && !today().equals(wallet.getBathDate());
        return new Status(running, canStart, (int) elapsed, running ? (int) (MAX_MINUTES - elapsed) : 0,
                running ? inkFor(elapsed) : 0, MAX_MINUTES, INK_PER_HOUR, paidInk, wallet.getBalance());
    }

    private boolean finished(PointWallet wallet) {
        return elapsedMinutes(wallet) >= MAX_MINUTES;
    }

    private long elapsedMinutes(PointWallet wallet) {
        return Duration.between(wallet.getBathStartedAt(), Instant.now(clock)).toMinutes();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private PointWallet wallet(User user) {
        return pointWalletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("지갑이 존재하지 않습니다."));
    }
}
