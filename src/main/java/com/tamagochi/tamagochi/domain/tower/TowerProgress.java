package com.tamagochi.tamagochi.domain.tower;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 탑 진행 (유저마다 하나, 최소형). 어디까지 올랐는지, 지금 원정 중인지, 오늘 탑에서 쓴 포만감을 적는다.
 * 패배는 없다: 포만감이 떨어지면 원정이 끝나고 집으로 돌아올 뿐이다.
 */
@Entity
@Table(name = "tower_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TowerProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 돌파한 가장 높은 층 (아직 없으면 0) */
    @Column(name = "highest_floor", nullable = false)
    private int highestFloor;

    /** 지금(또는 마지막) 원정의 층과 그 층 안의 맵 번호 (1부터) */
    @Column(name = "current_floor", nullable = false)
    private int currentFloor = TowerRules.FIRST_FLOOR;

    @Column(name = "current_map", nullable = false)
    private int currentMap = 1;

    @Column(name = "on_expedition", nullable = false)
    private boolean onExpedition;

    /** 오늘(KST) 탑에서 쓴 포만감. spentDate가 오늘이 아니면 0으로 본다 */
    @Column(name = "spent_date")
    private LocalDate spentDate;

    @Column(name = "spent_today", nullable = false)
    private int spentToday;

    /** 같은 유저의 요청이 겹치면 늦은 쪽이 실패하고 다시 시도한다 (OptimisticRetry) */
    @Version
    private Long version;

    public TowerProgress(User user) {
        this.user = user;
    }

    /** 지금 출발할 수 있는 가장 높은 층: 돌파한 층 바로 위 */
    public int maxDepartFloor() {
        return highestFloor + 1;
    }

    /** 고른 층의 첫 맵에서 원정을 시작한다 */
    public void depart(int floor) {
        this.currentFloor = floor;
        this.currentMap = 1;
        this.onExpedition = true;
    }

    public void endExpedition() {
        this.onExpedition = false;
    }

    public int spentToday(LocalDate today) {
        return today.equals(spentDate) ? spentToday : 0;
    }

    public void addSpent(LocalDate today, int amount) {
        this.spentToday = spentToday(today) + Math.max(0, amount);
        this.spentDate = today;
    }

    /** 맵 통과 결과: 층을 돌파했는지, 그 층을 처음 돌파했는지 */
    public record Advance(boolean floorCleared, boolean firstClear) {}

    /** 지금 맵을 통과해 다음 맵으로 간다. 층의 마지막 맵이었으면 그 층을 돌파하고 다음 층 첫 맵으로 간다 */
    public Advance advance() {
        if (currentMap < TowerRules.MAPS_PER_FLOOR) {
            currentMap++;
            return new Advance(false, false);
        }
        boolean first = currentFloor > highestFloor;
        if (first) highestFloor = currentFloor;
        currentFloor++;
        currentMap = 1;
        return new Advance(true, first);
    }
}
