package com.tamagochi.tamagochi.repository.spar;

import com.tamagochi.tamagochi.domain.spar.InkFragment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InkFragmentRepository extends JpaRepository<InkFragment, Long> {
    Optional<InkFragment> findByUserIdAndInkId(Long userId, String inkId);

    List<InkFragment> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
