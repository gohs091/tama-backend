package com.tamagochi.tamagochi.domain.usage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 하루에 사용자마다 한 줄: 그날(한국 날짜) 앱을 열어 끄적이 상태를 읽어 갔다. 일별 활성 · 재방문율을 내려고 남긴다 (UsageRecorder).
 */
@Entity
@Table(name = "user_daily_visit",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_daily_visit", columnNames = {"user_id", "visit_date"}),
        indexes = @Index(name = "idx_user_daily_visit_date", columnList = "visit_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 한국 날짜 */
    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    /** 그날 처음 본 때 (한국 시간) */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UserDailyVisit(Long userId, LocalDate visitDate, LocalDateTime createdAt) {
        this.userId = userId;
        this.visitDate = visitDate;
        this.createdAt = createdAt;
    }
}
