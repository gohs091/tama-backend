package com.tamagochi.tamagochi.domain.promotion;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 완전체 트레이닝 결과로 받을 프로모션(토스 포인트) 지급 기록. 트레이닝 세션 하나당 최대 한 건이다.
 * RECORDED → SENDING → PENDING → GRANTED 순으로 바뀌고, 영구 오류이거나 재시도를 다 쓰면 FAILED가 된다.
 */
@Entity
@Table(name = "promotion_grant",
        uniqueConstraints = @UniqueConstraint(name = "uk_promotion_grant_session", columnNames = "training_session_id"),
        indexes = {
                @Index(name = "idx_promotion_grant_user_date", columnList = "user_id, grant_date"),
                @Index(name = "idx_promotion_grant_status", columnList = "status, updated_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionGrant {

    /** 일시 오류로 다시 시도하는 최대 횟수. 넘으면 FAILED. */
    public static final int MAX_ATTEMPTS = 5;

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

    /** 토스 get-key로 받은 지급 키. 한 번 받으면 재시도에도 같은 키를 써서 중복 지급을 막는다. */
    @Column(length = 500)
    private String grantKey;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int attempts;

    @Column(length = 300)
    private String lastError;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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

    public void saveGrantKey(String key) {
        this.grantKey = key;
    }

    /** 토스가 지급 요청을 접수했다 */
    public void markPending() {
        this.status = PromotionGrantStatus.PENDING;
        this.lastError = null;
    }

    public void markGranted() {
        this.status = PromotionGrantStatus.GRANTED;
        this.lastError = null;
    }

    public void markFailed(String reason) {
        this.status = PromotionGrantStatus.FAILED;
        this.lastError = truncate(reason);
    }

    /** 일시 오류: 같은 키로 다시 시도하도록 되돌린다. 횟수를 다 쓰면 실패 처리한다. */
    public void retryLater(String reason) {
        this.attempts++;
        this.lastError = truncate(reason);
        this.status = this.attempts >= MAX_ATTEMPTS ? PromotionGrantStatus.FAILED : PromotionGrantStatus.RECORDED;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 300 ? s : s.substring(0, 300);
    }
}
