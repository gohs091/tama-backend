package com.tamagochi.tamagochi.repository.land;

import com.tamagochi.tamagochi.domain.land.Adventure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdventureRepository extends JpaRepository<Adventure, Long> {
    Optional<Adventure> findByUserId(Long userId);
}
