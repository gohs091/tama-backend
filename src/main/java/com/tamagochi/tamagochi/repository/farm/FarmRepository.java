package com.tamagochi.tamagochi.repository.farm;

import com.tamagochi.tamagochi.domain.farm.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarmRepository extends JpaRepository<Farm, Long> {
    Optional<Farm> findByUserId(Long userId);
}
