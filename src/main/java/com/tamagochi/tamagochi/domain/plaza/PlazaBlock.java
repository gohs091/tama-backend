package com.tamagochi.tamagochi.domain.plaza;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 광장에서 한 사람이 다른 사람을 차단한 기록 (보는 사람 id → 차단당한 사람 id) */
@Entity
@Table(name = "plaza_block", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "blocked_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlazaBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    @Column(nullable = false)
    private Instant createdAt;

    public PlazaBlock(Long userId, Long blockedUserId) {
        this.userId = userId;
        this.blockedUserId = blockedUserId;
        this.createdAt = Instant.now();
    }
}
