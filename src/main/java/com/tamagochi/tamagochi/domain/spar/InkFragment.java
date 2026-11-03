package com.tamagochi.tamagochi.domain.spar;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 스파링에서 모은 잉크 조각. 잉크마다 한 줄이고, 다 모으면 보관함(LandInk)에 잉크가 들어간다 */
@Entity
@Table(name = "ink_fragment", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ink_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InkFragment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ink_id", nullable = false)
    private String inkId;

    @Column(nullable = false)
    private int count;

    @Column(nullable = false)
    private Instant updatedAt;

    public InkFragment(User user, String inkId) {
        this.user = user;
        this.inkId = inkId;
        this.updatedAt = Instant.now();
    }

    public void add(Instant now) {
        this.count++;
        this.updatedAt = now;
    }
}
