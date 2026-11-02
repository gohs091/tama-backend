package com.tamagochi.tamagochi.domain.battle;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 만나서 배틀 한 판의 기록. 하루 보상 횟수 제한에 쓴다 */
@Entity
@Table(name = "meet_battle", indexes = {
        @Index(name = "idx_meet_battle_host", columnList = "host_user_id, created_at"),
        @Index(name = "idx_meet_battle_guest", columnList = "guest_user_id, created_at"),
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetBattle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_user_id", nullable = false)
    private Long hostUserId;

    @Column(name = "guest_user_id", nullable = false)
    private Long guestUserId;

    /** 이긴 사람. 무승부면 null */
    @Column(name = "winner_user_id")
    private Long winnerUserId;

    private int hostTaps;
    private int guestTaps;
    private int hostReward;
    private int guestReward;

    /** 한쪽이 대결 중에 나가서 끝났다 */
    private boolean quit;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public void setRewards(int hostReward, int guestReward) {
        this.hostReward = hostReward;
        this.guestReward = guestReward;
    }
}
