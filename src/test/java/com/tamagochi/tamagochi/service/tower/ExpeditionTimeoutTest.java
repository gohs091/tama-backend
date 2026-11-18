package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import com.tamagochi.tamagochi.domain.tower.TowerRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tower.TowerProgressRepository;
import com.tamagochi.tamagochi.service.lunch.LunchService;
import com.tamagochi.tamagochi.service.tamagotchi.RedrawService;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 원정은 출발한 지 60분이 지나면 집으로 돌아온 것으로 본다. 가방은 그대로다 */
class ExpeditionTimeoutTest extends TowerLunchTestSupport {

    @Autowired TowerProgressRepository progressRepository;
    @Autowired TowerExpeditionScheduler scheduler;
    @Autowired TamagotchiService tamagotchiService;
    @Autowired RedrawService redrawService;

    private Instant start;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        start = clock.now;
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
    }

    @AfterEach
    void restoreClock() {
        clock.now = start; // 다른 테스트와 시계를 같이 쓴다
    }

    /** 출발한 뒤로 이만큼 지난 시각으로 시계를 옮긴다 */
    private void sinceDeparture(long minutes, long seconds) {
        clock.now = start.plusSeconds(minutes * 60 + seconds);
    }

    private TowerProgress progress(User user) {
        return progressRepository.findByUserId(user.getId()).orElseThrow();
    }

    /** 가방에 도시락 n개를 챙겨 떠난 유저 */
    private User departedWithLunchboxes(int n) {
        User user = newUser(3 * n, 100);
        if (n > 0) {
            cookOneByOne(user, n);
            lunchService.pack(user, "lunchbox", n);
        }
        towerService.depart(user, null);
        return user;
    }

    private static String key() {
        return "k-" + System.nanoTime();
    }

    @Test
    @DisplayName("출발하면 출발 시각을 적고, 제한 시간은 60분이다")
    void departRecordsTime() {
        assertThat(TowerRules.EXPEDITION_TIMEOUT_MINUTES).isEqualTo(60);
        User user = newUser(0, 100);
        assertThat(towerService.get(user).expeditionSecondsLeft()).isNull();

        TowerService.View v = towerService.depart(user, null);
        assertThat(v.expeditionSecondsLeft()).isEqualTo(3600);
        assertThat(progress(user).getDepartedAt()).isEqualTo(java.time.LocalDateTime.ofInstant(start, clock.getZone()));

        sinceDeparture(10, 0);
        assertThat(towerService.get(user).expeditionSecondsLeft()).isEqualTo(3000);
    }

    @Test
    @DisplayName("59분 뒤 get(): 아직 원정 중이고 가방이 그대로다")
    void stillOutAt59Minutes() {
        User user = departedWithLunchboxes(2);
        sinceDeparture(59, 0);

        TowerService.View v = towerService.get(user);
        assertThat(v.onExpedition()).isTrue();
        assertThat(v.result().expeditionEnded()).isFalse();
        assertThat(v.result().endReason()).isNull();
        assertThat(v.bagCount()).isEqualTo(2);
        assertThat(progress(user).getDepartedAt()).isNotNull();
        assertThat(towerService.clearMap(user).result().mapCleared()).isTrue(); // 맵도 계속 통과할 수 있다
    }

    @Test
    @DisplayName("60분 1초 뒤 get(): 집으로 돌아와 있고 endReason=TIME_UP, 가방 개수 그대로, departedAt은 비워진다")
    void timeUpAfter60Minutes() {
        User user = departedWithLunchboxes(2);
        towerService.clearMap(user); // 1층 맵 2까지 갔다
        sinceDeparture(60, 1);

        TowerService.View v = towerService.get(user);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(v.result().endReason()).isEqualTo("TIME_UP").isEqualTo(TowerService.TIME_UP);
        assertThat(v.result().mapCleared()).isFalse();
        assertThat(v.bagCount()).isEqualTo(2);
        assertThat(v.expeditionSecondsLeft()).isNull();
        assertThat(v.satiety()).isEqualTo(65);     // 포만감·진행은 그대로
        assertThat(v.currentMap()).isEqualTo(2);
        assertThat(progress(user).getDepartedAt()).isNull();
        assertThat(progress(user).isOnExpedition()).isFalse();
        assertThat(lunchService.get(user).bag()).containsEntry("lunchbox", 2);

        // 한 번 알린 뒤에는 평소 상태다
        v = towerService.get(user);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.result().expeditionEnded()).isFalse();
    }

    @Test
    @DisplayName("딱 60분이 되면 만료다 (59분 59초까지는 원정 중)")
    void boundary() {
        User user = departedWithLunchboxes(0);
        sinceDeparture(59, 59);
        assertThat(towerService.get(user).onExpedition()).isTrue();
        sinceDeparture(60, 0);
        assertThat(towerService.get(user).result().endReason()).isEqualTo(TowerService.TIME_UP);
    }

    @Test
    @DisplayName("만료 뒤 맵 통과는 통과시키지 않고 TIME_UP으로 알린다 (포만감을 쓰지 않는다). 돌아오기도 TIME_UP")
    void clearMapAndReturnAfterTimeUp() {
        User user = departedWithLunchboxes(1);
        sinceDeparture(61, 0);

        TowerService.View v = towerService.clearMap(user);
        assertThat(v.result().mapCleared()).isFalse();
        assertThat(v.result().spentSatiety()).isZero();
        assertThat(v.result().endReason()).isEqualTo(TowerService.TIME_UP);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.satiety()).isEqualTo(100);
        assertThat(v.currentMap()).isEqualTo(1);
        assertThat(v.bagCount()).isEqualTo(1);
        assertThat(progress(user).isOnExpedition()).isFalse(); // 귀가가 저장됐다
        assertCode(() -> towerService.clearMap(user), "NOT_ON_EXPEDITION");

        User other = departedWithLunchboxes(0); // 시계는 이미 61분 뒤: 지금 떠난 원정이다
        sinceDeparture(61 + 61, 0);
        v = towerService.returnHome(other);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.result().endReason()).isEqualTo(TowerService.TIME_UP);
    }

    @Test
    @DisplayName("스스로 돌아오면 예전처럼 RETURNED이고 departedAt이 비워진다")
    void returnHomeIsStillReturned() {
        User user = departedWithLunchboxes(1);
        sinceDeparture(30, 0);

        TowerService.View v = towerService.returnHome(user);
        assertThat(v.result().expeditionEnded()).isTrue();
        assertThat(v.result().endReason()).isEqualTo("RETURNED");
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.bagCount()).isEqualTo(1);
        assertThat(progress(user).getDepartedAt()).isNull();
    }

    @Test
    @DisplayName("만료 뒤에는 탑을 열어 보지 않아도 집에서 먹이기가 ON_EXPEDITION으로 거절되지 않는다")
    void feedingAtHomeWorksAfterTimeUp() {
        User user = newUser(0, 50);
        towerService.depart(user, null);
        assertCode(() -> tamagotchiService.feed(user, key()), "ON_EXPEDITION");

        sinceDeparture(60, 1);
        tamagotchiService.feed(user, key());
        assertThat(pet(user).satiety()).isEqualTo(53);
        assertThat(progress(user).isOnExpedition()).isFalse();
        assertThat(progress(user).getDepartedAt()).isNull();

        tamagotchiService.recover(user, key()); // 고기가 들어가는 회복도 된다
        assertThat(pet(user).satiety()).isEqualTo(65);
    }

    @Test
    @DisplayName("만료 뒤에는 도시락 담기·꺼내기가 ON_EXPEDITION으로 거절되지 않고, 도시락 먹기는 탑이 아니라서 거절된다")
    void packingWorksAfterTimeUp() {
        User user = newUser(6, 60);
        cookOneByOne(user, 2);
        lunchService.pack(user, "lunchbox", 1);
        towerService.depart(user, null);
        assertCode(() -> lunchService.pack(user, "lunchbox", 1), "ON_EXPEDITION");

        sinceDeparture(60, 1);
        LunchService.View v = lunchService.pack(user, "lunchbox", 1);
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.bag()).containsEntry("lunchbox", 2);
        assertThat(lunchService.unpack(user, "lunchbox", 1).bag()).containsEntry("lunchbox", 1);
        assertThat(progress(user).isOnExpedition()).isFalse();

        assertCode(() -> lunchService.eat(user, "lunchbox"), "NOT_ON_EXPEDITION");
        assertThat(lunchService.get(user).bag()).containsEntry("lunchbox", 1);
    }

    @Test
    @DisplayName("만료 뒤에는 새로 시작·다시 그리기도 ON_EXPEDITION으로 거절되지 않는다")
    void restartWorksAfterTimeUp() {
        User user = newUser(0, 80);
        towerService.depart(user, null);
        assertCode(() -> tamagotchiService.reset(user), "ON_EXPEDITION");

        sinceDeparture(60, 1);
        tamagotchiService.reset(user);
        assertThat(pet(user).getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        assertThat(progress(user).isOnExpedition()).isFalse();

        User other = newUser(0, 80);
        towerService.depart(other, null); // 시계는 이미 61분 뒤: 지금 떠난 원정이다
        assertCode(() -> redrawService.redraw(other), "ON_EXPEDITION");
        clock.now = clock.now.plusSeconds(3601);
        redrawService.redraw(other);
        assertThat(pet(other).getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
    }

    @Test
    @DisplayName("만료 뒤에는 바로 다시 떠날 수 있고, 새 원정의 60분이 다시 시작된다")
    void departAgainAfterTimeUp() {
        User user = departedWithLunchboxes(0);
        assertCode(() -> towerService.depart(user, null), "ON_EXPEDITION");
        sinceDeparture(61, 0);

        TowerService.View v = towerService.depart(user, null);
        assertThat(v.onExpedition()).isTrue();
        assertThat(v.result().expeditionEnded()).isFalse();
        assertThat(v.currentMap()).isEqualTo(1);
        assertThat(v.expeditionSecondsLeft()).isEqualTo(3600);

        sinceDeparture(61 + 59, 0);
        assertThat(towerService.get(user).onExpedition()).isTrue();
        sinceDeparture(61 + 60, 1);
        assertThat(towerService.get(user).result().endReason()).isEqualTo(TowerService.TIME_UP);
    }

    @Test
    @DisplayName("스케줄러만 돌려도 만료된 원정이 접힌다 (앱을 열지 않은 사람). 아직 시간이 남은 원정과 가방은 그대로다")
    void schedulerEndsTimedOutExpeditions() {
        User late = departedWithLunchboxes(2);
        sinceDeparture(30, 0);
        User fresh = departedWithLunchboxes(1);

        scheduler.sweep(); // 아직 아무도 만료가 아니다
        assertThat(progress(late).isOnExpedition()).isTrue();
        assertThat(progress(fresh).isOnExpedition()).isTrue();

        sinceDeparture(60, 1); // late는 60분 1초, fresh는 30분 1초
        scheduler.sweep();

        assertThat(progress(late).isOnExpedition()).isFalse();
        assertThat(progress(late).getDepartedAt()).isNull();
        assertThat(progress(fresh).isOnExpedition()).isTrue();
        assertThat(progress(fresh).getDepartedAt()).isNotNull();

        TowerService.View v = towerService.get(late); // 이미 접혀 있으니 이번 조회가 끝낸 것이 아니다
        assertThat(v.onExpedition()).isFalse();
        assertThat(v.result().expeditionEnded()).isFalse();
        assertThat(v.bagCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("출발 시각이 없는 예전 원정은 처음 본 때를 출발로 삼는다: 바로 끝나지 않고, 그로부터 60분 뒤에 끝난다")
    void legacyExpeditionWithoutDepartedAt() {
        User user = departedWithLunchboxes(1);
        TowerProgress legacy = progress(user);
        ReflectionTestUtils.setField(legacy, "departedAt", null);
        progressRepository.save(legacy);

        sinceDeparture(600, 0); // 떠난 지 오래됐지만 기록이 없다
        TowerService.View v = towerService.get(user);
        assertThat(v.onExpedition()).isTrue();
        assertThat(progress(user).getDepartedAt()).isNotNull();

        sinceDeparture(600 + 59, 0);
        assertThat(towerService.get(user).onExpedition()).isTrue();
        sinceDeparture(600 + 60, 1);
        v = towerService.get(user);
        assertThat(v.result().endReason()).isEqualTo(TowerService.TIME_UP);
        assertThat(v.bagCount()).isEqualTo(1);
    }
}
