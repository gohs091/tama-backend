package com.tamagochi.tamagochi.repository.gear;

import com.tamagochi.tamagochi.domain.gear.GearInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GearInventoryRepository extends JpaRepository<GearInventory, Long> {
    Optional<GearInventory> findByUserId(Long userId);
}
