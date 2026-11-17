package com.tamagochi.tamagochi.domain.quest;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 첫걸음 퀘스트 진행. 유저·퀘스트마다 한 줄이다 (유니크).
 * 줄이 없으면 아직 살펴보지 않은 것이고, 처음 조회할 때 모든 퀘스트의 줄이 생긴다 (StarterQuestService.fill).
 * 줄은 StarterQuestProgressRepository의 쿼리로만 만들고 바꾼다 (같은 줄을 동시에 만들거나 받아도 한 번만 되도록).
 */
@Entity
@Table(name = "starter_quest_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_starter_quest_user_quest", columnNames = {"user_id", "quest"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StarterQuestProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** StarterQuest.id. 퀘스트가 늘어도 컬럼을 고치지 않도록 글자로 둔다 */
    @Column(name = "quest", nullable = false, length = 32)
    private String quest;

    /** 끝낸 시각. 비어 있으면 아직 안 했다 */
    @Column(name = "done_at")
    private Instant doneAt;

    /** 보상을 받은 시각. 비어 있으면 아직 안 받았다 */
    @Column(name = "claimed_at")
    private Instant claimedAt;

    /** 이 기능이 생기기 전에 이미 해 본 항목이라 보상 없이 받은 것으로 닫았다 (보상 0으로 보여 준다) */
    @Column(name = "backfilled", nullable = false)
    private boolean backfilled;

    public boolean isDone() {
        return doneAt != null;
    }

    public boolean isClaimed() {
        return claimedAt != null;
    }
}
