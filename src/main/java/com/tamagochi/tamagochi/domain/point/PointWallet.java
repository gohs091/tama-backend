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
