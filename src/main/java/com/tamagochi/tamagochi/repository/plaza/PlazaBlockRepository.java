package com.tamagochi.tamagochi.repository.plaza;

import com.tamagochi.tamagochi.domain.plaza.PlazaBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PlazaBlockRepository extends JpaRepository<PlazaBlock, Long> {
    List<PlazaBlock> findByUserId(Long userId);

    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    @Transactional
    long deleteByUserIdAndBlockedUserId(Long userId, Long blockedUserId);
}
