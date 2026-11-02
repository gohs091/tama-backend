package com.tamagochi.tamagochi.repository.user;

import com.tamagochi.tamagochi.domain.user.NicknameHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NicknameHistoryRepository extends JpaRepository<NicknameHistory, Long> {

    List<NicknameHistory> findByUserIdOrderByChangedAtDesc(Long userId);

    List<NicknameHistory> findTop50ByNicknameOrderByChangedAtDesc(String nickname);
}
