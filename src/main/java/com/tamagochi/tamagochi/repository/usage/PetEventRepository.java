package com.tamagochi.tamagochi.repository.usage;

import com.tamagochi.tamagochi.domain.usage.PetEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PetEventRepository extends JpaRepository<PetEvent, Long> {

    List<PetEvent> findByUserIdOrderByIdAsc(Long userId);
}
