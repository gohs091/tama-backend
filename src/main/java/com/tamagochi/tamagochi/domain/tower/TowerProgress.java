package com.tamagochi.tamagochi.domain.tower;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 탑 진행 (유저마다 하나, 최소형). 어디까지 올랐는지, 지금 원정 중인지, 오늘 탑에서 쓴 포만감을 적는다.
 * 패배는 없다: 포만감이 떨어지거나 제한 시간이 지나면 원정이 끝나고 집으로 돌아올 뿐이다.
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

    /** 지금 원정을 떠난 시각 (KST). 원정 중이 아니면 null. 여기서 제한 시간이 지나면 집으로 돌아온 것으로 본다 */
    @Column(name = "departed_at")
    private LocalDateTime departedAt;

    /**
     * 지금 원정의 id (UUID 문자열). 출발할 때 새로 받고, 귀가·시간 만료 때 비운다.
     * 이 칸이 생기기 전에 떠난 원정은 원정 중이어도 null이다 (그 원정은 runId 없이 이어 간다)
     */
    @Column(name = "run_id", length = 36)
    private String runId;

    /**
     * 지금 맵에 들어온 시각 (KST). 출발할 때와 맵을 하나 통과한 뒤에 적는다. 원정 중이 아니면 null.
     * 이 칸이 생기기 전에 떠난 원정은 null이고, 그때는 다음 통과를 시간 검사 없이 받은 뒤부터 시계가 돈다
     */
    @Column(name = "map_entered_at")
    private LocalDateTime mapEnteredAt;

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

    /** 지금 출발할 수 있는 가장 높은 층: 돌파한 층 바로 위. 공개 층 상한을 넘지 않는다 (맨 위 층을 깨면 그 층만 다시 갈 수 있다) */
    public int maxDepartFloor() {
        return Math.min(highestFloor + 1, TowerFloorCatalog.openFloorCap());
    }

    /** 고른 층의 첫 맵에서 원정을 시작한다. now는 출발 시각(= 첫 맵에 들어온 시각), runId는 이번 원정의 id */
    public void depart(int floor, LocalDateTime now, String runId) {
        this.currentFloor = floor;
        this.currentMap = 1;
        this.onExpedition = true;
        this.departedAt = now;
        this.runId = runId;
        this.mapEnteredAt = now;
    }

    /** 원정을 접는다. 원정 id와 맵 시계도 비운다 (층·맵·가방은 그대로) */
    public void endExpedition() {
        this.onExpedition = false;
        this.departedAt = null;
        this.runId = null;
        this.mapEnteredAt = null;
    }

    /**
     * 지금 맵을 통과할 수 있을 때까지 남은 초 (올림). 원정 중이 아니면 null, 바로 통과할 수 있으면 0.
     * 맵에 들어온 시각이 없는 원정(그 칸이 생기기 전에 떠난 원정)은 0이다
     */
    public Long secondsUntilClear(LocalDateTime now, int minMapSeconds) {
        if (!onExpedition) return null;
        if (mapEnteredAt == null || minMapSeconds <= 0) return 0L;
        long leftMillis = Duration.between(now, mapEnteredAt.plusSeconds(minMapSeconds)).toMillis();
        return leftMillis <= 0 ? 0L : (leftMillis + 999) / 1000;
    }

    /**
     * 원정 제한 시간이 지났으면 집으로 돌려보낸다 (가방·진행은 그대로). 이번에 돌려보냈으면 true.
     * 출발 시각이 없는 원정(이 기록이 생기기 전에 떠난 원정)은 지금 떠난 것으로 보고 시각만 적는다
     */
    public boolean expireIfTimedOut(LocalDateTime now) {
        if (!onExpedition) return false;
        if (departedAt == null) {
            departedAt = now;
            return false;
        }
        if (now.isBefore(deadline())) return false;
        endExpedition();
        return true;
    }

    /** 원정 제한 시간까지 남은 초. 원정 중이 아니면 null */
    public Long secondsLeft(LocalDateTime now) {
        if (!onExpedition) return null;
        if (departedAt == null) return TowerRules.EXPEDITION_TIMEOUT_MINUTES * 60L;
        return Math.max(0, Duration.between(now, deadline()).toSeconds());
    }

    private LocalDateTime deadline() {
        return departedAt.plusMinutes(TowerRules.EXPEDITION_TIMEOUT_MINUTES);
    }

    public int spentToday(LocalDate today) {
        return today.equals(spentDate) ? spentToday : 0;
    }

    public void addSpent(LocalDate today, int amount) {
        this.spentToday = spentToday(today) + Math.max(0, amount);
        this.spentDate = today;
    }

    /** 맵 통과 결과: 층을 돌파했는지, 그 층을 처음 돌파했는지, 맨 위 공개 층을 돌파해 원정이 끝났는지 */
    public record Advance(boolean floorCleared, boolean firstClear, boolean topCleared) {}

    /**
     * 지금 맵을 통과해 다음 맵으로 간다. 그 층의 마지막 맵이었으면 그 층을 돌파하고 다음 층 첫 맵으로 간다.
     * 층의 맵 수는 층마다 다르다 (TowerFloorCatalog). currentMap이 그 층 맵 수보다 크면(나중에 맵 수를 줄였을 때)
     * 그 층을 돌파한 것으로 본다. 맨 위 공개 층을 돌파하면 더 올라가지 않고 원정을 끝낸다.
     * 한 번에 한 칸만 간다. now는 다음 맵에 들어온 시각으로 적는다 (그 맵의 최소 체류 시간이 여기서부터 돈다)
     */
    public Advance advance(LocalDateTime now) {
        if (currentMap < TowerFloorCatalog.mapsPerFloor(currentFloor)) {
            currentMap++;
            mapEnteredAt = now;
            return new Advance(false, false, false);
        }
        int cap = TowerFloorCatalog.openFloorCap();
        boolean first = currentFloor > highestFloor;
        if (first) highestFloor = Math.min(currentFloor, cap);
        if (currentFloor >= cap) {
            endExpedition();
            return new Advance(true, first, true);
        }
        currentFloor++;
        currentMap = 1;
        mapEnteredAt = now;
        return new Advance(true, first, false);
    }
}
