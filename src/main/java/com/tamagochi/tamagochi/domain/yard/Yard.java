package com.tamagochi.tamagochi.domain.yard;

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
import java.util.Objects;
import java.util.Set;

/**
 * 끄적이 마당 (유저마다 하나). 화분에서 딴 열매로 소품을 사서 자리에 놓고, 잉크로 산 배경을 고른다.
 * 소품은 갖고 있는 개수(props)와 자리에 놓인 것(slots)을 따로 두고, 놓이지 않은 나머지가 "보관 중"이다.
 */
@Entity
@Table(name = "yard")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Yard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private int fruit;

    /** 화분에 심은 시각. GROW가 지나면 딸 수 있다 */
    @Column(nullable = false)
    private Instant plantedAt;

    /** 고른 배경. 비어 있으면 내 진영 배경 */
    private String background;

    private LocalDate boostDate;
    private Integer boostCount;

    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "yard_prop", joinColumns = @JoinColumn(name = "yard_id"))
    @MapKeyColumn(name = "prop_id")
    @Column(name = "count", nullable = false)
    private Map<String, Integer> props = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "yard_slot", joinColumns = @JoinColumn(name = "yard_id"))
    @MapKeyColumn(name = "slot")
    @Column(name = "prop_id", nullable = false)
    private Map<String, String> slots = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "yard_background", joinColumns = @JoinColumn(name = "yard_id"))
    @Column(name = "background_id", nullable = false)
    private Set<String> backgrounds = new HashSet<>();

    /** 처음 마당: 화분이 이미 다 자라 있어서 들어오자마자 한 번 따 볼 수 있다 */
    public Yard(User user, Instant now) {
        this.user = user;
        this.plantedAt = now.minus(YardCatalog.GROW);
    }

    // ── 화분

    public Duration remaining(Instant now) {
        Duration left = Duration.between(now, plantedAt.plus(YardCatalog.GROW));
        return left.isNegative() ? Duration.ZERO : left;
    }

    public boolean ripe(Instant now) {
        return remaining(now).isZero();
    }

    public int harvest(Instant now) {
        if (!ripe(now)) throw new IllegalStateException("아직 자라는 중이에요.");
        fruit += YardCatalog.HARVEST;
        plantedAt = now;
        return YardCatalog.HARVEST;
    }

    public int boostsLeft(LocalDate today) {
        int used = today.equals(boostDate) && boostCount != null ? boostCount : 0;
        return Math.max(0, YardCatalog.BOOST_PER_DAY - used);
    }

    /** 광고 보고 바로 자라기 */
    public void boost(LocalDate today, Instant now) {
        if (ripe(now)) throw new IllegalStateException("이미 다 자랐어요. 화분을 눌러 따 주세요.");
        int left = boostsLeft(today);
        if (left <= 0) throw new IllegalStateException("오늘은 더 바로 자라게 할 수 없어요.");
        boostCount = YardCatalog.BOOST_PER_DAY - left + 1;
        boostDate = today;
        plantedAt = now.minus(YardCatalog.GROW);
    }

    // ── 소품

    public void buyProp(String propId) {
        int price = YardCatalog.propPrice(propId);
        if (fruit < price) throw new IllegalStateException("열매가 모자라요.");
        fruit -= price;
        props.merge(propId, 1, Integer::sum);
    }

    /** 갖고 있지만 자리에 놓이지 않은 개수 */
    public int stored(String propId) {
        long placed = slots.values().stream().filter(propId::equals).count();
        return props.getOrDefault(propId, 0) - (int) placed;
    }

    /** 자리에 소품을 놓는다. 이미 다른 소품이 있으면 그 소품은 보관함으로 돌아간다. propId가 비어 있으면 자리를 비운다 */
    public void place(String slot, String propId) {
        YardCatalog.requireSlot(slot);
        if (propId == null || propId.isBlank()) {
            slots.remove(slot);
            return;
        }
        YardCatalog.propPrice(propId);
        if (propId.equals(slots.get(slot))) return;
        if (stored(propId) <= 0) throw new IllegalStateException("보관 중인 소품이 아니에요.");
        slots.put(slot, propId);
    }

    // ── 배경

    public boolean ownsBackground(String background, String ownFaction) {
        return Objects.equals(background, ownFaction) || backgrounds.contains(background);
    }

    public void addBackground(String background) {
        YardCatalog.requireBackground(background);
        backgrounds.add(background);
    }

    /** 배경 고르기. 내 진영 배경을 고르거나 비우면 진영을 따른다 */
    public void useBackground(String background, String ownFaction) {
        if (background == null || background.isBlank() || background.equals(ownFaction)) {
            this.background = null;
            return;
        }
        YardCatalog.requireBackground(background);
        if (!backgrounds.contains(background)) throw new IllegalStateException("아직 갖고 있지 않은 배경이에요.");
        this.background = background;
    }
}
