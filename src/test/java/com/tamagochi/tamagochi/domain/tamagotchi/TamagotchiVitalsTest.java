package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TamagotchiVitalsTest {

    // 포만감 6분/1, 파워 12분/1, 0인 채 4시간이면 사망, 똥 60분/1, 8개면 사망
    private static final VitalsRule RULE = new VitalsRule(6, 12, Duration.ofHours(4), 60, 8, Duration.ofDays(365));
    // 서버 중단 감지: 틱 간격이 5분을 넘으면 그 시간은 흐르지 않은 것으로 본다
    private static final VitalsRule STRICT = new VitalsRule(6, 12, Duration.ofHours(4), 60, 8, Duration.ofMinutes(5));
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 23, 12, 0);

    private Tamagotchi pet;

    @BeforeEach
    void setUp() {
        pet = baby(50, 50);
        pet.applyTime(T0, RULE); // 기준 시각 설정 (첫 tick은 감소 없음)
    }

    private static Tamagotchi baby(int hunger, int strength) {
        return Tamagotchi.builder()
                .evolutionStage(EvolutionStage.BABY)
                .hunger(hunger)
                .strength(strength)
                .build();
    }

    private LocalDateTime after(long minutes) {
        return T0.plusMinutes(minutes);
    }

    @Test
    @DisplayName("알은 시간이 지나도 변하지 않는다")
    void egg_doesNotChange() {
        Tamagotchi egg = Tamagotchi.builder().evolutionStage(EvolutionStage.EGG).hunger(50).strength(50).build();
        egg.applyTime(T0, RULE);
        egg.applyTime(after(600), RULE);
        assertThat(egg.getHunger()).isEqualTo(50);
        assertThat(egg.getStrength()).isEqualTo(50);
        assertThat(egg.getPoopCount()).isZero();
        assertThat(egg.isDead()).isFalse();
    }

    @Test
    @DisplayName("첫 tick은 기준 시각만 잡고 감소하지 않는다")
    void firstTick_setsBaselineOnly() {
        assertThat(pet.getHunger()).isEqualTo(50);
        assertThat(pet.getStrength()).isEqualTo(50);
    }

    @Test
    @DisplayName("포만감은 6분마다 1, 파워는 12분마다 1씩 줄고 나머지 시간은 누적된다")
    void decaysGradually() {
        pet.applyTime(after(5), RULE);
        assertThat(pet.getHunger()).isEqualTo(50); // 6분 미만 → 변화 없음

        pet.applyTime(after(6), RULE);
        assertThat(pet.getHunger()).isEqualTo(51);

        pet.applyTime(after(60), RULE);
        assertThat(pet.getHunger()).isEqualTo(60);   // 60분 → 10
        assertThat(pet.getStrength()).isEqualTo(45); // 60분 → 5
    }

    @Test
    @DisplayName("tick이 잦아도 나머지 시간이 유실되지 않는다")
    void remainderIsNotLost() {
        for (int m = 1; m <= 60; m++) pet.applyTime(after(m), RULE);
        assertThat(pet.getHunger()).isEqualTo(60);
    }

    @Test
    @DisplayName("수면 중에는 소진되지 않고, 깨어난 직후에도 한꺼번에 깎이지 않는다")
    void sleeping_pausesDecay() {
        pet.sleep();
        pet.applyTime(after(300), RULE);
        pet.wakeUp();
        pet.applyTime(after(301), RULE);
        assertThat(pet.getHunger()).isEqualTo(50);
        assertThat(pet.getStrength()).isEqualTo(50);
        assertThat(pet.getPoopCount()).isZero();
    }

    @Test
    @DisplayName("포만감이 0이 되어도 4시간이 지나기 전엔 살아 있다")
    void starving_aliveBeforeFourHours() {
        Tamagotchi p = baby(99, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(6), RULE);                       // hunger 100 → 굶주림 시작
        assertThat(p.getHunger()).isEqualTo(100);
        p.applyTime(after(6 + 239), RULE);
        assertThat(p.isDead()).isFalse();
    }

    @Test
    @DisplayName("포만감이 0인 채 4시간이 지나면 사망한다")
    void starving_diesAfterFourHours() {
        Tamagotchi p = baby(99, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(6), RULE);
        p.applyTime(after(6 + 240), RULE);
        assertThat(p.isDead()).isTrue();
    }

    @Test
    @DisplayName("0 상태에서 먹이를 주면 사망 타이머가 해제된다")
    void feeding_resetsStarvationTimer() {
        Tamagotchi p = baby(99, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(6), RULE);
        p.applyTime(after(6 + 200), RULE);
        p.feed();
        p.applyTime(after(6 + 201), RULE);
        assertThat(p.getHungryStartedAt()).isNull();
        p.applyTime(after(6 + 300), RULE);
        assertThat(p.isDead()).isFalse();
    }

    @Test
    @DisplayName("파워가 0인 채 4시간이 지나면 사망한다")
    void exhausted_diesAfterFourHours() {
        Tamagotchi p = baby(0, 1);
        p.applyTime(T0, RULE);
        p.applyTime(after(12), RULE);                      // strength 0
        assertThat(p.getStrength()).isZero();
        p.applyTime(after(12 + 239), RULE);
        assertThat(p.isDead()).isFalse();
        p.applyTime(after(12 + 240), RULE);
        assertThat(p.isDead()).isTrue();
    }

    @Test
    @DisplayName("알약으로 파워를 채우면 사망 타이머가 해제된다")
    void pill_resetsExhaustionTimer() {
        Tamagotchi p = baby(0, 1);
        p.applyTime(T0, RULE);
        p.applyTime(after(12), RULE);
        p.applyTime(after(12 + 200), RULE);
        p.addStrength(20);
        p.applyTime(after(12 + 201), RULE);
        assertThat(p.getStrengthEmptySince()).isNull();
        p.applyTime(after(12 + 300), RULE);
        assertThat(p.isDead()).isFalse();
    }

    @Test
    @DisplayName("똥은 60분마다 1개씩 늘어난다")
    void poop_growsOverTime() {
        pet.applyTime(after(59), RULE);
        assertThat(pet.getPoopCount()).isZero();
        pet.applyTime(after(60), RULE);
        assertThat(pet.getPoopCount()).isEqualTo(1);
        pet.applyTime(after(180), RULE);
        assertThat(pet.getPoopCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("청소하면 똥이 0이 되고 간격이 다시 시작된다")
    void clean_resetsPoopAndInterval() {
        pet.applyTime(after(120), RULE);
        assertThat(pet.getPoopCount()).isEqualTo(2);
        pet.cleanPoop();
        assertThat(pet.getPoopCount()).isZero();
        // cleanPoop은 LocalDateTime.now() 기준으로 재시작하므로 과거 시각 tick은 증가시키지 않는다
        pet.applyTime(after(121), RULE);
        assertThat(pet.getPoopCount()).isZero();
    }

    @Test
    @DisplayName("똥이 최대치(8개)에 도달하면 사망한다")
    void poop_fullMeansDeath() {
        Tamagotchi p = baby(0, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(7 * 60), RULE);
        assertThat(p.getPoopCount()).isEqualTo(7);
        assertThat(p.isDead()).isFalse();
        p.applyTime(after(8 * 60), RULE);
        assertThat(p.isDead()).isTrue();
    }

    @Test
    @DisplayName("사망 후에는 상태가 더 이상 변하지 않는다")
    void dead_staysFrozen() {
        Tamagotchi p = baby(0, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(8 * 60), RULE);
        assertThat(p.isDead()).isTrue();
        int hunger = p.getHunger();
        p.applyTime(after(20 * 60), RULE);
        assertThat(p.getHunger()).isEqualTo(hunger);
    }

    @Test
    @DisplayName("reset하면 부활하고 초기값으로 돌아간다")
    void reset_revives() {
        Tamagotchi p = baby(0, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(8 * 60), RULE);
        p.reset();
        assertThat(p.isDead()).isFalse();
        assertThat(p.getHunger()).isEqualTo(Tamagotchi.INITIAL_HUNGER);
        assertThat(p.getStrength()).isEqualTo(Tamagotchi.INITIAL_STRENGTH);
        assertThat(p.getPoopCount()).isZero();
    }

    // ── 서버 중단(다운타임) ───────────────────────────────────

    /** 1분 간격으로 minutes 만큼 틱을 반복 */
    private void tickEveryMinute(Tamagotchi p, long fromMin, long toMin) {
        for (long m = fromMin; m <= toMin; m++) p.applyTime(after(m), STRICT);
    }

    @Test
    @DisplayName("서버가 오래 꺼져 있어도 그 시간은 반영되지 않는다 (재시작 직후 사망 방지)")
    void downtime_isNotCounted() {
        Tamagotchi p = baby(50, 50);
        p.applyTime(T0, STRICT);
        tickEveryMinute(p, 1, 10);
        int hunger = p.getHunger();
        int strength = p.getStrength();

        p.applyTime(after(10 + 600), STRICT); // 10시간 중단 후 첫 틱

        assertThat(p.getHunger()).isEqualTo(hunger);
        assertThat(p.getStrength()).isEqualTo(strength);
        assertThat(p.getPoopCount()).isZero();
        assertThat(p.isDead()).isFalse();
    }

    @Test
    @DisplayName("중단 후에도 시계는 이어서 흐른다 (정상 틱 간격이면 다시 감소)")
    void afterDowntime_decayResumes() {
        Tamagotchi p = baby(50, 50);
        p.applyTime(T0, STRICT);
        tickEveryMinute(p, 1, 10);
        long resume = 10 + 600;
        p.applyTime(after(resume), STRICT);
        int hunger = p.getHunger();

        for (long m = resume + 1; m <= resume + 12; m++) p.applyTime(after(m), STRICT);

        assertThat(p.getHunger()).isGreaterThan(hunger); // 12분 → 포만감 2 감소
    }

    @Test
    @DisplayName("중단 전에 쌓인 똥 때문에 재시작 직후 죽지 않는다")
    void downtime_doesNotKillWithPoop() {
        Tamagotchi p = baby(0, 100);
        p.applyTime(T0, STRICT);
        tickEveryMinute(p, 1, 7 * 60); // 7시간 동안 정상 틱 → 똥 7개
        assertThat(p.getPoopCount()).isEqualTo(7);

        p.applyTime(after(7 * 60 + 5 * 60), STRICT); // 5시간 중단 후 재시작

        assertThat(p.getPoopCount()).isEqualTo(7);
        assertThat(p.isDead()).isFalse();
    }

    @Test
    @DisplayName("굶주림 사망 타이머도 중단 시간만큼 멈춘다")
    void downtime_pausesStarvationTimer() {
        Tamagotchi p = baby(99, 100);
        p.applyTime(T0, STRICT);
        tickEveryMinute(p, 1, 6);              // 6분에 hunger 100 → 굶주림 시작
        long awake = 6 + 180;
        tickEveryMinute(p, 7, awake);          // 굶주린 채 3시간 경과
        assertThat(p.isDead()).isFalse();

        long resume = awake + 8 * 60;          // 8시간 중단
        p.applyTime(after(resume), STRICT);
        assertThat(p.isDead()).isFalse();      // 중단 시간은 세지 않으므로 아직 3시간 + α

        for (long m = resume + 1; m <= resume + 59; m++) p.applyTime(after(m), STRICT);
        assertThat(p.isDead()).isFalse();      // 합계 4시간 직전

        for (long m = resume + 60; m <= resume + 62; m++) p.applyTime(after(m), STRICT);
        assertThat(p.isDead()).isTrue();       // 깨어 있던 시간이 합계 4시간을 넘기면 사망
    }

    @Test
    @DisplayName("정상 틱 간격(1분)에서는 중단으로 오인하지 않는다")
    void normalTicks_notTreatedAsDowntime() {
        Tamagotchi p = baby(50, 50);
        p.applyTime(T0, STRICT);
        tickEveryMinute(p, 1, 60);
        assertThat(p.getHunger()).isEqualTo(60);
        assertThat(p.getPoopCount()).isEqualTo(1);
    }
}
