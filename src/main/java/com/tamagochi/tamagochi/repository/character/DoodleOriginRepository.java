package com.tamagochi.tamagochi.repository.character;

import com.tamagochi.tamagochi.domain.character.DoodleOrigin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoodleOriginRepository extends JpaRepository<DoodleOrigin, Long> {
    Optional<DoodleOrigin> findByTossUserId(String tossUserId);
}
