package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.point.PointService;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 탑 원정 중에는 가방에 챙긴 도시락만 먹을 수 있다: 집에서 포만감을 채우는 먹이기는 거절한다 */
class ExpeditionFeedingTest extends TowerLunchTestSupport {

    @Autowired TamagotchiService tamagotchiService;
    @Autowired PointService pointService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void redis() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
    }

    private int ink(User user) {
        return pointService.getWallet(user).getBalance();
    }

    private static String key() {
        return "k-" + System.nanoTime();
    }

    @Test
    @DisplayName("원정 중 고기 먹이기는 거절되고 포만감·잉크가 그대로다. 돌아오면 다시 먹일 수 있다")
    void meatIsRejectedOnExpeditionAndAllowedAfterReturn() {
        User user = newUser(0, 50);
        int ink = ink(user);
        towerService.depart(user, null);

        assertCode(() -> tamagotchiService.feed(user, key()), "ON_EXPEDITION");
        assertThat(pet(user).satiety()).isEqualTo(50);
        assertThat(pet(user).getFeedCount()).isZero();
        assertThat(ink(user)).isEqualTo(ink);

        towerService.returnHome(user);
        tamagotchiService.feed(user, key());
        assertThat(pet(user).satiety()).isEqualTo(53);
        assertThat(ink(user)).isEqualTo(ink - 5);
    }

    @Test
    @DisplayName("원정 중에도 싸 간 도시락은 먹을 수 있고, 알약(파워)도 먹일 수 있다")
    void lunchboxAndPillStillWorkOnExpedition() {
        User user = newUser(3, 50);
        lunchService.cook(user, "lunchbox", 1);
        lunchService.pack(user, "lunchbox", 1);
        int ink = ink(user);
        towerService.depart(user, null);

        assertThat(lunchService.eat(user, "lunchbox").satiety()).isEqualTo(85);

        tamagotchiService.eatPill(user, key());
        assertThat(pet(user).getStrength()).isEqualTo(83);
        assertThat(pet(user).satiety()).isEqualTo(85);
        assertThat(ink(user)).isEqualTo(ink - 5);
    }

    @Test
    @DisplayName("원정 중 한 판만큼 회복은 고기가 들어가면 거절되고, 포만감이 가득이라 알약만 들어가면 된다")
    void recoverIsRejectedOnExpeditionOnlyWhenItFeedsMeat() {
        User hungry = newUser(0, 50);
        int ink = ink(hungry);
        towerService.depart(hungry, null);
        assertCode(() -> tamagotchiService.recover(hungry, key()), "ON_EXPEDITION");
        assertThat(pet(hungry).satiety()).isEqualTo(50);
        assertThat(pet(hungry).getStrength()).isEqualTo(80);
        assertThat(ink(hungry)).isEqualTo(ink);

        User full = newUser(0, 100);
        int fullInk = ink(full);
        towerService.depart(full, null);
        tamagotchiService.recover(full, key()); // 포만감이 가득이라 알약 4개만
        assertThat(pet(full).satiety()).isEqualTo(100);
        assertThat(pet(full).getStrength()).isEqualTo(92);
        assertThat(ink(full)).isEqualTo(fullInk - 20);
    }
}
