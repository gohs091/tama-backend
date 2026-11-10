package com.tamagochi.tamagochi.domain.farm;

import com.tamagochi.tamagochi.domain.farm.FarmCatalog.Crop;
import com.tamagochi.tamagochi.domain.farm.FarmCatalog.Quest;
import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 끄적이 농장 (유저마다 하나). 밭 9칸, 물, 오늘의 퀘스트, 광고 물 횟수.
 * 자란 정도는 들를 때마다 지난 시간만큼 한꺼번에 더한다(settle). 물이 있던 시간은 WATER_SPEED배로 센다.
 * 물은 "언제까지 있나(waterUntil)" 하나로 둔다. 물을 바꾸기 전에 항상 settle 하므로 지난 구간 계산이 맞는다.
 */
@Entity
@Table(name = "farm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Farm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 자란 정도를 마지막으로 더한 시각 */
    @Column(nullable = false)
    private Instant settledAt;

    /** 물이 이 시각까지 있다 (지났으면 물 없음) */
    @Column(nullable = false)
    private Instant waterUntil;

    private LocalDate questDate;
    private LocalDate adDate;
    private Integer adCount;
    private boolean bonusClaimed;

    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_plot", joinColumns = @JoinColumn(name = "farm_id"))
    @MapKeyColumn(name = "plot_index")
    private Map<Integer, FarmPlot> plots = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_open", joinColumns = @JoinColumn(name = "farm_id"))
    @Column(name = "plot_index", nullable = false)
    private Set<Integer> open = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_quest", joinColumns = @JoinColumn(name = "farm_id"))
    @MapKeyColumn(name = "quest")
    @Column(name = "count", nullable = false)
    private Map<String, Integer> questCounts = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_quest_claimed", joinColumns = @JoinColumn(name = "farm_id"))
    @Column(name = "quest", nullable = false)
    private Set<String> questClaimed = new HashSet<>();

    public Farm(User user, Instant now) {
        this.user = user;
        this.settledAt = now;
        this.waterUntil = now;
        this.open.addAll(FarmCatalog.INITIAL_OPEN);
    }

    // ── 자라기

    /** 지난 시간만큼 자란 정도를 더한다. 물이 있던 구간은 빠르게 */
    public void settle(Instant now) {
        if (!now.isAfter(settledAt)) return;
        long dt = Duration.between(settledAt, now).toSeconds();
        long wet = Math.max(0, Math.min(dt, Duration.between(settledAt, waterUntil).toSeconds()));
        long grow = dt + wet * (FarmCatalog.WATER_SPEED - 1);
        plots.replaceAll((i, p) -> p.getCrop() == null ? p : p.grownBy(grow, need(p)));
        settledAt = now;
    }

    public FarmPlot plot(int i) {
        FarmCatalog.requirePlot(i);
        return plots.getOrDefault(i, FarmPlot.empty());
    }

    public boolean isOpen(int i) {
        return open.contains(i);
    }

    public boolean ripe(int i) {
        FarmPlot p = plot(i);
        return p.getCrop() != null && p.getGrown() >= need(p);
    }

    /** 다 자랄 때까지 실제로 남은 시간(초). 남은 물 동안은 빠르게 자란다고 보고 센다 */
    public long remainingSeconds(int i, Instant now) {
        FarmPlot p = plot(i);
        if (p.getCrop() == null) return 0;
        long left = Math.max(0, need(p) - p.getGrown());
        long water = waterSeconds(now), fast = water * FarmCatalog.WATER_SPEED;
        return left <= fast ? (left + FarmCatalog.WATER_SPEED - 1) / FarmCatalog.WATER_SPEED : water + (left - fast);
    }

    public double progress(int i) {
        FarmPlot p = plot(i);
        return p.getCrop() == null ? 0 : Math.min(1.0, (double) p.getGrown() / need(p));
    }

    /** 다음 심기의 번호 (씨앗값 결제 키) */
    public int nextSeq(int i) {
        return plot(i).getSeq() + 1;
    }

    public void plant(int i, String cropId, Instant now) {
        FarmCatalog.crop(cropId);
        if (!isOpen(i)) throw new IllegalStateException("아직 열지 않은 밭이에요.");
        settle(now);
        plots.put(i, plot(i).planted(cropId));
    }

    public void clear(int i) {
        if (!isOpen(i)) throw new IllegalStateException("아직 열지 않은 밭이에요.");
        plots.put(i, plot(i).cleared());
    }

    /** 다 자란 칸을 거둔다. replant면 같은 작물을 처음부터 다시 키우고, 아니면 칸을 비운다. 받은 열매 수를 돌려준다 */
    public int harvest(int i, boolean replant, Instant now) {
        settle(now);
        if (!ripe(i)) throw new IllegalStateException("아직 자라는 중이에요.");
        FarmPlot p = plot(i);
        plots.put(i, replant ? p.planted(p.getCrop()) : p.cleared());
        return FarmCatalog.crop(p.getCrop()).fruit();
    }

    // ── 밭 늘리기

    /** 다음 칸을 여는 값(열매). 다 열었으면 -1 */
    public int unlockPrice() {
        int opened = open.size() - FarmCatalog.INITIAL_OPEN.size();
        return opened < FarmCatalog.UNLOCK_PRICES.size() ? FarmCatalog.UNLOCK_PRICES.get(opened) : -1;
    }

    public void unlock(int i) {
        FarmCatalog.requirePlot(i);
        if (isOpen(i)) throw new IllegalStateException("이미 열린 밭이에요.");
        open.add(i);
    }

    // ── 물

    public long waterSeconds(Instant now) {
        return Math.max(0, Duration.between(now, waterUntil).toSeconds());
    }

    public void addWater(Duration amount, Instant now) {
        settle(now);
        Instant base = waterUntil.isAfter(now) ? waterUntil : now;
        Instant until = base.plus(amount), max = now.plus(FarmCatalog.WATER_MAX);
        waterUntil = until.isAfter(max) ? max : until;
    }

    public int adsLeft(LocalDate today) {
        int used = today.equals(adDate) && adCount != null ? adCount : 0;
        return Math.max(0, FarmCatalog.AD_PER_DAY - used);
    }

    /** 광고를 본 뒤: 물을 받는다 (하루 횟수로 막는다) */
    public void adWater(LocalDate today, Instant now) {
        int left = adsLeft(today);
        if (left <= 0) throw new IllegalStateException("오늘은 광고로 물을 더 받을 수 없어요.");
        adCount = FarmCatalog.AD_PER_DAY - left + 1;
        adDate = today;
        addWater(FarmCatalog.AD_WATER, now);
    }

    // ── 오늘의 퀘스트

    private void rollQuestDay(LocalDate today) {
        if (today.equals(questDate)) return;
        questDate = today;
        questCounts.clear();
        questClaimed.clear();
        bonusClaimed = false;
    }

    public boolean questActive(Quest q, LocalDate today) {
        return !q.side || FarmCatalog.sideQuest(today).filter(q::equals).isPresent();
    }

    public int questCount(Quest q, LocalDate today) {
        return today.equals(questDate) ? Math.min(q.need, questCounts.getOrDefault(q.name(), 0)) : 0;
    }

    public boolean questClaimed(Quest q, LocalDate today) {
        return today.equals(questDate) && questClaimed.contains(q.name());
    }

    /** 앱에서 한 행동을 센다 (오늘 없는 곁다리는 세지 않는다) */
    public void progress(Quest q, int n, LocalDate today) {
        if (n <= 0 || !questActive(q, today)) return;
        rollQuestDay(today);
        questCounts.merge(q.name(), n, (a, b) -> Math.min(q.need, a + b));
    }

    /** 다 한 퀘스트의 보상을 받는다: 물(+곁다리는 열매). 받은 열매 수를 돌려준다 */
    public int claim(Quest q, LocalDate today, Instant now) {
        rollQuestDay(today);
        if (!questActive(q, today)) throw new IllegalStateException("오늘은 없는 퀘스트예요.");
        if (questClaimed.contains(q.name())) throw new IllegalStateException("이미 받았어요.");
        if (questCount(q, today) < q.need) throw new IllegalStateException("아직 다 하지 않았어요.");
        questClaimed.add(q.name());
        addWater(q.water, now);
        return q.fruit;
    }

    public boolean bonusReady(LocalDate today) {
        return FarmCatalog.DAILY.stream().allMatch(q -> questClaimed(q, today)) && !(today.equals(questDate) && bonusClaimed);
    }

    public boolean bonusClaimed(LocalDate today) {
        return today.equals(questDate) && bonusClaimed;
    }

    /** 매일 퀘스트를 다 받으면: 심어 둔 모든 밭이 바로 다 자란다 */
    public void claimBonus(LocalDate today, Instant now) {
        if (!bonusReady(today)) throw new IllegalStateException("오늘의 퀘스트를 먼저 다 받아 주세요.");
        settle(now);
        bonusClaimed = true;
        plots.replaceAll((i, p) -> p.getCrop() == null ? p : p.full(need(p)));
    }

    private static long need(FarmPlot p) {
        Crop c = FarmCatalog.crop(p.getCrop());
        return c.grow().toSeconds();
    }
}
