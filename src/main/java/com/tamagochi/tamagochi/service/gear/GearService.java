package com.tamagochi.tamagochi.service.gear;

import com.tamagochi.tamagochi.domain.gear.GearCatalog;
import com.tamagochi.tamagochi.domain.gear.GearCatalog.Attacks;
import com.tamagochi.tamagochi.domain.gear.GearCatalog.Weapon;
import com.tamagochi.tamagochi.domain.gear.GearInventory;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.domain.yard.Yard;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.gear.GearInventoryRepository;
import com.tamagochi.tamagochi.repository.yard.YardRepository;
import com.tamagochi.tamagochi.service.common.OptimisticRetry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * 기본 무기: 열매 정가로 사고(buy), 무기 칸에 낀다(equip).
 * 확률 드롭·강화·잉크 결제는 없다. 딱따구리 구출 가드는 아직 붙이지 않는다 (오쿠보 가게 N-5 훅: Weapon.unlockNpc).
 * 모든 동작은 한 트랜잭션이고, 같은 유저의 요청이 겹치면 다시 한다 (OptimisticRetry) — 열매가 두 번 빠지거나 무기가 공짜로 늘지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GearService {

    /**
     * 카탈로그 한 줄. fruitPrice는 정가, canBuy는 아직 없고 열매가 값이 될 때 true.
     * meleeDamage·rangedDamage는 이 무기를 꼈을 때 바뀌는 쪽만 채운다 (안 바뀌는 쪽은 null).
     * 전투력 숫자는 없다
     */
    public record CatalogItem(String id, String emoji, String name, String slot, String style, String tier,
                              int fruitPrice, int unlockFloor, String unlockNpc,
                              Integer meleeDamage, Integer rangedDamage,
                              boolean owned, boolean equipped, boolean canBuy, String note) {}

    /**
     * 장비 전체 상태. attacks는 지금 장착(없으면 맨손)을 반영한 근접·원거리 기본 피해.
     * equippedWeaponId는 맨손이면 null. catalog는 기본 무기 두 종
     */
    public record View(boolean provisional, String note, List<CatalogItem> catalog, List<String> owned,
                       String equippedWeaponId, Attacks attacks, int fruit) {}

    private final GearInventoryRepository gearInventoryRepository;
    private final YardRepository yardRepository;
    private final OptimisticRetry retry;
    private final Clock clock;

    public View get(User user) {
        return retry.run(() -> view(user, inventory(user)));
    }

    /** 열매 정가로 기본 무기를 산다. 이미 가지고 있으면 거절하고 열매를 빼지 않는다 */
    public View buy(User user, String itemId) {
        return retry.run(() -> {
            Weapon weapon = weapon(itemId);
            GearInventory inv = inventory(user);
            if (inv.owns(weapon.id())) throw new CodedException("ALREADY_OWNED", "이미 가지고 있어요.");
            Yard yard = yard(user);
            int cost = weapon.price().fruit();
            if (yard.getFruit() < cost) throw new CodedException("NOT_ENOUGH_FRUIT", "열매가 모자라요.");
            yard.spendFruit(cost);
            inv.add(weapon.id());
            return view(user, inv);
        });
    }

    /**
     * 무기 칸에 낀다. id가 비면 벗어서 맨손이 된다.
     * 가진 무기만 낄 수 있다
     */
    public View equip(User user, String itemId) {
        return retry.run(() -> {
            String id = itemId == null || itemId.isBlank() ? null : weapon(itemId).id();
            GearInventory inv = inventory(user);
            inv.equipWeapon(id);
            return view(user, inv);
        });
    }

    /** 로컬 개발 전용 (DevController): 열매 없이 무기를 준다. 이미 있으면 그대로다. 무기 칸이 비어 있으면 바로 낀다 */
    public View devGive(User user, String itemId) {
        return retry.run(() -> {
            Weapon weapon = itemId == null || itemId.isBlank()
                    ? GearCatalog.weapon("wooden-brush").orElseThrow()
                    : weapon(itemId);
            GearInventory inv = inventory(user);
            if (!inv.owns(weapon.id())) inv.add(weapon.id());
            if (inv.equippedWeaponIdOrNone() == null) inv.equipWeapon(weapon.id());
            return view(user, inv);
        });
    }

    private static Weapon weapon(String id) {
        return GearCatalog.weapon(id).orElseThrow(() -> new CodedException("UNKNOWN_GEAR", "없는 무기예요."));
    }

    private GearInventory inventory(User user) {
        return gearInventoryRepository.findByUserId(user.getId())
                .orElseGet(() -> gearInventoryRepository.save(new GearInventory(user)));
    }

    private Yard yard(User user) {
        return yardRepository.findByUserId(user.getId())
                .orElseGet(() -> yardRepository.save(new Yard(user, clock.instant())));
    }

    private View view(User user, GearInventory inv) {
        String equipped = inv.equippedWeaponIdOrNone();
        List<String> owned = new ArrayList<>(inv.ownedIds());
        owned.sort(String::compareTo);
        int fruit = yard(user).getFruit();
        List<CatalogItem> catalog = GearCatalog.weapons().stream().map(w -> {
            boolean has = inv.owns(w.id());
            return new CatalogItem(w.id(), w.emoji(), w.name(), w.slot().name(), w.style().name(), w.tier().name(),
                    w.price().fruit(), w.unlockFloor(), w.unlockNpc(), w.meleeDamage(), w.rangedDamage(),
                    has, w.id().equals(equipped), !has && fruit >= w.price().fruit(), w.note());
        }).toList();
        return new View(GearCatalog.provisional(), GearCatalog.note(), catalog, owned, equipped,
                GearCatalog.attacks(equipped), fruit);
    }
}
