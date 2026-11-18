package com.tamagochi.tamagochi.service.gear;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.gear.GearService.View;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 기본 무기 구매·장착: 열매 정가, 맨손/장착 피해, 겹친 요청에서 열매가 두 번 빠지지 않음 */
class GearServiceTest extends TowerLunchTestSupport {

    @Autowired GearService gearService;

    @Test
    @DisplayName("처음에는 맨손(시안 자이로 25·11)이고 가진 무기가 없다. 카탈로그는 두 종, 전투력 숫자는 없다")
    void startsUnarmed() {
        User user = newUser(10, 100);
        View v = gearService.get(user);
        assertThat(v.provisional()).isTrue();
        assertThat(v.catalog()).hasSize(2);
        assertThat(v.owned()).isEmpty();
        assertThat(v.equippedWeaponId()).isNull();
        assertThat(v.attacks().meleeDamage()).isEqualTo(25);
        assertThat(v.attacks().rangedDamage()).isEqualTo(11);
        assertThat(v.fruit()).isEqualTo(10);
        assertThat(v.catalog().get(0).id()).isEqualTo("wooden-brush");
        assertThat(v.catalog().get(0).fruitPrice()).isEqualTo(10);
        assertThat(v.catalog().get(0).owned()).isFalse();
        assertThat(v.catalog().get(0).canBuy()).isTrue();
        assertThat(v.catalog().get(1).id()).isEqualTo("feather-nib");
        assertThat(v.catalog().get(1).canBuy()).isTrue();
    }

    @Test
    @DisplayName("나무 붓을 사면 열매 10이 빠지고, 껴도 피해는 시안 기본(25·11) 그대로다 (추가 피해 미정)")
    void buyAndEquipWoodenBrush() {
        User user = newUser(15, 100);
        View bought = gearService.buy(user, "wooden-brush");
        assertThat(bought.fruit()).isEqualTo(5);
        assertThat(fruit(user)).isEqualTo(5);
        assertThat(bought.owned()).containsExactly("wooden-brush");
        assertThat(bought.equippedWeaponId()).isNull(); // 산 것만으로는 안 낀다
        assertThat(bought.attacks().meleeDamage()).isEqualTo(25);
        assertThat(bought.catalog().get(0).owned()).isTrue();
        assertThat(bought.catalog().get(0).canBuy()).isFalse();
        assertThat(bought.catalog().get(0).equipped()).isFalse();

        View equipped = gearService.equip(user, "wooden-brush");
        assertThat(equipped.equippedWeaponId()).isEqualTo("wooden-brush");
        assertThat(equipped.attacks().meleeDamage()).isEqualTo(25);
        assertThat(equipped.attacks().rangedDamage()).isEqualTo(11);
        assertThat(equipped.catalog().get(0).equipped()).isTrue();
        assertThat(equipped.fruit()).isEqualTo(5); // 끼기는 열매를 쓰지 않는다
    }

    @Test
    @DisplayName("두 무기를 모두 사면 바꿔 낄 수 있고, 피해는 시안 기본(25·11) 그대로다")
    void buyFeatherNibAndSwitch() {
        User user = newUser(25, 100);
        gearService.buy(user, "wooden-brush");
        View both = gearService.buy(user, "feather-nib");
        assertThat(both.fruit()).isEqualTo(5);
        assertThat(both.owned()).containsExactlyInAnyOrder("feather-nib", "wooden-brush");

        View nib = gearService.equip(user, "feather-nib");
        assertThat(nib.equippedWeaponId()).isEqualTo("feather-nib");
        assertThat(nib.attacks().meleeDamage()).isEqualTo(25);
        assertThat(nib.attacks().rangedDamage()).isEqualTo(11);

        View brush = gearService.equip(user, "wooden-brush");
        assertThat(brush.attacks().meleeDamage()).isEqualTo(25);
        assertThat(brush.attacks().rangedDamage()).isEqualTo(11);

        View bare = gearService.equip(user, null);
        assertThat(bare.equippedWeaponId()).isNull();
        assertThat(bare.attacks().meleeDamage()).isEqualTo(25);
        assertThat(bare.attacks().rangedDamage()).isEqualTo(11);
        assertThat(bare.owned()).hasSize(2); // 벗어도 가진 것은 그대로다
    }

