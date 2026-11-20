package com.tamagochi.tamagochi.repository.tower;

import com.tamagochi.tamagochi.domain.tower.TowerInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TowerInventoryRepository extends JpaRepository<TowerInventory, Long> {
    Optional<TowerInventory> findByUserId(Long userId);
}
