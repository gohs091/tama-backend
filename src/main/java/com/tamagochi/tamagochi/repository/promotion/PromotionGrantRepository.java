package com.tamagochi.tamagochi.repository.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionGrantRepository extends JpaRepository<PromotionGrant, Long> {

    /**
     * 트레이닝 하루 상한을 셀 때는 지급에 실패한 건(FAILED)은 뺀다. 우리 쪽 문제로 기회를 잃지 않게 하기 위해서다.
     * 진화 달성 보상(kind=EVOLUTION)은 트레이닝 상한과 무관하므로 세지 않는다.
     */
    @Query("select count(g) from PromotionGrant g where g.user.id = :userId and g.grantDate = :grantDate "
            + "and g.status <> :status and (g.kind is null or g.kind <> 'EVOLUTION')")
    long countByUserIdAndGrantDateAndStatusNot(@Param("userId") Long userId, @Param("grantDate") LocalDate grantDate,
                                              @Param("status") PromotionGrantStatus status);

    /** 진화 달성 보상처럼 키로 한 번만 주는 건이 이미 있는지 */
    Optional<PromotionGrant> findByTrainingSessionId(String trainingSessionId);

    /** 한 곳에서만 처리하도록 상태를 선점한다. 선점에 성공하면 1, 이미 누가 가져갔으면 0. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update PromotionGrant g set g.status = :to, g.updatedAt = :now where g.id = :id and g.status = :from")
    int claim(@Param("id") Long id, @Param("from") PromotionGrantStatus from,
              @Param("to") PromotionGrantStatus to, @Param("now") LocalDateTime now);

    // 트랜잭션 밖(토스 호출 중)에서 사용자 키를 읽어야 해서 user를 함께 가져온다
    @EntityGraph(attributePaths = "user")
    Optional<PromotionGrant> findWithUserById(Long id);

    @EntityGraph(attributePaths = "user")
    List<PromotionGrant> findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(PromotionGrantStatus status, LocalDateTime before);
}
