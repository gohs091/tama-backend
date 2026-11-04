package com.tamagochi.tamagochi.repository.yard;

import com.tamagochi.tamagochi.domain.yard.Yard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface YardRepository extends JpaRepository<Yard, Long> {
    Optional<Yard> findByUserId(Long userId);
}
