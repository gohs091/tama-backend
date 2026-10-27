package com.tamagochi.tamagochi.domain.land;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 보관함에 들어간 잉크 한 개. 같은 잉크는 한 번만 들어간다 */
@Entity
@Table(name = "land_ink", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ink_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandInk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ink_id", nullable = false)
    private String inkId;

    @Column(nullable = false)
    private Instant foundAt;

    public LandInk(User user, String inkId, Instant foundAt) {
        this.user = user;
        this.inkId = inkId;
        this.foundAt = foundAt;
    }
}
