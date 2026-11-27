package com.tamagochi.tamagochi.repository.usage;

import com.tamagochi.tamagochi.domain.usage.PlazaVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlazaVisitRepository extends JpaRepository<PlazaVisit, Long> {

    List<PlazaVisit> findByUserIdOrderByIdAsc(Long userId);
}
