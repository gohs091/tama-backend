package com.tamagochi.tamagochi.repository.point;

import com.tamagochi.tamagochi.domain.point.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);
}