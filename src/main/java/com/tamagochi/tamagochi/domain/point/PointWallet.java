package com.tamagochi.tamagochi.domain.point;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PointWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int balance;

    @Version
    private Long version;

    // 잉크통 충전: 하루(KST) 한 번만 시작할 수 있고, 시작하면 최대 4시간까지 쌓인다. 기존 지갑에는 비어 있다(null).
    @Column(name = "bath_date")
    private java.time.LocalDate bathDate;      // 마지막으로 시작한 날짜
    @Column(name = "bath_started_at")
    private java.time.Instant bathStartedAt;   // 시작 시각
    @Column(name = "bath_settled")
    private Boolean bathSettled;               // 정산(잉크 지급)이 끝났는지

    public boolean bathRunning() {
        return bathStartedAt != null && !Boolean.TRUE.equals(bathSettled);
    }

    public void startBath(java.time.LocalDate today, java.time.Instant now) {
        this.bathDate = today;
        this.bathStartedAt = now;
        this.bathSettled = false;
    }

    public void settleBath() {
        this.bathSettled = true;
    }

    public void charge(int amount) {
        this.balance += amount;
    }

    public void use(int amount) {
        if (this.balance < amount) {
            throw new IllegalStateException("포인트가 부족합니다.");
        }
        this.balance -= amount;
    }
}