    @Test
    @DisplayName("열매가 모자라면 사지 않고 아무것도 바뀌지 않는다")
    void notEnoughFruit() {
        User user = newUser(9, 100);
        assertCode(() -> gearService.buy(user, "wooden-brush"), "NOT_ENOUGH_FRUIT");
        View v = gearService.get(user);
        assertThat(v.owned()).isEmpty();
        assertThat(v.fruit()).isEqualTo(9);
        assertThat(v.catalog().get(0).canBuy()).isFalse();
        assertThat(fruit(user)).isEqualTo(9);
    }

    @Test
    @DisplayName("이미 가진 무기를 다시 사면 거절하고 열매를 빼지 않는다")
    void alreadyOwned() {
        User user = newUser(30, 100);
        gearService.buy(user, "wooden-brush");
        assertCode(() -> gearService.buy(user, "wooden-brush"), "ALREADY_OWNED");
        assertThat(fruit(user)).isEqualTo(20);
        assertThat(gearService.get(user).owned()).containsExactly("wooden-brush");
    }

    @Test
    @DisplayName("없는 무기, 가지지 않은 무기 장착은 거절한다")
    void unknownOrNotOwned() {
        User user = newUser(20, 100);
        assertCode(() -> gearService.buy(user, "no-such"), "UNKNOWN_GEAR");
        assertCode(() -> gearService.equip(user, "wooden-brush"), "NOT_OWNED");
        assertCode(() -> gearService.equip(user, "no-such"), "UNKNOWN_GEAR");
        assertThat(fruit(user)).isEqualTo(20);
        assertThat(gearService.get(user).equippedWeaponId()).isNull();
    }

    @Test
    @DisplayName("동시에 같은 무기를 두 번 사면 한 번만 사고 열매도 한 번만 빠진다")
    void concurrentBuySpendsOnce() throws Exception {
        User user = newUser(10, 100);
        List<String> results = together(2, () -> gearService.buy(user, "wooden-brush"));
        assertThat(results).contains("OK");
        assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
        assertThat(results).contains("ALREADY_OWNED");
        View v = gearService.get(user);
        assertThat(v.owned()).containsExactly("wooden-brush");
        assertThat(v.fruit()).isEqualTo(0);
        assertThat(fruit(user)).isEqualTo(0);
    }

    @Test
    @DisplayName("개발용 지급은 열매를 쓰지 않고, 칸이 비어 있으면 바로 낀다. 이미 있으면 그대로다")
    void devGive() {
        User user = newUser(10, 100);
        View given = gearService.devGive(user, "feather-nib");
        assertThat(given.fruit()).isEqualTo(10);
        assertThat(given.owned()).containsExactly("feather-nib");
        assertThat(given.equippedWeaponId()).isEqualTo("feather-nib");
        assertThat(given.attacks().rangedDamage()).isEqualTo(11);

        View again = gearService.devGive(user, "feather-nib");
        assertThat(again.fruit()).isEqualTo(10);
        assertThat(again.owned()).containsExactly("feather-nib");

        View other = gearService.devGive(user, "wooden-brush");
        assertThat(other.owned()).containsExactlyInAnyOrder("feather-nib", "wooden-brush");
        assertThat(other.equippedWeaponId()).isEqualTo("feather-nib"); // 이미 끼던 것은 유지
        assertThat(other.fruit()).isEqualTo(10);
    }

    @Test
    @DisplayName("id를 생략한 개발 지급은 나무 붓을 준다")
    void devGiveDefaultsToBrush() {
        User user = newUser(0, 100);
        View v = gearService.devGive(user, null);
        assertThat(v.owned()).containsExactly("wooden-brush");
        assertThat(v.equippedWeaponId()).isEqualTo("wooden-brush");
        assertThat(v.attacks().meleeDamage()).isEqualTo(25);
    }
}
