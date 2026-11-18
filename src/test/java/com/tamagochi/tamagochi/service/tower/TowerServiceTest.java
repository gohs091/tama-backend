package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.tower.TowerFloorCatalog;
import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import com.tamagochi.tamagochi.domain.tower.TowerRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tower.TowerProgressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TowerServiceTest extends TowerLunchTestSupport {

    @Autowired TowerProgressRepository progressRepository;

    /** 저장된 진행을 직접 고친다 (예전 규칙으로 중간에 있던 사람, 높은 층까지 오른 사람 등을 만든다) */
    private void patchProgress(User user, String field, Object value) {
        TowerProgress progress = progressRepository.findByUserId(user.getId()).orElseThrow();
        ReflectionTestUtils.setField(progress, field, value);
        progressRepository.save(progress);
    }

    /** 포만감을 채워 가며 맵을 n장 통과한다. 마지막 결과를 돌려준다 */
    private TowerService.View clearMaps(User user, int n) {
        TowerService.View v = null;
        for (int i = 0; i < n; i++) {
            fillSatiety(user);
            v = towerService.clearMap(user);
            assertThat(v.result().mapCleared()).isTrue();
        }
        return v;
    }

    @Test
    @DisplayName("규칙 값: 맵 1장 = 포만감 35, 원정 제한 60분, 공개 층 상한 17, 1층 = 맵 5장 (층의 맵 수는 데이터)")
    void rules() {
        assertThat(TowerRules.MAP_SATIETY_COST).isEqualTo(35);
        assertThat(TowerRules.EXPEDITION_TIMEOUT_MINUTES).isEqualTo(60);
        assertThat(TowerFloorCatalog.openFloorCap()).isEqualTo(17);
        assertThat(TowerFloorCatalog.mapsPerFloor(1)).isEqualTo(5);

        User user = newUser(0, 100);
        TowerService.View v = towerService.get(user);
        assertThat(v.mapsPerFloor()).isEqualTo(5); // 예전 앱이 점을 그리는 필드: 지금 층의 맵 수
        assertThat(v.openFloorCap()).isEqualTo(17);
    }

    @Test
    @DisplayName("1층은 맵 5장: 4번 통과해도 1층이고, 5번째에 층을 처음 돌파해 2층 첫 맵으로 간다")
    void fiveMapsClearFirstFloor() {
        User user = newUser(0, 100);
        towerService.depart(user, 1);

        TowerService.View v = null;
        for (int cleared = 1; cleared <= 4; cleared++) {
            fillSatiety(user);
            v = towerService.clearMap(user);
            assertThat(v.result().floorCleared()).as("map " + cleared).isFalse();
            assertThat(v.currentFloor()).isEqualTo(1);
            assertThat(v.currentMap()).isEqualTo(cleared + 1);
            assertThat(v.highestFloor()).isZero();
        }
        assertThat(v.currentMap()).isEqualTo(5);

        fillSatiety(user);
        v = towerService.clearMap(user);
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.result().firstClear()).isTrue();
        assertThat(v.result().expeditionEnded()).isFalse();
        assertThat(v.highestFloor()).isEqualTo(1);
        assertThat(v.maxDepartFloor()).isEqualTo(2);
        assertThat(v.currentFloor()).isEqualTo(2);
        assertThat(v.currentMap()).isEqualTo(1);
        assertThat(v.mapsPerFloor()).isEqualTo(TowerFloorCatalog.mapsPerFloor(2));
        assertThat(v.spentToday()).isEqualTo(35 * 5);
        assertThat(v.onExpedition()).isTrue();
    }

    @Test
    @DisplayName("중간 진행: 예전 규칙(3장)으로 1층 맵 2에 있던 사람은 통과하면 맵 3으로 간다 (층이 끝나지 않는다). 맵 3·4·5를 더 가면 돌파한다")
    void midFloorProgressSurvivesMapCountChange() {
        User user = newUser(0, 100);
        towerService.depart(user, 1);
        patchProgress(user, "currentMap", 2);

        TowerService.View v = clearMaps(user, 1);
        assertThat(v.result().floorCleared()).isFalse();
        assertThat(v.currentFloor()).isEqualTo(1);
        assertThat(v.currentMap()).isEqualTo(3);
        assertThat(v.highestFloor()).isZero();

        v = clearMaps(user, 2);
        assertThat(v.result().floorCleared()).isFalse();
        assertThat(v.currentMap()).isEqualTo(5);

        v = clearMaps(user, 1);
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.currentFloor()).isEqualTo(2);
        assertThat(v.currentMap()).isEqualTo(1);
    }

    @Test
    @DisplayName("currentMap이 그 층 맵 수보다 큰 이상한 값이어도 터지지 않고, 다음 통과에서 그 층을 돌파한 것으로 본다")
    void currentMapBeyondFloorMapsClearsFloor() {
        User user = newUser(0, 100);
        towerService.depart(user, 1);
        patchProgress(user, "currentMap", 9);

        TowerService.View shown = towerService.get(user);
        assertThat(shown.currentMap()).isEqualTo(9);
        assertThat(shown.mapsPerFloor()).isEqualTo(5);

        TowerService.View v = clearMaps(user, 1);
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.result().firstClear()).isTrue();
        assertThat(v.highestFloor()).isEqualTo(1);
        assertThat(v.currentFloor()).isEqualTo(2);
        assertThat(v.currentMap()).isEqualTo(1);
    }

    @Test
    @DisplayName("공개 층 상한 17: 18층으로는 떠날 수 없고, 17층을 돌파한 사람은 17층까지만(다시) 갈 수 있다")
    void openFloorCap() {
        User user = newUser(0, 100);
        patchProgressAfterCreate(user, 16);
        assertThat(towerService.get(user).maxDepartFloor()).isEqualTo(17);
        assertCode(() -> towerService.depart(user, 18), "FLOOR_LOCKED");

        User top = newUser(0, 100);
        patchProgressAfterCreate(top, 17);
        TowerService.View v = towerService.get(top);
        assertThat(v.highestFloor()).isEqualTo(17);
        assertThat(v.maxDepartFloor()).isEqualTo(17);
        assertCode(() -> towerService.depart(top, 18), "FLOOR_LOCKED");
        assertCode(() -> towerService.depart(top, 99), "FLOOR_LOCKED");

        v = towerService.depart(top, 17); // 17층으로 다시 떠날 수는 있다
        assertThat(v.onExpedition()).isTrue();
        assertThat(v.currentFloor()).isEqualTo(17);
        towerService.returnHome(top);
        assertThat(towerService.depart(top, null).currentFloor()).isEqualTo(17); // 비우면 갈 수 있는 가장 높은 층
    }

    /** 진행을 만들고(없으면) 돌파한 가장 높은 층을 정한다 */
    private void patchProgressAfterCreate(User user, int highestFloor) {
        towerService.get(user);
        patchProgress(user, "highestFloor", highestFloor);
    }

    @Test
    @DisplayName("17층 마지막 맵을 통과하면 17층을 돌파하고, 18층으로 올라가지 않고 원정이 끝난다. 다시 깨면 처음 돌파가 아니다")
    void clearingTopFloorEndsExpeditionWithoutGoingTo18() {
        User user = newUser(0, 100);
        patchProgressAfterCreate(user, 16);
        towerService.depart(user, 17);
        int maps = TowerFloorCatalog.mapsPerFloor(17);

        TowerService.View v = clearMaps(user, maps - 1);
        assertThat(v.result().floorCleared()).isFalse();
        assertThat(v.onExpedition()).isTrue();
        assertThat(v.currentFloor()).isEqualTo(17);
        assertThat(v.currentMap()).isEqualTo(maps);

        v = clearMaps(user, 1);
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.result().firstClear()).isTrue();
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(v.result().endReason()).isEqualTo(TowerService.TOP_FLOOR_CLEARED);
        assertThat(v.highestFloor()).isEqualTo(17);
        assertThat(v.currentFloor()).isEqualTo(17); // 18이 되지 않는다
        assertThat(v.maxDepartFloor()).isEqualTo(17);
        assertThat(v.onExpedition()).isFalse();
        assertThat(progressRepository.findByUserId(user.getId()).orElseThrow().getDepartedAt()).isNull();
        assertCode(() -> towerService.clearMap(user), "NOT_ON_EXPEDITION");
        assertCode(() -> towerService.depart(user, 18), "FLOOR_LOCKED");

        // 17층을 다시 깬다: 처음 돌파가 아니고, 여전히 18층으로 가지 않는다
        fillSatiety(user);
        towerService.depart(user, 17);
        v = clearMaps(user, maps);
        assertThat(v.result().floorCleared()).isTrue();
        assertThat(v.result().firstClear()).isFalse();
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(v.highestFloor()).isEqualTo(17);
        assertThat(v.currentFloor()).isEqualTo(17);
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
    @DisplayName("도중에 도시락을 먹어 원정을 이어 간다: 포만감이 모자라도 가방에 도시락이 있으면 원정 중이고, 먹으면 다음 맵을 통과한다")
    void lunchboxKeepsExpeditionGoing() {
        User user = newUser(6, 100);
        cookOneByOne(user, 2);
        lunchService.pack(user, "lunchbox", 2);
        towerService.depart(user, 1);

        towerService.clearMap(user);
        TowerService.View v = towerService.clearMap(user);
        assertThat(v.satiety()).isEqualTo(30);
        assertThat(v.onExpedition()).isTrue(); // 포만감은 모자라지만 가방에 도시락이 있어 원정이 이어진다
        assertThat(v.currentMap()).isEqualTo(3);
        assertThat(v.bagCount()).isEqualTo(2);

        assertThat(lunchService.eat(user, "lunchbox").satiety()).isEqualTo(65); // 도시락 하나 = 맵 1장 값(+35)
        v = towerService.clearMap(user);
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.result().floorCleared()).isFalse(); // 1층은 맵 5장이라 3장으로는 끝나지 않는다
        assertThat(v.highestFloor()).isZero();
        assertThat(v.currentFloor()).isEqualTo(1);
        assertThat(v.currentMap()).isEqualTo(4);
        assertThat(v.satiety()).isEqualTo(30);
        assertThat(v.spentToday()).isEqualTo(105);
        assertThat(v.bagCount()).isEqualTo(1);
        assertThat(v.onExpedition()).isTrue(); // 가방에 하나 더 있어 원정이 이어진다

        assertThat(lunchService.eat(user, null).satiety()).isEqualTo(65); // id 없이 먹어도 된다
        v = towerService.clearMap(user); // 30, 가방도 비었다
        assertThat(v.currentMap()).isEqualTo(5);
        assertThat(v.spentToday()).isEqualTo(140);
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(v.result().endReason()).isEqualTo("SATIETY_EMPTY");
    }

    @Test
    @DisplayName("포만감이 35보다 적은데 가방에 도시락이 있으면 거절만 하고 원정은 이어진다. 먹으면 통과할 수 있다")
    void notEnoughSatietyWithLunchboxIsRejectedButExpeditionContinues() {
        User user = newUser(3, 100);
        lunchService.cook(user, "lunchbox", 1);
        lunchService.pack(user, "lunchbox", 1);
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

        assertThat(lunchService.eat(user, "lunchbox").satiety()).isEqualTo(65);
        v = towerService.clearMap(user);
        assertThat(v.result().mapCleared()).isTrue();
        assertThat(v.result().floorCleared()).isFalse();
        assertThat(v.currentMap()).isEqualTo(4);
        assertThat(v.satiety()).isEqualTo(30);
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
    @DisplayName("완성작 전(낙서) 끄적이가 탑에서 포만감을 다 써도 굶주림 타이머가 시작되지 않고 떠나지 않는다 (하한 포만감 1)")
    void preAdultPetSpendingAllSatietyInTowerDoesNotStartStarvation() {
        User user = newUser(0, 70);
        assertThat(pet(user).getEvolutionStage()).isEqualTo(com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage.BABY);
        towerService.depart(user, null);
        towerService.clearMap(user);                       // 70 → 35
        TowerService.View v = towerService.clearMap(user); // 35 → 1 (하한), 가방이 비어 원정 종료
        assertThat(v.satiety()).isEqualTo(TowerRules.SATIETY_FLOOR);
        assertThat(v.result().expeditionEnded()).isTrue();

        // 스케줄러 틱이 돌아도(운영 규칙: 포만감 0으로 6시간이면 떠남) 탑 소모만으로는 타이머가 켜지지 않는다
        var rule = new com.tamagochi.tamagochi.domain.tamagotchi.VitalsRule(8, 16, java.time.Duration.ofHours(6), 60, 13,
                java.time.Duration.ofMinutes(5));
        var pet = pet(user);
        java.time.LocalDateTime t = java.time.LocalDateTime.of(2026, 11, 18, 10, 0);
        pet.applyTime(t, rule);
        pet.applyTime(t.plusMinutes(4), rule);
        pet.applyTime(t.plusMinutes(7), rule); // 포만감 1이 줄기(8분) 전
        assertThat(pet.getHunger()).isEqualTo(99);
        assertThat(pet.getHungryStartedAt()).isNull();
        assertThat(pet.isDead()).isFalse();
    }

    @Test
    @DisplayName("하루에 여러 층을 이어서 처음 돌파할 수 있고, 여러 번 다시 떠날 수 있다 (횟수 제한 없음)")
    void manyFloorsInOneDay() {
        User user = newUser(0, 100);
        towerService.depart(user, null);
        TowerService.View v = null;
        int maps = 0;
        for (int floor = 1; floor <= 4; floor++) {
            v = clearMaps(user, TowerFloorCatalog.mapsPerFloor(floor));
            maps += TowerFloorCatalog.mapsPerFloor(floor);
            assertThat(v.result().firstClear()).as("floor " + floor).isTrue();
            assertThat(v.highestFloor()).isEqualTo(floor);
        }
        assertThat(v.spentToday()).isEqualTo(35 * maps);
        assertThat(v.currentFloor()).isEqualTo(5);

        // 같은 날 돌아왔다가 몇 번이고 다시 떠난다. 이미 돌파한 층은 처음 돌파가 아니다
        for (int i = 0; i < 3; i++) {
            assertThat(towerService.returnHome(user).result().endReason()).isEqualTo("RETURNED");
            fillSatiety(user);
            assertThat(towerService.depart(user, 2).onExpedition()).isTrue();
        }
        v = clearMaps(user, TowerFloorCatalog.mapsPerFloor(2));
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
        towerService.returnHome(user); // 원정은 60분이면 끝나므로, 다음 날에는 새로 떠난다
        try {
            clock.advanceDays(1);
            assertThat(towerService.get(user).spentToday()).isZero();
            towerService.depart(user, null);
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
        clearMaps(user, TowerFloorCatalog.mapsPerFloor(1));
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
        User user = newUser(3, 20);
        assertCode(() -> towerService.depart(user, null), "NOT_ENOUGH_SATIETY");
        assertThat(towerService.get(user).onExpedition()).isFalse();

        lunchService.cook(user, "lunchbox", 1);
        lunchService.pack(user, "lunchbox", 1);
        assertThat(towerService.depart(user, null).onExpedition()).isTrue();
        assertThat(lunchService.eat(user, "lunchbox").satiety()).isEqualTo(55);
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
