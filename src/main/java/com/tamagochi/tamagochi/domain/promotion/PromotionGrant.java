package com.tamagochi.tamagochi.domain.promotion;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 완전체 트레이닝 결과로 받을 프로모션(토스 포인트) 지급 기록. 트레이닝 세션 하나당 최대 한 건이다.
 * 지금은 기록만 남기고(RECORDED) 실제 지급은 하지 않는다. 지급 연동 후 GRANTED/FAILED로 바뀐다.
 */
@Entity
@Table(name = "promotion_grant",
        uniqueConstraints = @UniqueConstraint(name = "uk_promotion_grant_session", columnNames = "training_session_id"),
        indexes = @Index(name = "idx_promotion_grant_user_date", columnList = "user_id, grant_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "training_session_id", nullable = false, length = 36)
    private String trainingSessionId;

    @Column(name = "grant_date", nullable = false)
    private LocalDate grantDate; // 하루 상한을 셀 때 쓰는 날짜(KST)

    @Column(nullable = false)
    private int clickCount;

    @Column(nullable = false)
    private int tier;

    @Column(nullable = false)
    private int amount; // 토스 포인트(원)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionGrantStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public PromotionGrant(User user, String trainingSessionId, LocalDate grantDate,
                          int clickCount, int tier, int amount) {
        this.user = user;
        this.trainingSessionId = trainingSessionId;
        this.grantDate = grantDate;
        this.clickCount = clickCount;
        this.tier = tier;
        this.amount = amount;
        this.status = PromotionGrantStatus.RECORDED;
    }
}
