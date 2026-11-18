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
 * 도시락은 한 종류뿐이라 개수만 센다. 가방 용량 = 담을 수 있는 최대 개수.
 * 도시락은 상하지 않고, 원정이 끝나도 가방에 남은 것은 가방에 그대로 있다.
 *
 * 저장은 예전 그대로 { lunch_id: 개수 } 맵이다 (pantry_cupboard / pantry_bag). 지금은 키가 LunchCatalog.id() 하나뿐이고,
 * 예전 세 종류 키(riceball·hearty·feast)가 남아 있으면 migrateLegacy가 한 번 지금 도시락 개수로 바꾼다.
 */
@Entity
@Table(name = "pantry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pantry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 기본 가방 용량. 보너스(NPC·가구)는 여기에 더하지 않고 bagCapacity에서 합친다 */
    @Column(name = "bag_slots", nullable = false)
    private int bagSlots = LunchCatalog.defaultBagSlots();

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
     * 가방 용량 = 기본 + 보너스 (상한 LunchCatalog.maxBagSlots). 용량을 늘리는 것(NPC·가구)은 보너스를 넘겨서 붙인다.
     * 지금은 햄스터를 구한 뒤의 한 칸뿐이다 (LunchService.bagBonus)
     */
    public int bagCapacity(int bonusSlots) {
        return Math.min(LunchCatalog.maxBagSlots(), bagSlots + Math.max(0, bonusSlots));
    }

    /** 찬장의 도시락 개수. 아직 바꾸지 않은 예전 도시락이 있어도 지금 도시락 개수로 센다 */
    public int cupboardCount() {
        return units(cupboard);
    }

    /** 가방에 든 도시락 개수. 아직 바꾸지 않은 예전 도시락이 있어도 지금 도시락 개수로 센다 */
    public int bagUsed() {
        return units(bag);
    }

    /** 예전 종류 키가 남아 있는가 (지금 도시락 id가 아닌 키) */
    public boolean hasLegacy() {
        return hasOtherKey(cupboard) || hasOtherKey(bag);
    }

    /**
     * 예전 세 종류를 지금 도시락 개수로 바꾼다 (LunchCatalog.unitsPer: 포만감을 잃지 않게 올림). 같은 칸의 여러 종류는 합치고,
     * 예전 키는 남기지 않는다. 예전 키가 없으면 아무것도 하지 않으니 여러 번 불러도 한 번만 바뀐다.
     * 바꾼 뒤 가방이 용량을 넘으면 넘는 만큼은 찬장으로 옮긴다 (사라지는 것은 없다).
     * 바꿨으면 true
     */
    public boolean migrateLegacy(int capacity) {
        if (!hasLegacy()) return false;
        int inCupboard = units(cupboard);
        int inBag = units(bag);
        int overflow = Math.max(0, inBag - Math.max(0, capacity));
        set(cupboard, inCupboard + overflow);
        set(bag, inBag - overflow);
        return true;
    }

    public void addToCupboard(int count) {
        if (count > 0) cupboard.merge(LunchCatalog.id(), count, Integer::sum);
    }

    /** 찬장 → 가방 */
    public void pack(int count, int capacity) {
        if (count(cupboard) < count) throw new CodedException("NOT_IN_CUPBOARD", "찬장에 그만큼 없어요.");
        if (bagUsed() + count > capacity) throw new CodedException("BAG_FULL", "가방에 자리가 없어요.");
        set(cupboard, count(cupboard) - count);
        bag.merge(LunchCatalog.id(), count, Integer::sum);
    }

    /** 가방 → 찬장 */
    public void unpack(int count) {
        if (count(bag) < count) throw new CodedException("NOT_IN_BAG", "가방에 그만큼 없어요.");
        set(bag, count(bag) - count);
        cupboard.merge(LunchCatalog.id(), count, Integer::sum);
    }

    /** 가방에서 하나 꺼낸다 (먹기) */
    public void takeFromBag() {
        if (count(bag) < 1) throw new CodedException("NOT_IN_BAG", "가방에 도시락이 없어요.");
        set(bag, count(bag) - 1);
    }

    private static int count(Map<String, Integer> of) {
        return Math.max(0, of.getOrDefault(LunchCatalog.id(), 0));
    }

    private static int units(Map<String, Integer> of) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : of.entrySet()) {
            int n = e.getValue() == null ? 0 : Math.max(0, e.getValue());
            sum += n * LunchCatalog.unitsPer(e.getKey());
        }
        return sum;
    }

    private static boolean hasOtherKey(Map<String, Integer> of) {
        return of.keySet().stream().anyMatch(k -> !LunchCatalog.id().equals(k));
    }

    /** 맵을 지금 도시락 키 하나로 맞춘다 (0이면 비운다) */
    private static void set(Map<String, Integer> of, int count) {
        of.keySet().removeIf(k -> !LunchCatalog.id().equals(k));
        if (count > 0) of.put(LunchCatalog.id(), count);
        else of.remove(LunchCatalog.id());
    }
}
