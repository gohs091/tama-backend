package com.tamagochi.tamagochi.domain.land;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 유저마다 하나인 모험 기록. 지금 모험 중이면 어느 구역을 언제부터 탐험하는지,
 * 끝났으면 마지막 결과(정산한 잉크, 끝난 이유)를 남긴다. 시간은 서버 시계로만 잰다.
 */
@Entity
@Table(name = "adventure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Adventure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Version
    private Long version;

    private String regionId;
    private Instant startedAt;
    private boolean running;
    private boolean bonus;              // 하루 첫 모험이라 처음 3분 잉크 줍기 보너스가 있는지
    private int exploredAtStart;        // 시작할 때 이 구역을 누적 몇 분 탐험했었는지
    private LocalDate lastStartDate;    // 마지막으로 모험을 시작한 날 (하루 첫 모험 판단)

    // 마지막으로 끝난 모험의 결과 (화면에 한 번 알려 주기 위해 남긴다)
    private int lastMinutes;
    private int lastNetInk;
    @Enumerated(EnumType.STRING)
    private AdventureRules.EndReason lastEndReason;

    public Adventure(User user) {
        this.user = user;
    }

    public void start(String regionId, Instant now, LocalDate today, int exploredAtStart) {
        this.bonus = !today.equals(this.lastStartDate);
        this.regionId = regionId;
        this.startedAt = now;
        this.running = true;
        this.exploredAtStart = exploredAtStart;
        this.lastStartDate = today;
    }

    public void finish(int minutes, int netInk, AdventureRules.EndReason reason) {
        this.running = false;
        this.lastMinutes = minutes;
        this.lastNetInk = netInk;
        this.lastEndReason = reason;
    }
}
