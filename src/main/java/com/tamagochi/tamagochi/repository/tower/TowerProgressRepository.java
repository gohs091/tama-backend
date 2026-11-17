package com.tamagochi.tamagochi.repository.tower;

import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TowerProgressRepository extends JpaRepository<TowerProgress, Long> {
    Optional<TowerProgress> findByUserId(Long userId);
}
