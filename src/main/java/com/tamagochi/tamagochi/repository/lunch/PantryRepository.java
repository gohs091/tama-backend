package com.tamagochi.tamagochi.repository.lunch;

import com.tamagochi.tamagochi.domain.lunch.Pantry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PantryRepository extends JpaRepository<Pantry, Long> {
    Optional<Pantry> findByUserId(Long userId);
}
