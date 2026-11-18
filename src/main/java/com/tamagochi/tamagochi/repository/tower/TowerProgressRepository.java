package com.tamagochi.tamagochi.repository.tower;

import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TowerProgressRepository extends JpaRepository<TowerProgress, Long> {
    Optional<TowerProgress> findByUserId(Long userId);

    /** 지금 원정 중인 진행의 id (제한 시간이 지난 원정을 접는 스케줄러용) */
    @Query("select p.id from TowerProgress p where p.onExpedition = true")
    List<Long> findIdsOnExpedition();
}
