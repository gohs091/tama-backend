package com.tamagochi.tamagochi.repository.usage;

import com.tamagochi.tamagochi.domain.usage.UserDailyVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface UserDailyVisitRepository extends JpaRepository<UserDailyVisit, Long> {

    boolean existsByUserIdAndVisitDate(Long userId, LocalDate visitDate);

    List<UserDailyVisit> findByUserId(Long userId);
}
