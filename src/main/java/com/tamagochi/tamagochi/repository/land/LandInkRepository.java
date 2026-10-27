package com.tamagochi.tamagochi.repository.land;

import com.tamagochi.tamagochi.domain.land.LandInk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LandInkRepository extends JpaRepository<LandInk, Long> {
    List<LandInk> findByUserIdOrderByFoundAtAsc(Long userId);
}
