package com.tamagochi.tamagochi.domain.tower;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 탑 장비 보관 (유저마다 하나). 가방과 낀 장비를 JSON 한 덩어리로 둔다 (모양은 TowerInventoryService.Data).
 * 장비는 탑에서 주운 것이고, 원정이 끝나도 남는다.
 * 아이템 한 개 한 개를 행으로 두지 않는 까닭: 옵션이 아이템마다 다르고(스탯 열쇠 → 값), 늘 통째로 읽고 통째로 쓴다.
 */
@Entity
@Table(name = "tower_inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TowerInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** { "bag": [...], "equip": { "weapon": …, "head": …, "body": …, "charm1": …, "charm2": … } } */
    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data = "{}";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 같은 유저의 저장이 겹치면 늦은 쪽이 실패하고 다시 시도한다 (OptimisticRetry) */
    @Version
    private Long version;

    public TowerInventory(User user) {
        this.user = user;
    }

    public void replace(String json, LocalDateTime now) {
        this.data = json;
        this.updatedAt = now;
    }
}
