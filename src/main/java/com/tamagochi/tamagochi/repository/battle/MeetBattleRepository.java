package com.tamagochi.tamagochi.repository.battle;

import com.tamagochi.tamagochi.domain.battle.MeetBattle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface MeetBattleRepository extends JpaRepository<MeetBattle, Long> {

    /** 이 사람이 since 이후 보상을 받은 대결 수 */
    @Query("select count(b) from MeetBattle b where b.createdAt >= :since and "
            + "((b.hostUserId = :u and b.hostReward > 0) or (b.guestUserId = :u and b.guestReward > 0))")
    long countRewarded(@Param("u") Long userId, @Param("since") LocalDateTime since);

    /** 이 사람이 since 이후 한 대결 수 (보상과 상관없이) */
    @Query("select count(b) from MeetBattle b where b.createdAt >= :since and (b.hostUserId = :u or b.guestUserId = :u)")
    long countBattles(@Param("u") Long userId, @Param("since") LocalDateTime since);

    /** 두 사람이 since 이후 보상을 받으며 한 대결 수 */
    @Query("select count(b) from MeetBattle b where b.createdAt >= :since and "
            + "((b.hostUserId = :a and b.guestUserId = :b) or (b.hostUserId = :b and b.guestUserId = :a)) and "
            + "(b.hostReward > 0 or b.guestReward > 0)")
    long countPairRewarded(@Param("a") Long a, @Param("b") Long b, @Param("since") LocalDateTime since);
}
