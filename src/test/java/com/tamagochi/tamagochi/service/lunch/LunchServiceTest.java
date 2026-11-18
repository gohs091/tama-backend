package com.tamagochi.tamagochi.service.lunch;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.lunch.Pantry;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.lunch.PantryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LunchServiceTest extends TowerLunchTestSupport {

    @Autowired PantryRepository pantryRepository;
    @Autowired PlatformTransactionManager transactionManager;

    /** 예전 세 종류였을 때 저장된 것처럼 찬장·가방에 예전 키를 넣는다 */
    private void storeLegacy(User user, Map<String, Integer> cupboard, Map<String, Integer> bag) {
        lunchService.get(user); // 찬장 행을 만든다
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Pantry pantry = pantryRepository.findByUserId(user.getId()).orElseThrow();
            pantry.getCupboard().putAll(cupboard);
            pantry.getBag().putAll(bag);
        });
    }

    private Pantry stored(User user) {
        return pantryRepository.findByUserId(user.getId()).orElseThrow();
    }

    @Test
    @DisplayName("도시락은 한 종류: 하나 만들면 열매 3이 빠지고, 담아서 탑에서 먹으면 포만감이 35 찬다")
    void cookPackEatOne() {
        User user = newUser(10, 50);
        LunchService.View v = lunchService.get(user);
        assertThat(v.catalog()).hasSize(1);
        assertThat(v.catalog().get(0).id()).isEqualTo("lunchbox");
        assertThat(v.catalog().get(0).name()).isEqualTo("도시락");
        assertThat(v.catalog().get(0).fruitCost()).isEqualTo(3);
        assertThat(v.catalog().get(0).satiety()).isEqualTo(35);
        assertThat(v.cupboard()).isEmpty();
        assertThat(v.cupboardCount()).isZero();
        assertThat(v.bagCount()).isZero();

        v = lunchService.cook(user, "lunchbox", 1);
        assertThat(v.fruit()).isEqualTo(7);
        assertThat(fruit(user)).isEqualTo(7);
        assertThat(v.cupboard()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(v.cupboardCount()).isEqualTo(1);

        v = lunchService.pack(user, "lunchbox", 1);
        assertThat(v.cupboard()).isEmpty();
        assertThat(v.cupboardCount()).isZero();
        assertThat(v.bag()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(v.bagCount()).isEqualTo(1);
        assertThat(v.bagUsed()).isEqualTo(1);

        towerService.depart(user, null);
        v = lunchService.eat(user, "lunchbox");
        assertThat(v.recovered()).isEqualTo(35);
        assertThat(v.satiety()).isEqualTo(85);
        assertThat(v.bag()).isEmpty();
        assertThat(v.bagCount()).isZero();
        assertThat(fruit(user)).isEqualTo(7);
    }

    @Test
    @DisplayName("만들면 열매가 값 × 개수만큼 빠지고 찬장에 개수가 쌓인다. 하루 상한은 없다")
    void cookSpendsFruitAndFillsCupboard() {
        User user = newUser(30, 50);
        rescueHamster(); // 한 번에 여러 개 만들기는 햄스터를 구한 뒤에 된다
        LunchService.View v = lunchService.cook(user, "lunchbox", 2);
        assertThat(v.fruit()).isEqualTo(24);
        assertThat(v.cupboard()).containsEntry("lunchbox", 2);
        assertThat(v.bag()).isEmpty();

        for (int i = 0; i < 5; i++) v = lunchService.cook(user, "lunchbox", 1); // 여러 번 만들어도 막지 않는다
        assertThat(v.fruit()).isEqualTo(9);
        assertThat(v.cupboard()).containsOnly(Map.entry("lunchbox", 7));
        assertThat(v.cupboardCount()).isEqualTo(7);
        assertThat(fruit(user)).isEqualTo(9);
    }

    @Test
    @DisplayName("열매가 모자라면 만들지 않고 아무것도 바뀌지 않는다")
    void cookRejectsWhenFruitShort() {
        User user = newUser(5, 50);
        rescueHamster(); // 여러 개를 한 번에 만들 때의 열매 검사 (구하기 전에는 하나만 만든다)
        assertCode(() -> lunchService.cook(user, "lunchbox", 2), "NOT_ENOUGH_FRUIT");
        assertThat(fruit(user)).isEqualTo(5);
        assertThat(lunchService.get(user).cupboard()).isEmpty();
    }

    @Test
    @DisplayName("잠긴 도시락은 없다: 목록의 도시락은 잠겨 있지 않고 해금 조건도 없다")
    void nothingIsLocked() {
        User user = newUser(50, 50);
        LunchService.View v = lunchService.get(user);
        assertThat(v.catalog()).extracting(LunchService.LunchView::id).containsExactly("lunchbox");
        assertThat(v.catalog()).extracting(LunchService.LunchView::locked).containsExactly(false);
        assertThat(v.catalog().get(0).unlock()).isNull();

        // 예전에 잠겨 있던 id로 보내도 거절하지 않고 지금 도시락을 만든다
        v = lunchService.cook(user, "feast", 1);
        assertThat(v.cupboard()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(fruit(user)).isEqualTo(47);
    }

    @Test
    @DisplayName("예전 id(riceball·hearty·feast)로 만들고·담고·꺼내고·먹어도 모두 되고, 지금 도시락 하나로 처리한다")
    void oldIdsWorkAsTheSingleLunchbox() {
        User user = newUser(20, 30);
        assertThat(lunchService.cook(user, "riceball", 1).cupboard()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(lunchService.cook(user, "hearty", 1).cupboard()).containsOnly(Map.entry("lunchbox", 2));
        assertThat(lunchService.cook(user, "feast", 1).cupboard()).containsOnly(Map.entry("lunchbox", 3));
        assertThat(fruit(user)).isEqualTo(11); // 어느 id든 열매 3

        LunchService.View v = lunchService.pack(user, "hearty", 2);
        assertThat(v.bag()).containsOnly(Map.entry("lunchbox", 2));
        v = lunchService.unpack(user, "feast", 1);
        assertThat(v.bag()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(v.cupboard()).containsOnly(Map.entry("lunchbox", 2));

        towerService.depart(user, null);
        v = lunchService.eat(user, "riceball");
        assertThat(v.recovered()).isEqualTo(35); // 예전 주먹밥 +20이 아니라 지금 도시락 +35
        assertThat(v.satiety()).isEqualTo(65);
        assertThat(v.bag()).isEmpty();

        assertThat(lunchService.devGive(user, "feast", 2).cupboard()).containsOnly(Map.entry("lunchbox", 4));
    }

    @Test
    @DisplayName("id를 생략해도(null·빈 문자열) 만들고·담고·먹을 수 있다")
    void idCanBeOmitted() {
        User user = newUser(10, 30);
        rescueHamster();
        assertThat(lunchService.cook(user, null, 2).cupboard()).containsOnly(Map.entry("lunchbox", 2));
        assertThat(lunchService.cook(user, "", null).cupboard()).containsOnly(Map.entry("lunchbox", 3));
        assertThat(fruit(user)).isEqualTo(1);

        assertThat(lunchService.pack(user, null, 2).bag()).containsOnly(Map.entry("lunchbox", 2));
        assertThat(lunchService.unpack(user, " ", 1).bag()).containsOnly(Map.entry("lunchbox", 1));

        towerService.depart(user, null);
        LunchService.View v = lunchService.eat(user, null);
        assertThat(v.recovered()).isEqualTo(35);
        assertThat(v.satiety()).isEqualTo(65);
        assertThat(lunchService.devGive(user, null, 1).cupboardCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("전혀 모르는 id, 1보다 작거나 너무 큰 개수는 거절한다")
    void rejectsBadIdAndCount() {
        User user = newUser(500, 50);
        assertCode(() -> lunchService.cook(user, "pizza", 1), "UNKNOWN_LUNCHBOX");
        assertCode(() -> lunchService.cook(user, "lunchbox", 0), "INVALID_COUNT");
        assertCode(() -> lunchService.cook(user, "lunchbox", -3), "INVALID_COUNT");
        assertCode(() -> lunchService.cook(user, "lunchbox", 100), "INVALID_COUNT");
        assertThat(fruit(user)).isEqualTo(500);
    }

    @Test
    @DisplayName("가방 용량은 기본 2개이고 넘치게 담을 수 없다. 꺼내면 찬장으로 돌아간다")
    void packRespectsBagSlots() {
        User user = newUser(20, 50);
        cookOneByOne(user, 3);
        LunchService.View v = lunchService.pack(user, "lunchbox", 2);
        assertThat(v.bagSlots()).isEqualTo(2);
        assertThat(v.bagUsed()).isEqualTo(2);
        assertThat(v.bagCount()).isEqualTo(2);
        assertThat(v.cupboard()).containsEntry("lunchbox", 1);

        assertCode(() -> lunchService.pack(user, "lunchbox", 1), "BAG_FULL");

        v = lunchService.unpack(user, "lunchbox", 1);
        assertThat(v.bag()).containsEntry("lunchbox", 1);
        assertThat(v.cupboard()).containsEntry("lunchbox", 2);
        assertCode(() -> lunchService.unpack(user, "lunchbox", 2), "NOT_IN_BAG");
        assertCode(() -> lunchService.pack(user, "lunchbox", 3), "NOT_IN_CUPBOARD");
    }

    @Test
    @DisplayName("탑에 가 있는 동안에는 가방을 챙기거나 풀 수 없다. 돌아오면 가방에 남은 도시락은 그대로 있다")
    void cannotPackWhileOnExpedition() {
        User user = newUser(20, 100);
        cookOneByOne(user, 2);
        lunchService.pack(user, "lunchbox", 1);
        towerService.depart(user, null);
        assertThat(lunchService.get(user).onExpedition()).isTrue();

        assertCode(() -> lunchService.pack(user, "lunchbox", 1), "ON_EXPEDITION");
        assertCode(() -> lunchService.unpack(user, "lunchbox", 1), "ON_EXPEDITION");
        assertThat(lunchService.cook(user, "lunchbox", 1).cupboard()).containsEntry("lunchbox", 2); // 만들기는 막지 않는다

        towerService.returnHome(user);
        LunchService.View v = lunchService.get(user);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.bag()).containsEntry("lunchbox", 1);
        assertThat(lunchService.unpack(user, "lunchbox", 1).bag()).isEmpty();
    }

    @Test
    @DisplayName("도시락은 탑 원정 중에만 먹을 수 있다")
    void eatOnlyOnExpedition() {
        User user = newUser(20, 50);
        lunchService.cook(user, "lunchbox", 1);
        lunchService.pack(user, "lunchbox", 1);

        assertCode(() -> lunchService.eat(user, "lunchbox"), "NOT_ON_EXPEDITION");
        assertThat(lunchService.get(user).bag()).containsEntry("lunchbox", 1);
        assertThat(pet(user).satiety()).isEqualTo(50);

        towerService.depart(user, null);
        LunchService.View v = lunchService.eat(user, "lunchbox");
        assertThat(v.recovered()).isEqualTo(35);
        assertThat(v.satiety()).isEqualTo(85);
        assertThat(v.bag()).isEmpty();
        assertCode(() -> lunchService.eat(user, "lunchbox"), "NOT_IN_BAG");
    }

    @Test
    @DisplayName("먹어도 포만감은 100을 넘지 않고, 이미 가득이면 도시락을 쓰지 않는다")
    void eatCapsSatietyAtHundred() {
        User user = newUser(20, 90);
        cookOneByOne(user, 2);
        lunchService.pack(user, "lunchbox", 2);
        towerService.depart(user, null);

        LunchService.View v = lunchService.eat(user, "lunchbox");
        assertThat(v.recovered()).isEqualTo(10);
        assertThat(v.satiety()).isEqualTo(100);
        assertThat(pet(user).getHunger()).isZero();

        assertCode(() -> lunchService.eat(user, "lunchbox"), "FULL");
        assertThat(lunchService.get(user).bag()).containsEntry("lunchbox", 1);
    }

    @Test
    @DisplayName("가방에 하나뿐인 도시락을 동시에 두 번 먹으면 한 번만 먹힌다")
    void concurrentEatConsumesOnce() throws Exception {
        User user = newUser(20, 40);
        lunchService.cook(user, "lunchbox", 1);
        lunchService.pack(user, "lunchbox", 1);
        towerService.depart(user, null);

        List<String> results = together(2, () -> lunchService.eat(user, "lunchbox"));

        assertThat(results).containsExactlyInAnyOrder("OK", "NOT_IN_BAG");
        assertThat(pet(user).satiety()).isEqualTo(75);
        assertThat(lunchService.get(user).bag()).isEmpty();
    }

    @Test
    @DisplayName("동시에 만들어도 열매는 만든 만큼만 빠진다 (이중 차감·공짜 도시락 없음)")
    void concurrentCookSpendsFruitExactly() throws Exception {
        // 열매가 하나 몫뿐: 둘이 동시에 만들면 하나만 된다
        User poor = newUser(3, 50);
        List<String> results = together(2, () -> lunchService.cook(poor, "lunchbox", 1));
        assertThat(results).containsExactlyInAnyOrder("OK", "NOT_ENOUGH_FRUIT");
        assertThat(fruit(poor)).isZero();
        assertThat(lunchService.get(poor).cupboard()).containsEntry("lunchbox", 1);

        // 열매가 넉넉: 넷이 동시에 만들면 넷 다 되고 정확히 네 개 값만 빠진다
        User rich = newUser(20, 50);
        results = together(4, () -> lunchService.cook(rich, "lunchbox", 1));
        assertThat(results).containsOnly("OK");
        assertThat(fruit(rich)).isEqualTo(8);
        assertThat(lunchService.get(rich).cupboard()).containsEntry("lunchbox", 4);
    }

    // ── 예전 세 종류 → 지금 도시락 (저장된 것 바꾸기) ──

    @Test
    @DisplayName("예전 종류로 저장된 찬장·가방은 처음 읽을 때 지금 도시락 개수로 바뀌고, DB에 예전 키가 남지 않는다")
    void legacyPantryIsMigratedOnFirstRead() {
        User user = newUser(0, 50);
        storeLegacy(user, Map.of("riceball", 2, "hearty", 1, "feast", 1), Map.of("riceball", 1));
        assertThat(stored(user).hasLegacy()).isTrue();

        LunchService.View v = lunchService.get(user);
        assertThat(v.cupboard()).containsOnly(Map.entry("lunchbox", 7)); // 2×1 + 1×2 + 1×3
        assertThat(v.cupboardCount()).isEqualTo(7);
        assertThat(v.bag()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(v.bagCount()).isEqualTo(1);

        Pantry stored = stored(user);
        assertThat(stored.hasLegacy()).isFalse();
        assertThat(stored.getCupboard()).containsOnly(Map.entry("lunchbox", 7));
        assertThat(stored.getBag()).containsOnly(Map.entry("lunchbox", 1));

        // 다시 읽어도 더 늘지 않는다
        lunchService.get(user);
        assertThat(lunchService.get(user).cupboardCount()).isEqualTo(7);
        assertThat(stored(user).getCupboard()).containsOnly(Map.entry("lunchbox", 7));
    }

    @Test
    @DisplayName("예전 든든 도시락이 가방에 있던 사람: 바뀐 뒤 2개가 되어 탑에서 두 번 먹을 수 있다")
    void legacyBagStaysEdibleAfterMigration() {
        User user = newUser(0, 20);
        storeLegacy(user, Map.of(), Map.of("hearty", 1));

        assertThat(towerService.get(user).bagCount()).as("탑 화면도 지금 개수로 센다").isEqualTo(2);
        towerService.depart(user, null);
        assertThat(lunchService.eat(user, "hearty").satiety()).isEqualTo(55);
        LunchService.View v = lunchService.eat(user, null);
        assertThat(v.satiety()).isEqualTo(90);
        assertThat(v.bag()).isEmpty();
        assertThat(stored(user).hasLegacy()).isFalse();
    }

    @Test
    @DisplayName("예전 가방이 지금 용량을 넘게 되면 넘는 만큼은 찬장으로 간다 (가방 잔치 2 → 가방 2 + 찬장 4)")
    void legacyBagOverflowMovesToCupboard() {
        User user = newUser(0, 50);
        storeLegacy(user, Map.of(), Map.of("feast", 2));

        LunchService.View v = lunchService.get(user);
        assertThat(v.bagSlots()).isEqualTo(2);
        assertThat(v.bag()).containsOnly(Map.entry("lunchbox", 2));
        assertThat(v.bagUsed()).isEqualTo(2);
        assertThat(v.cupboard()).containsOnly(Map.entry("lunchbox", 4));
    }

    @Test
    @DisplayName("일괄 변환(서버가 뜰 때): 앱을 열지 않은 사람의 예전 도시락도 바뀌고, 이미 바뀐 사람·빈 찬장은 건드리지 않는다")
    void bulkMigrationConvertsOnlyLegacyPantries() {
        User legacy = newUser(0, 50);
        storeLegacy(legacy, Map.of("feast", 1), Map.of("hearty", 1));
        User fresh = newUser(9, 50);
        cookOneByOne(fresh, 3);
        lunchService.pack(fresh, "lunchbox", 1);
        User empty = newUser(0, 50);
        lunchService.get(empty);
        Long freshVersion = stored(fresh).getVersion();
        Long emptyVersion = stored(empty).getVersion();

        assertThat(lunchService.migrateLegacyPantries()).isGreaterThanOrEqualTo(1);

        assertThat(stored(legacy).getCupboard()).containsOnly(Map.entry("lunchbox", 3));
        assertThat(stored(legacy).getBag()).containsOnly(Map.entry("lunchbox", 2));
        assertThat(stored(fresh).getCupboard()).containsOnly(Map.entry("lunchbox", 2));
        assertThat(stored(fresh).getBag()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(stored(fresh).getVersion()).isEqualTo(freshVersion);
        assertThat(stored(empty).getCupboard()).isEmpty();
        assertThat(stored(empty).getVersion()).isEqualTo(emptyVersion);

        // 한 번 더 돌려도 다시 바꾸지 않는다
        lunchService.migrateLegacyPantries();
        assertThat(stored(legacy).getCupboard()).containsOnly(Map.entry("lunchbox", 3));
        assertThat(stored(legacy).getBag()).containsOnly(Map.entry("lunchbox", 2));
    }

    // ── 햄스터 보상: 한 번에 여러 개 만들기 + 가방 +1 ──

    @Test
    @DisplayName("햄스터를 구하기 전: 여러 개를 만들라고 해도 거절하지 않고 하나만 만들고, 열매도 하나 몫(3)만 빠진다")
    void cookMakesOnlyOneBeforeHamsterRescue() {
        User user = newUser(30, 50);
        assertThat(lunchService.get(user).batchCooking()).isFalse();

        LunchService.View v = lunchService.cook(user, "lunchbox", 5);
        assertThat(v.batchCooking()).isFalse();
        assertThat(v.cupboard()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(v.cupboardCount()).isEqualTo(1);
        assertThat(v.fruit()).isEqualTo(27);
        assertThat(fruit(user)).isEqualTo(27);
        assertThat(v.bagSlots()).isEqualTo(2);
        assertThat(v.catalog().get(0).locked()).as("도시락 자체는 잠그지 않는다").isFalse();

        // 열매가 하나 몫뿐이어도, 여러 개를 보냈다고 NOT_ENOUGH_FRUIT가 되지 않는다
        User poor = newUser(3, 50);
        assertThat(lunchService.cook(poor, "lunchbox", 5).cupboardCount()).isEqualTo(1);
        assertThat(fruit(poor)).isZero();

        // 개수가 없으면 지금처럼 하나, 0·음수·상한 초과는 구하기 전에도 INVALID_COUNT
        assertThat(lunchService.cook(user, "lunchbox", null).cupboardCount()).isEqualTo(2);
        assertCode(() -> lunchService.cook(user, "lunchbox", 0), "INVALID_COUNT");
        assertCode(() -> lunchService.cook(user, "lunchbox", -1), "INVALID_COUNT");
        assertCode(() -> lunchService.cook(user, "lunchbox", 100), "INVALID_COUNT");
        assertThat(fruit(user)).isEqualTo(24);
    }

    @Test
    @DisplayName("햄스터를 구한 뒤: 한 번에 여러 개를 만들고 열매가 값 × 개수만큼 빠진다. 가방은 한 칸 늘어 3칸이다")
    void cookMakesManyAfterHamsterRescue() {
        User user = newUser(30, 50);
        rescueHamster();

        LunchService.View v = lunchService.cook(user, "lunchbox", 5);
        assertThat(v.batchCooking()).isTrue();
        assertThat(v.cupboard()).containsOnly(Map.entry("lunchbox", 5));
        assertThat(v.cupboardCount()).isEqualTo(5);
        assertThat(v.fruit()).isEqualTo(15);
        assertThat(fruit(user)).isEqualTo(15);
        assertThat(v.bagSlots()).isEqualTo(3);
        assertThat(v.catalog().get(0).locked()).isFalse();

        // 늘어난 칸까지 담을 수 있고, 그 이상은 넘치지 않는다
        v = lunchService.pack(user, "lunchbox", 3);
        assertThat(v.bagUsed()).isEqualTo(3);
        assertCode(() -> lunchService.pack(user, "lunchbox", 1), "BAG_FULL");
        assertCode(() -> lunchService.cook(user, "lunchbox", 100), "INVALID_COUNT");
    }

    @Test
    @DisplayName("담기·꺼내기는 햄스터와 상관없이 여러 개를 한 번에 옮긴다")
    void packAndUnpackManyRegardlessOfHamster() {
        User user = newUser(30, 50);
        cookOneByOne(user, 3);
        LunchService.View v = lunchService.pack(user, "lunchbox", 2); // 구하기 전
        assertThat(v.bag()).containsOnly(Map.entry("lunchbox", 2));
        assertThat(v.cupboardCount()).isEqualTo(1);
        v = lunchService.unpack(user, "lunchbox", 2);
        assertThat(v.bag()).isEmpty();
        assertThat(v.cupboardCount()).isEqualTo(3);

        rescueHamster(); // 구한 뒤
        v = lunchService.pack(user, "lunchbox", 3);
        assertThat(v.bag()).containsOnly(Map.entry("lunchbox", 3));
        v = lunchService.unpack(user, "lunchbox", 2);
        assertThat(v.bag()).containsOnly(Map.entry("lunchbox", 1));
        assertThat(v.cupboardCount()).isEqualTo(2);
    }
}
