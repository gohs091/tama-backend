package com.tamagochi.tamagochi.repository.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface PromotionGrantRepository extends JpaRepository<PromotionGrant, Long> {

    long countByUserIdAndGrantDate(Long userId, LocalDate grantDate);
}
