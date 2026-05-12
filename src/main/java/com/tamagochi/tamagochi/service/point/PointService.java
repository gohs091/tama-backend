package com.tamagochi.tamagochi.service.point;

import com.tamagochi.tamagochi.domain.point.PointTransaction;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.point.TransactionStatus;
import com.tamagochi.tamagochi.domain.point.TransactionType;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.point.PointTransactionRepository;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointService {

    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "point:idempotency:";
    private static final long IDEMPOTENCY_TTL = 24 * 60 * 60; // 24시간

    @Transactional
    public PointTransaction earnPoint(User user, int amount, String idempotencyKey) {

        // 1. Redis로 중복 요청 체크 (빠른 1차 필터)
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "processing", IDEMPOTENCY_TTL, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isNew)) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }

        // 2. DB로 중복 요청 체크 (정확한 2차 필터)
        if (pointTransactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            redisTemplate.delete(redisKey);
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }

        // 3. 포인트 지갑 잔액 증가 (낙관적 락으로 동시성 제어)
        PointWallet wallet = pointWalletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("지갑이 존재하지 않습니다."));
        wallet.charge(amount);

        // 4. 트랜잭션 기록 생성
        PointTransaction transaction = PointTransaction.builder()
                .user(user)
                .idempotencyKey(idempotencyKey)
                .type(TransactionType.EARN)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .build();

        return pointTransactionRepository.save(transaction);
    }

    @Transactional
    public PointTransaction usePoint(User user, int amount, String idempotencyKey) {

        // 1. Redis 중복 체크
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "processing", IDEMPOTENCY_TTL, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isNew)) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }

        // 2. DB 중복 체크
        if (pointTransactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            redisTemplate.delete(redisKey);
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }

        // 3. 포인트 차감 (잔액 부족시 예외 발생)
        PointWallet wallet = pointWalletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("지갑이 존재하지 않습니다."));
        wallet.use(amount);

        // 4. 트랜잭션 기록
        PointTransaction transaction = PointTransaction.builder()
                .user(user)
                .idempotencyKey(idempotencyKey)
                .type(TransactionType.USE)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .build();

        return pointTransactionRepository.save(transaction);
    }
    @Transactional
    public PointWallet getWallet(User user) {
        return pointWalletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("지갑이 존재하지 않습니다."));
    }

}
