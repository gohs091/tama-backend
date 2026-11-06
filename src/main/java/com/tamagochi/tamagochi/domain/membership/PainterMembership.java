package com.tamagochi.tamagochi.domain.membership;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 화가 회원(토스 인앱 정기결제) 한 건. 주문(orderId)마다 한 줄이다.
 * 결제 직후 앱이 알려 준 주문을 서버가 토스에 확인해 만들고, 이후 갱신·해지·환불은 토스 웹훅으로 고친다.
 */
@Entity
@Table(name = "painter_membership", indexes = @Index(name = "idx_painter_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PainterMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false, unique = true, length = 100)
    private String orderId;

    @Column(nullable = false, length = 100)
    private String sku;

    /** 토스 구독 상태: ACTIVE · EXPIRED · IN_GRACE_PERIOD · ON_HOLD · PAUSED · REVOKED */
    @Column(nullable = false, length = 30)
    private String status;

    /** 토스가 "지금 쓸 수 있다"고 알려 준 값 */
    @Column(nullable = false)
    private boolean accessGranted;

    /** 혜택이 끝나는 시각. 웹훅이 오기 전에는 결제 시각 + 갱신 주기로 어림한다 */
    private LocalDateTime expiresAt;

    private boolean autoRenew;

    /** 환불·회수(REVOKED)된 시각. 있으면 이 주문으로 만든 혜택은 거둔다 */
    private LocalDateTime revokedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static PainterMembership start(Long userId, String orderId, String sku, LocalDateTime now, LocalDateTime expiresAt) {
        PainterMembership m = new PainterMembership();
        m.userId = userId;
        m.orderId = orderId;
        m.sku = sku;
        m.status = "ACTIVE";
        m.accessGranted = true;
        m.expiresAt = expiresAt;
        m.autoRenew = true;
        m.createdAt = now;
        m.updatedAt = now;
        return m;
    }

    /** 지금 혜택을 쓸 수 있다: 접근 허용이고, 회수되지 않았고, 만료 전 */
    public boolean isAccessible(LocalDateTime now) {
        if (revokedAt != null || !accessGranted) return false;
        return expiresAt == null || now.isBefore(expiresAt);
    }

    /** 토스 웹훅의 현재 상태로 고친다 */
    public void apply(String status, boolean accessGranted, LocalDateTime expiresAt, boolean autoRenew, LocalDateTime now) {
        this.status = status;
        this.accessGranted = accessGranted;
        if (expiresAt != null) this.expiresAt = expiresAt;
        this.autoRenew = autoRenew;
        if ("REVOKED".equals(status) && this.revokedAt == null) this.revokedAt = now;
        this.updatedAt = now;
    }
}
