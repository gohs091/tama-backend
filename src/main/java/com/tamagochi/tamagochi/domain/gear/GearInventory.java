package com.tamagochi.tamagochi.domain.gear;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.CodedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 장비 인벤토리 (유저마다 하나). 지금 범위는 기본 무기뿐이다.
 * owned는 { 아이템 id: 강화 단계 }이고, 지금은 강화가 없어 단계는 늘 0이다.
 * 무기 칸은 하나(equippedWeaponId). 비우면 맨손이다.
 */
@Entity
@Table(name = "gear_inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GearInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 같은 유저의 요청이 겹치면 늦은 쪽이 실패하고 다시 시도한다 (OptimisticRetry) */
    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gear_owned", joinColumns = @JoinColumn(name = "gear_inventory_id"))
    @MapKeyColumn(name = "item_id", length = 40)
    @Column(name = "upgrade_level", nullable = false)
    private Map<String, Integer> owned = new HashMap<>();

    @Column(name = "equipped_weapon_id", length = 40)
    private String equippedWeaponId;

    public GearInventory(User user) {
        this.user = user;
    }

    public boolean owns(String itemId) {
        return itemId != null && owned.containsKey(itemId);
    }

    public Set<String> ownedIds() {
        return Set.copyOf(owned.keySet());
    }

    /** 산 무기를 넣는다. 이미 있으면 거절한다. 강화 단계는 0으로 둔다 */
    public void add(String itemId) {
        if (owns(itemId)) throw new CodedException("ALREADY_OWNED", "이미 가지고 있어요.");
        owned.put(itemId, 0);
    }

    /**
     * 무기 칸에 낀다. id가 비면 벗어서 맨손이 된다.
     * 가진 무기만 낄 수 있다
     */
    public void equipWeapon(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            this.equippedWeaponId = null;
            return;
        }
        if (!owns(itemId)) throw new CodedException("NOT_OWNED", "가지고 있지 않은 무기예요.");
        this.equippedWeaponId = itemId;
    }

    /** 저장된 장착 id가 목록에 없으면(데이터 깨짐) 맨손으로 본다 */
    public String equippedWeaponIdOrNone() {
        return owns(equippedWeaponId) ? equippedWeaponId : null;
    }
}
