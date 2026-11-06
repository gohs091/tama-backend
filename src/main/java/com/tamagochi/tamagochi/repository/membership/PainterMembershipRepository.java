package com.tamagochi.tamagochi.repository.membership;

import com.tamagochi.tamagochi.domain.membership.PainterMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PainterMembershipRepository extends JpaRepository<PainterMembership, Long> {

    Optional<PainterMembership> findByOrderId(String orderId);

    List<PainterMembership> findByUserIdOrderByCreatedAtDesc(Long userId);
}
