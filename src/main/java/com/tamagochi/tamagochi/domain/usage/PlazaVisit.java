package com.tamagochi.tamagochi.domain.usage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 광장 방문 한 번. 방에 들어왔다가 나갈 때 한 줄 남긴다 (한 마리도 안 잡은 방문도). 사용자 수치를 내려고 남긴다 (UsageRecorder).
 * 채팅은 횟수만 센다. 말한 내용은 남기지 않는다.
 */
@Entity
@Table(name = "plaza_visit", indexes = {
        @Index(name = "idx_plaza_visit_user", columnList = "user_id, entered_at"),
        @Index(name = "idx_plaza_visit_entered", columnList = "entered_at"),
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlazaVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 방의 종류: 2d | 3d (사냥 · 채팅은 3d 방에만 있다) */
    @Column(name = "kind", nullable = false, length = 4)
    private String kind;

    /** 방에 들어온 때 · 나간 때 (한국 시간) */
    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "left_at", nullable = false)
    private LocalDateTime leftAt;

    /** 이 방문에서 잡은 몬스터 수 */
    @Column(name = "kills", nullable = false)
    private int kills;

    /** 이 방문에서 방에 나간 말의 수 (막힌 말은 세지 않는다) */
    @Column(name = "chats", nullable = false)
    private int chats;

    public PlazaVisit(Long userId, String kind, LocalDateTime enteredAt, LocalDateTime leftAt, int kills, int chats) {
        this.userId = userId;
        this.kind = kind;
        this.enteredAt = enteredAt;
        this.leftAt = leftAt;
        this.kills = kills;
        this.chats = chats;
    }
}
