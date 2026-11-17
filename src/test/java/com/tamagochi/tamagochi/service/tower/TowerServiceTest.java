package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.tower.TowerRules;
import com.tamagochi.tamagochi.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TowerServiceTest extends TowerLunchTestSupport {

    @Test
    @DisplayName("규칙 값: 맵 1장 = 포만감 35, 한 층 = 맵 3장")
    void rules() {
        assertThat(TowerRules.MAP_SATIETY_COST).isEqualTo(35);
        assertThat(TowerRules.MAPS_PER_FLOOR).isEqualTo(3);
    }

    @Test
    @DisplayName("맵을 한 번 통과하면 포만감 35를 쓰고 다음 맵으로 간다")
    void clearMapSpendsSatiety() {
        User user = newUser(0, 100);
        TowerService.View v = towerService.depart(user, null);
        assertThat(v.onExpedition()).isTrue();
        assertThat(v.currentFloor()).isEqualTo(1);
        assertThat(v.currentMap()).isEqualTo(1);

        v = towerService.clearMap(user);
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.result().spentSatiety()).isEqualTo(35);
        assertThat(v.result().floorCleared()).isFalse();
        assertThat(v.result().expeditionEnded()).isFalse();
        assertThat(v.satiety()).isEqualTo(65);
        assertThat(v.spentToday()).isEqualTo(35);
        assertThat(v.currentMap()).isEqualTo(2);
        assertThat(pet(user).getHunger()).isEqualTo(35); // 끄적이의 hunger를 그대로 쓴다
    }

    @Test
    @DisplayName("맵 3장을 통과하면 그 층을 처음 돌파하고 다음 층 첫 맵으로 간다 (도중에 도시락을 먹어 이어 간다)")
    void threeMapsClearFloor() {
        User user = newUser(5, 100);
        lunchService.cook(user, "hearty", 1);
        lunchService.pack(user, "hearty", 1);
        towerService.depart(user, 1);

        towerService.clearMap(user);
        TowerService.View v = towerService.clearMap(user);
        assertThat(v.satiety()).isEqualTo(30);
        assertThat(v.onExpedition()).isTrue(); // 포만감은 모자라지만 가방에 도시락이 있어 원정이 이어진다
        assertThat(v.currentMap()).isEqualTo(3);

        assertThat(lunchService.eat(user, "hearty").satiety()).isEqualTo(75);
        v = towerService.clearMap(user);
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.result().firstClear()).isTrue();
        assertThat(v.highestFloor()).isEqualTo(1);
        assertThat(v.maxDepartFloor()).isEqualTo(2);
        assertThat(v.currentFloor()).isEqualTo(2);
        assertThat(v.currentMap()).isEqualTo(1);
        assertThat(v.satiety()).isEqualTo(40);
        assertThat(v.spentToday()).isEqualTo(105);
        assertThat(v.onExpedition()).isTrue();
    }

    @Test
    @DisplayName("포만감이 35보다 적은데 가방에 도시락이 있으면 거절만 하고 원정은 이어진다. 먹으면 통과할 수 있다")
    void notEnoughSatietyWithLunchboxIsRejectedButExpeditionContinues() {
        User user = newUser(2, 100);
        lunchService.cook(user, "riceball", 1);
        lunchService.pack(user, "riceball", 1);
        towerService.depart(user, null);
        towerService.clearMap(user);
        towerService.clearMap(user); // 포만감 30, 가방에 도시락이 있어 아직 원정 중

        assertCode(() -> towerService.clearMap(user), "NOT_ENOUGH_SATIETY");
        TowerService.View v = towerService.get(user);
        assertThat(v.onExpedition()).isTrue();
        assertThat(v.satiety()).isEqualTo(30);
        assertThat(v.currentMap()).isEqualTo(3);
        assertThat(v.spentToday()).isEqualTo(70);
        assertThat(v.bagCount()).isEqualTo(1);

        assertThat(lunchService.eat(user, "riceball").satiety()).isEqualTo(50);
        v = towerService.clearMap(user);
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.satiety()).isEqualTo(15);
    }

    @Test
    @DisplayName("포만감이 35보다 적고 가방도 비었으면 통과시키지 않고 원정이 끝난다. 패배는 없고 진행은 그대로다")
    void notEnoughSatietyWithEmptyBagEndsExpedition() {
        User user = newUser(0, 100);
        towerService.depart(user, null);
        towerService.clearMap(user); // 65
        // 원정 중에 시간이 흘러 포만감이 맵 값 아래로 내려갔다 (가방은 비어 있다)
        var pet = pet(user);
        pet.spendSatiety(40);
        tamagotchiRepository.save(pet);

        TowerService.View v = towerService.clearMap(user);
        assertThat(v.result().mapCleared()).isFalse();
        assertThat(v.result().spentSatiety()).isZero();
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(v.result().endReason()).isEqualTo("SATIETY_EMPTY");
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.satiety()).isEqualTo(25);
        assertThat(v.currentMap()).isEqualTo(2);
        assertThat(v.spentToday()).isEqualTo(35);
        assertThat(pet(user).isDead()).isFalse();
    }

    @Test
    @DisplayName("통과한 뒤 포만감이 모자라고 가방에 도시락도 없으면 원정이 저절로 끝난다")
    void expeditionEndsWhenNothingLeft() {
        User user = newUser(0, 100);
        towerService.depart(user, null);
        assertThat(towerService.clearMap(user).result().expeditionEnded()).isFalse(); // 65

        TowerService.View v = towerService.clearMap(user); // 30, 도시락 없음
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(v.result().endReason()).isEqualTo("SATIETY_EMPTY");
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.currentMap()).isEqualTo(3);
        assertCode(() -> towerService.clearMap(user), "NOT_ON_EXPEDITION");
    }

    @Test
    @DisplayName("탑은 포만감을 0으로 만들지 않는다: 딱 35일 때 통과하면 1이 남아 굶주림 타이머가 켜지지 않는다")
    void towerNeverEmptiesSatiety() {
        User user = newUser(0, 35);
        towerService.depart(user, null);
        TowerService.View v = towerService.clearMap(user);
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.result().spentSatiety()).isEqualTo(34);
        assertThat(v.satiety()).isEqualTo(1);
        assertThat(v.spentToday()).isEqualTo(34);
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(pet(user).getHunger()).isEqualTo(99);
        assertThat(pet(user).getHungryStartedAt()).isNull();
        assertThat(pet(user).isDead()).isFalse();
    }

    @Test
    @DisplayName("하루에 여러 층을 이어서 처음 돌파할 수 있고, 여러 번 다시 떠날 수 있다 (횟수 제한 없음)")
    void manyFloorsInOneDay() {
        User user = newUser(0, 100);
        towerService.depart(user, null);
        TowerService.View v = null;
        for (int floor = 1; floor <= 4; floor++) {
            for (int map = 1; map <= TowerRules.MAPS_PER_FLOOR; map++) {
                fillSatiety(user);
                v = towerService.clearMap(user);
                assertThat(v.result().mapCleared()).isTrue();
            }
            assertThat(v.result().firstClear()).as("floor " + floor).isTrue();
            assertThat(v.highestFloor()).isEqualTo(floor);
        }
        assertThat(v.spentToday()).isEqualTo(35 * 12);
        assertThat(v.currentFloor()).isEqualTo(5);

        // 같은 날 돌아왔다가 몇 번이고 다시 떠난다. 이미 돌파한 층은 처음 돌파가 아니다
        for (int i = 0; i < 3; i++) {
            assertThat(towerService.returnHome(user).result().endReason()).isEqualTo("RETURNED");
            fillSatiety(user);
            assertThat(towerService.depart(user, 2).onExpedition()).isTrue();
        }
        for (int map = 1; map <= TowerRules.MAPS_PER_FLOOR; map++) {
            fillSatiety(user);
            v = towerService.clearMap(user);
        }
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.result().firstClear()).isFalse();
        assertThat(v.highestFloor()).isEqualTo(4);
        assertThat(v.currentFloor()).isEqualTo(3);
    }

    @Test
    @DisplayName("오늘 쓴 포만감은 날짜(KST)가 바뀌면 0부터 다시 센다")
    void spentTodayResetsNextDay() {
        User user = newUser(0, 100);
        towerService.depart(user, null);
        assertThat(towerService.clearMap(user).spentToday()).isEqualTo(35);
        try {
            clock.advanceDays(1);
            assertThat(towerService.get(user).spentToday()).isZero();
            assertThat(towerService.clearMap(user).spentToday()).isEqualTo(35);
        } finally {
            clock.advanceDays(-1);
        }
    }

    @Test
    @DisplayName("원정 중이 아니면 맵 통과를 받지 않는다")
    void clearMapNeedsExpedition() {
        User user = newUser(0, 100);
        assertCode(() -> towerService.clearMap(user), "NOT_ON_EXPEDITION");
        assertThat(pet(user).satiety()).isEqualTo(100);
        assertThat(towerService.get(user).spentToday()).isZero();
    }

    @Test
    @DisplayName("출발할 수 있는 층은 1 ~ 돌파한 층 + 1이고, 원정 중에는 다시 출발할 수 없다")
    void departFloorRange() {
        User user = newUser(0, 100);
        assertThat(towerService.get(user).maxDepartFloor()).isEqualTo(1);
        assertCode(() -> towerService.depart(user, 2), "FLOOR_LOCKED");
        assertCode(() -> towerService.depart(user, 0), "FLOOR_LOCKED");

        towerService.depart(user, null);
        assertCode(() -> towerService.depart(user, 1), "ON_EXPEDITION");
        for (int map = 1; map <= TowerRules.MAPS_PER_FLOOR; map++) {
            fillSatiety(user);
            towerService.clearMap(user);
        }
        towerService.returnHome(user);

        assertCode(() -> towerService.depart(user, 3), "FLOOR_LOCKED");
        assertThat(towerService.depart(user, 1).currentFloor()).isEqualTo(1);
        towerService.returnHome(user);
        TowerService.View v = towerService.depart(user, null); // 비우면 돌파한 층 바로 위
        assertThat(v.currentFloor()).isEqualTo(2);
        assertThat(v.currentMap()).isEqualTo(1);
    }

    @Test
    @DisplayName("포만감이 35보다 적으면 떠날 수 없지만, 가방에 도시락이 있으면 떠날 수 있다")
    void departNeedsSatietyOrLunchbox() {
        User user = newUser(2, 20);
        assertCode(() -> towerService.depart(user, null), "NOT_ENOUGH_SATIETY");
        assertThat(towerService.get(user).onExpedition()).isFalse();

        lunchService.cook(user, "riceball", 1);
        lunchService.pack(user, "riceball", 1);
        assertThat(towerService.depart(user, null).onExpedition()).isTrue();
        assertThat(lunchService.eat(user, "riceball").satiety()).isEqualTo(40);
        assertThat(towerService.clearMap(user).result().mapCleared()).isTrue();
    }

    @Test
    @DisplayName("돌아오기는 언제든 되고, 원정 중이 아니면 아무 일도 없다")
    void returnHome() {
        User user = newUser(0, 100);
        TowerService.View v = towerService.returnHome(user);
        assertThat(v.result().expeditionEnded()).isFalse();

        towerService.depart(user, null);
        v = towerService.returnHome(user);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(v.result().endReason()).isEqualTo("RETURNED");
        assertThat(v.satiety()).isEqualTo(100);
    }
}
