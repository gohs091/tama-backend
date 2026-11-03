package com.tamagochi.tamagochi.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 닉네임이 정해지거나 바뀔 때마다 남긴다. 예전 닉네임으로 문의가 와도 유저를 찾을 수 있게 한다. */
@Entity
@Table(name = "nickname_history", indexes = {
        @Index(name = "idx_nickname_history_user", columnList = "user_id"),
        @Index(name = "idx_nickname_history_nickname", columnList = "nickname")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NicknameHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 40)
    private String nickname;

    @CreationTimestamp
    private LocalDateTime changedAt;

    public NicknameHistory(Long userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }
}
