package com.tamagochi.tamagochi.repository.quest;

import com.tamagochi.tamagochi.domain.quest.StarterQuestProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 첫걸음 퀘스트 진행. 줄을 만드는 쿼리는 모두 INSERT IGNORE라서 같은 줄을 동시에 만들어도 오류 없이 하나만 남고,
 * 끝내기·받기는 조건이 붙은 UPDATE라서 바뀐 줄 수(0 또는 1)로 내가 했는지 안다.
 */
public interface StarterQuestProgressRepository extends JpaRepository<StarterQuestProgress, Long> {

    List<StarterQuestProgress> findByUserId(Long userId);

    Optional<StarterQuestProgress> findByUserIdAndQuest(Long userId, String quest);

    @Query("select count(p) from StarterQuestProgress p where p.user.id = :userId and p.quest = :quest and p.doneAt is not null")
    long countDone(@Param("userId") Long userId, @Param("quest") String quest);

    /** 아직 안 한 줄 */
    @Transactional
    @Modifying
    @Query(value = "insert ignore into starter_quest_progress (user_id, quest, done_at, claimed_at, backfilled) "
            + "values (:userId, :quest, null, null, false)", nativeQuery = true)
    int insertOpen(@Param("userId") Long userId, @Param("quest") String quest);

    /** 끝냈고 보상은 아직 안 받은 줄 */
    @Transactional
    @Modifying
    @Query(value = "insert ignore into starter_quest_progress (user_id, quest, done_at, claimed_at, backfilled) "
            + "values (:userId, :quest, :now, null, false)", nativeQuery = true)
    int insertDone(@Param("userId") Long userId, @Param("quest") String quest, @Param("now") Instant now);

    /** 예전에 이미 해 본 항목: 끝냈고 보상 없이 받은 것으로 닫은 줄 */
    @Transactional
    @Modifying
    @Query(value = "insert ignore into starter_quest_progress (user_id, quest, done_at, claimed_at, backfilled) "
            + "values (:userId, :quest, :now, :now, true)", nativeQuery = true)
    int insertBackfilled(@Param("userId") Long userId, @Param("quest") String quest, @Param("now") Instant now);

    /** 아직 안 한 줄을 끝낸 것으로 바꾼다. 바꿨으면 1 */
    @Transactional
    @Modifying
    @Query("update StarterQuestProgress p set p.doneAt = :now where p.user.id = :userId and p.quest = :quest and p.doneAt is null")
    int markDone(@Param("userId") Long userId, @Param("quest") String quest, @Param("now") Instant now);

    /** 끝냈고 아직 안 받은 줄만 받은 것으로 바꾼다. 동시에 여러 번 불러도 1을 받는 쪽은 하나뿐이다 */
    @Transactional
    @Modifying
    @Query("update StarterQuestProgress p set p.claimedAt = :now where p.user.id = :userId and p.quest = :quest "
            + "and p.doneAt is not null and p.claimedAt is null")
    int claim(@Param("userId") Long userId, @Param("quest") String quest, @Param("now") Instant now);
}
