package com.tamagochi.tamagochi.domain.land;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저가 구역마다 누적으로 몇 분 탐험했는지. 잉크 발견은 이 값으로 정해진다 */
@Entity
@Table(name = "land_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "region_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "region_id", nullable = false)
    private String regionId;

    private int exploredMinutes;

    public LandProgress(User user, String regionId) {
        this.user = user;
        this.regionId = regionId;
    }

    public void explore(int minutes) {
        this.exploredMinutes += Math.max(0, minutes);
    }
}
