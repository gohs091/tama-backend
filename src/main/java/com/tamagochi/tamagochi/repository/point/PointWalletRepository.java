package com.tamagochi.tamagochi.repository.point;

import com.tamagochi.tamagochi.domain.point.PointWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {
    Optional<PointWallet> findByUserId(Long userId);
}
