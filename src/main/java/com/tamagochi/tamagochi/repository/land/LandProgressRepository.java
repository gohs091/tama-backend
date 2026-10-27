package com.tamagochi.tamagochi.repository.land;

import com.tamagochi.tamagochi.domain.land.LandProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LandProgressRepository extends JpaRepository<LandProgress, Long> {
    List<LandProgress> findByUserId(Long userId);
    Optional<LandProgress> findByUserIdAndRegionId(Long userId, String regionId);
}
