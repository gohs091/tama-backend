package com.tamagochi.tamagochi.domain.lunch;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.CodedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 도시락 보관 (유저마다 하나). 만든 도시락은 찬장(cupboard)에 쌓이고, 탑에 가져갈 것만 가방(bag)에 옮겨 담는다.
 * 가방 1칸 = 도시락 1개. 도시락은 상하지 않고, 원정이 끝나도 가방에 남은 것은 가방에 그대로 있다.
 */
@Entity
@Table(name = "pantry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pantry {

    public static final int DEFAULT_BAG_SLOTS = 2;
    public static final int MAX_BAG_SLOTS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 기본 가방 칸 수. 보너스(NPC·가구)는 여기에 더하지 않고 bagCapacity에서 합친다 */
    @Column(name = "bag_slots", nullable = false)
    private int bagSlots = DEFAULT_BAG_SLOTS;

    /** 같은 유저의 요청이 겹치면 늦은 쪽이 실패하고 다시 시도한다 (OptimisticRetry). 찬장·가방만 바뀌어도 오른다 */
    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pantry_cupboard", joinColumns = @JoinColumn(name = "pantry_id"))
    @MapKeyColumn(name = "lunch_id", length = 40)
    @Column(name = "count", nullable = false)
    private Map<String, Integer> cupboard = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pantry_bag", joinColumns = @JoinColumn(name = "pantry_id"))
    @MapKeyColumn(name = "lunch_id", length = 40)
    @Column(name = "count", nullable = false)
    private Map<String, Integer> bag = new HashMap<>();

    public Pantry(User user) {
        this.user = user;
    }

    /**
     * 가방 칸 수 = 기본 칸 + 보너스 (상한 MAX_BAG_SLOTS). 칸을 늘리는 것(NPC·가구)은 보너스를 넘겨서 붙인다.
     * 지금은 보너스를 주는 것이 없어 LunchService.bagBonus가 0을 넘긴다
     */
    public int bagCapacity(int bonusSlots) {
        return Math.min(MAX_BAG_SLOTS, bagSlots + Math.max(0, bonusSlots));
    }

    /** 가방에 든 도시락 개수 (쓴 칸 수) */
    public int bagUsed() {
        return bag.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void addToCupboard(String lunchId, int count) {
        if (count > 0) cupboard.merge(lunchId, count, Integer::sum);
    }

    /** 찬장 → 가방 */
    public void pack(String lunchId, int count, int capacity) {
        if (cupboard.getOrDefault(lunchId, 0) < count) throw new CodedException("NOT_IN_CUPBOARD", "찬장에 그만큼 없어요.");
        if (bagUsed() + count > capacity) throw new CodedException("BAG_FULL", "가방에 자리가 없어요.");
        take(cupboard, lunchId, count);
        bag.merge(lunchId, count, Integer::sum);
    }

    /** 가방 → 찬장 */
    public void unpack(String lunchId, int count) {
        if (bag.getOrDefault(lunchId, 0) < count) throw new CodedException("NOT_IN_BAG", "가방에 그만큼 없어요.");
        take(bag, lunchId, count);
        cupboard.merge(lunchId, count, Integer::sum);
    }

    /** 가방에서 하나 꺼낸다 (먹기) */
    public void takeFromBag(String lunchId) {
        if (bag.getOrDefault(lunchId, 0) < 1) throw new CodedException("NOT_IN_BAG", "가방에 없는 도시락이에요.");
        take(bag, lunchId, 1);
    }

    private static void take(Map<String, Integer> from, String lunchId, int count) {
        int left = from.get(lunchId) - count;
        if (left > 0) from.put(lunchId, left);
        else from.remove(lunchId);
    }
}
