package com.tamagochi.tamagochi.repository.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, String> {

    /** 아직 쓰지 않은 가장 최근 세션 */
    Optional<TrainingSession> findFirstByUserIdAndUsedAtIsNullOrderByStartedAtDesc(Long userId);
}
