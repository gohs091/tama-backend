package com.tamagochi.tamagochi.repository.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TamagotchiRepository extends JpaRepository<Tamagotchi, Long> {
    Optional<Tamagotchi> findByUserId(Long userId);
}
