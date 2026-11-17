package com.tamagochi.tamagochi.service.lunch;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LunchServiceTest extends TowerLunchTestSupport {

    @Test
    @DisplayName("만들면 열매가 값 × 개수만큼 빠지고 찬장에 쌓인다. 하루 상한은 없다")
    void cookSpendsFruitAndFillsCupboard() {
        User user = newUser(30, 50);
        LunchService.View v = lunchService.cook(user, "riceball", 2);
        assertThat(v.fruit()).isEqualTo(26);
        assertThat(v.cupboard()).containsEntry("riceball", 2);
        assertThat(v.bag()).isEmpty();

        for (int i = 0; i < 5; i++) v = lunchService.cook(user, "hearty", 1); // 여러 번 만들어도 막지 않는다
        assertThat(v.fruit()).isEqualTo(1);
        assertThat(v.cupboard()).containsEntry("hearty", 5).containsEntry("riceball", 2);
        assertThat(fruit(user)).isEqualTo(1);
    }

    @Test
    @DisplayName("열매가 모자라면 만들지 않고 아무것도 바뀌지 않는다")
    void cookRejectsWhenFruitShort() {
        User user = newUser(3, 50);
        assertCode(() -> lunchService.cook(user, "riceball", 2), "NOT_ENOUGH_FRUIT");
        assertThat(fruit(user)).isEqualTo(3);
        assertThat(lunchService.get(user).cupboard()).isEmpty();
    }

    @Test
    @DisplayName("잠긴 도시락은 목록에 잠긴 것으로 나오고 만들 수 없다. 개발용 지급으로는 받을 수 있다")
    void lockedLunchboxCannotBeCooked() {
        User user = newUser(50, 50);
        LunchService.View v = lunchService.get(user);
        assertThat(v.catalog()).extracting(LunchService.LunchView::id).containsExactly("riceball", "hearty", "feast");
        assertThat(v.catalog()).extracting(LunchService.LunchView::locked).containsExactly(false, false, true);
        assertThat(v.catalog().get(2).unlock().type()).isEqualTo("NPC");

        assertCode(() -> lunchService.cook(user, "feast", 1), "LOCKED");
        assertThat(fruit(user)).isEqualTo(50);

        assertThat(lunchService.devGive(user, "feast", 1).cupboard()).containsEntry("feast", 1);
        assertThat(fruit(user)).isEqualTo(50);
    }

    @Test
    @DisplayName("없는 도시락, 1보다 작거나 너무 큰 개수는 거절한다")
    void rejectsBadIdAndCount() {
        User user = newUser(500, 50);
        assertCode(() -> lunchService.cook(user, "pizza", 1), "UNKNOWN_LUNCHBOX");
        assertCode(() -> lunchService.cook(user, "riceball", 0), "INVALID_COUNT");
        assertCode(() -> lunchService.cook(user, "riceball", -3), "INVALID_COUNT");
        assertCode(() -> lunchService.cook(user, "riceball", 100), "INVALID_COUNT");
        assertThat(fruit(user)).isEqualTo(500);
    }

    @Test
    @DisplayName("가방은 기본 2칸(도시락 2개)이고 넘치게 담을 수 없다. 꺼내면 찬장으로 돌아간다")
    void packRespectsBagSlots() {
        User user = newUser(20, 50);
        lunchService.cook(user, "riceball", 3);
        LunchService.View v = lunchService.pack(user, "riceball", 2);
        assertThat(v.bagSlots()).isEqualTo(2);
        assertThat(v.bagUsed()).isEqualTo(2);
        assertThat(v.cupboard()).containsEntry("riceball", 1);

        assertCode(() -> lunchService.pack(user, "riceball", 1), "BAG_FULL");
        assertCode(() -> lunchService.pack(user, "hearty", 1), "NOT_IN_CUPBOARD");

        v = lunchService.unpack(user, "riceball", 1);
        assertThat(v.bag()).containsEntry("riceball", 1);
        assertThat(v.cupboard()).containsEntry("riceball", 2);
        assertCode(() -> lunchService.unpack(user, "riceball", 2), "NOT_IN_BAG");
    }

    @Test
    @DisplayName("탑에 가 있는 동안에는 가방을 챙기거나 풀 수 없다. 돌아오면 가방에 남은 도시락은 그대로 있다")
    void cannotPackWhileOnExpedition() {
        User user = newUser(20, 100);
        lunchService.cook(user, "riceball", 2);
        lunchService.pack(user, "riceball", 1);
        towerService.depart(user, null);
        assertThat(lunchService.get(user).onExpedition()).isTrue();

        assertCode(() -> lunchService.pack(user, "riceball", 1), "ON_EXPEDITION");
        assertCode(() -> lunchService.unpack(user, "riceball", 1), "ON_EXPEDITION");
        assertThat(lunchService.cook(user, "riceball", 1).cupboard()).containsEntry("riceball", 2); // 만들기는 막지 않는다

        towerService.returnHome(user);
        LunchService.View v = lunchService.get(user);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.bag()).containsEntry("riceball", 1);
        assertThat(lunchService.unpack(user, "riceball", 1).bag()).isEmpty();
    }

    @Test
    @DisplayName("도시락은 탑 원정 중에만 먹을 수 있다")
    void eatOnlyOnExpedition() {
        User user = newUser(20, 50);
        lunchService.cook(user, "riceball", 1);
        lunchService.pack(user, "riceball", 1);

        assertCode(() -> lunchService.eat(user, "riceball"), "NOT_ON_EXPEDITION");
        assertThat(lunchService.get(user).bag()).containsEntry("riceball", 1);
        assertThat(pet(user).satiety()).isEqualTo(50);

        towerService.depart(user, null);
        LunchService.View v = lunchService.eat(user, "riceball");
        assertThat(v.recovered()).isEqualTo(20);
        assertThat(v.satiety()).isEqualTo(70);
        assertThat(v.bag()).isEmpty();
        assertCode(() -> lunchService.eat(user, "riceball"), "NOT_IN_BAG");
    }

    @Test
    @DisplayName("먹어도 포만감은 100을 넘지 않고, 이미 가득이면 도시락을 쓰지 않는다")
    void eatCapsSatietyAtHundred() {
        User user = newUser(20, 90);
        lunchService.cook(user, "riceball", 2);
        lunchService.pack(user, "riceball", 2);
        towerService.depart(user, null);

        LunchService.View v = lunchService.eat(user, "riceball");
        assertThat(v.recovered()).isEqualTo(10);
        assertThat(v.satiety()).isEqualTo(100);
        assertThat(pet(user).getHunger()).isZero();

        assertCode(() -> lunchService.eat(user, "riceball"), "FULL");
        assertThat(lunchService.get(user).bag()).containsEntry("riceball", 1);
    }

    @Test
    @DisplayName("가방에 하나뿐인 도시락을 동시에 두 번 먹으면 한 번만 먹힌다")
    void concurrentEatConsumesOnce() throws Exception {
        User user = newUser(20, 40);
        lunchService.cook(user, "riceball", 1);
        lunchService.pack(user, "riceball", 1);
        towerService.depart(user, null);

        List<String> results = together(2, () -> lunchService.eat(user, "riceball"));

        assertThat(results).containsExactlyInAnyOrder("OK", "NOT_IN_BAG");
        assertThat(pet(user).satiety()).isEqualTo(60);
        assertThat(lunchService.get(user).bag()).isEmpty();
    }

    @Test
    @DisplayName("동시에 만들어도 열매는 만든 만큼만 빠진다 (이중 차감·공짜 도시락 없음)")
    void concurrentCookSpendsFruitExactly() throws Exception {
        // 열매가 하나 몫뿐: 둘이 동시에 만들면 하나만 된다
        User poor = newUser(2, 50);
        List<String> results = together(2, () -> lunchService.cook(poor, "riceball", 1));
        assertThat(results).containsExactlyInAnyOrder("OK", "NOT_ENOUGH_FRUIT");
        assertThat(fruit(poor)).isZero();
        assertThat(lunchService.get(poor).cupboard()).containsEntry("riceball", 1);

        // 열매가 넉넉: 넷이 동시에 만들면 넷 다 되고 정확히 네 개 값만 빠진다
        User rich = newUser(20, 50);
        results = together(4, () -> lunchService.cook(rich, "riceball", 1));
        assertThat(results).containsOnly("OK");
        assertThat(fruit(rich)).isEqualTo(12);
        assertThat(lunchService.get(rich).cupboard()).containsEntry("riceball", 4);
    }
}
