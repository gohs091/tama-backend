package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TamagotchiVitalsTest {

    // 포만감 6분/1, 파워 12분/1, 똥 60분/1 (8개에서 멈춘다). 어느 것으로도 떠나지 않는다
    private static final VitalsRule RULE = new VitalsRule(6, 12, 60, 8, Duration.ofDays(365));
    // 서버 중단 감지: 틱 간격이 5분을 넘으면 그 시간은 흐르지 않은 것으로 본다
    private static final VitalsRule STRICT = new VitalsRule(6, 12, 60, 8, Duration.ofMinutes(5));
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
    @DisplayName("자는 동안은 배도 안 고프고 응가도 안 하며, 피로만큼 자고 혼자 깬다")
    void sleeping_freezesVitals_andWakesByItself() {
        Tamagotchi p = baby(10, 90);
        p.applyTime(T0, RULE);
        p.applyTime(T0.plusHours(5), RULE);                  // 깨어 있던 5시간: 피로 +20
        int hunger = p.getHunger(), strength = p.getStrength(), poop = p.getPoopCount();
        LocalDateTime slept = T0.plusHours(5);
        p.sleep(slept);
        assertThat(p.sleepLength()).isEqualTo(Duration.ofSeconds(Math.round(0.2 * 7 * 3600))); // 1.4시간
        p.applyTime(slept.plusHours(1), RULE);
        assertThat(p.isSleeping()).isTrue();
        assertThat(p.getHunger()).isEqualTo(hunger);
        assertThat(p.getStrength()).isEqualTo(strength);
        assertThat(p.getPoopCount()).isEqualTo(poop);

        p.applyTime(slept.plusHours(2), RULE);                // 1.4시간에 혼자 깼다
        assertThat(p.isSleeping()).isFalse();
        assertThat(p.fatigue()).isCloseTo(2.4, org.assertj.core.data.Offset.offset(0.05)); // 깬 뒤 0.6시간 × 4
    }

    @Test
    @DisplayName("불을 끄면 언제든 잠든다. 방금 깼거나 활동이 없으면 짧게(20분) 잔다")
    void sleep_anytime_shortWhenRested() {
        pet.applyTime(T0, RULE);
        pet.sleep(T0);
        assertThat(pet.isSleeping()).isTrue();
        assertThat(pet.sleepLength()).isEqualTo(Tamagotchi.SLEEP_MIN);
        pet.applyTime(T0.plusMinutes(21), RULE);
        assertThat(pet.isSleeping()).isFalse();
    }

    @Test
    @DisplayName("피곤하면(피로 70 이상) 졸려 하고, 활동이 많으면 7시간을 다 잔다")
    void sleepy_byFatigue() {
        pet.applyTime(T0, RULE);
        assertThat(pet.isSleepy(T0)).isFalse();
        for (int i = 0; i < 10; i++) pet.addFatigue(Tamagotchi.FATIGUE_TRAINING);
        assertThat(pet.isSleepy(T0)).isTrue();
        pet.sleep(T0);
        assertThat(pet.sleepLength()).isEqualTo(Tamagotchi.SLEEP_MAX);
        pet.wakeUp();
        assertThat(pet.isSleeping()).isFalse();
    }

    @Test
    @DisplayName("고기 한 번에 포만감 +3, 가득 차면 isFull (백엔드 hunger는 0이 가득)")
    void feeding_addsThree_andStopsAtFull() {
        Tamagotchi p = baby(5, 50);
        assertThat(p.isFull()).isFalse();
        p.feed();
        assertThat(p.getHunger()).isEqualTo(2);
        p.feed();
        assertThat(p.getHunger()).isZero();
        assertThat(p.isFull()).isTrue();
    }

    @Test
    @DisplayName("자다가 먹이면 깬다")
    void feeding_wakesUp() {
        LocalDateTime night = LocalDateTime.of(2026, 10, 23, 22, 0);
        Tamagotchi p = baby(50, 50);
        p.sleep(night);
        p.feed();
        assertThat(p.isSleeping()).isFalse();
        p.sleep(night);
        p.addStrength(1);
        assertThat(p.isSleeping()).isFalse();
    }

    @Test
    @DisplayName("잉크통 충전 중에도 소진되지 않고, 나온 직후 한꺼번에 깎이지 않는다")
    void bathing_pausesDecay() {
        pet.setBathing(true);
        pet.applyTime(after(300), RULE);
        pet.setBathing(false);
        pet.applyTime(after(301), RULE);
        assertThat(pet.getHunger()).isEqualTo(50);
        assertThat(pet.getStrength()).isEqualTo(50);
        assertThat(pet.getPoopCount()).isZero();
    }

    @Test
    @DisplayName("포만감이 0인 채 며칠이 지나도 떠나지 않고, 배고픔 디버프만 받는다")
    void starving_neverDies_onlyHungryDebuff() {
        Tamagotchi p = baby(99, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(6), RULE);                       // hunger 100 → 굶주림 시작
        assertThat(p.getHunger()).isEqualTo(100);
        assertThat(p.getHungryStartedAt()).isEqualTo(after(6)); // 얼마나 굶었는지는 계속 기록한다
        p.cleanPoop();
        for (long h = 1; h <= 72; h++) p.applyTime(after(6 + h * 60), RULE); // 사흘 동안 굶는다
        assertThat(p.getHunger()).isEqualTo(100);          // 100에서 멈춘다
        assertThat(p.isDead()).isFalse();
        assertThat(p.getDeadAt()).isNull();
        assertThat(p.debuffs()).contains(DebuffRules.Debuff.HUNGRY);
    }

    @Test
    @DisplayName("배고픔 디버프는 고기를 먹여 포만감이 20을 넘으면 바로 풀린다")
    void feeding_clearsHungryDebuff() {
        Tamagotchi p = baby(81, 100);
        assertThat(p.debuffs()).containsExactly(DebuffRules.Debuff.HUNGRY);
        p.feed();                                          // hunger 78
        assertThat(p.debuffs()).isEmpty();
        assertThat(p.getHungryStartedAt()).isNull();
    }

    @Test
    @DisplayName("0 상태에서 먹이를 주면 굶주림 기록이 풀린다")
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
    @DisplayName("파워가 0인 채 며칠이 지나도 떠나지 않고, 기운 없음 디버프만 받는다")
    void exhausted_neverDies_onlyWeakDebuff() {
        Tamagotchi p = baby(0, 1);
        p.applyTime(T0, RULE);
        p.applyTime(after(12), RULE);                      // strength 0
        assertThat(p.getStrength()).isZero();
        for (long h = 1; h <= 72; h++) p.applyTime(after(12 + h * 60), RULE);
        assertThat(p.getStrength()).isZero();              // 0에서 멈춘다
        assertThat(p.isDead()).isFalse();
        assertThat(p.debuffs()).contains(DebuffRules.Debuff.WEAK);
    }

    @Test
    @DisplayName("기운 없음 디버프는 알약으로 파워가 20 이상이 되면 바로 풀린다")
    void pill_clearsWeakDebuff() {
        Tamagotchi p = baby(0, 0);
        assertThat(p.debuffs()).containsExactly(DebuffRules.Debuff.WEAK);
        p.addStrength(19);
        assertThat(p.debuffs()).containsExactly(DebuffRules.Debuff.WEAK);
        p.addStrength(1);
        assertThat(p.debuffs()).isEmpty();
    }

    @Test
    @DisplayName("알약으로 파워를 채우면 파워 0 기록이 풀린다")
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
    @DisplayName("똥이 최대치(8개)까지 쌓여도 떠나지 않고, 거기서 더 늘지 않는다")
    void poop_full_neverDies_andStopsAtMax() {
        Tamagotchi p = baby(0, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(8 * 60), RULE);
        assertThat(p.getPoopCount()).isEqualTo(8);
        assertThat(p.isDead()).isFalse();
        p.applyTime(after(40 * 60), RULE);
        assertThat(p.getPoopCount()).isEqualTo(8);         // 상한에서 멈춘다
        assertThat(p.isDead()).isFalse();
        assertThat(p.debuffs()).contains(DebuffRules.Debuff.DIRTY);
    }

    @Test
    @DisplayName("똥이 5개 쌓이면 지저분함 디버프가 켜지고, 청소하면 바로 풀린다")
    void dirtyDebuff_onAtFive_clearedByCleaning() {
        Tamagotchi p = baby(0, 100);
        p.applyTime(T0, RULE);
        p.applyTime(after(4 * 60), RULE);
        assertThat(p.debuffs()).isEmpty();                 // 4개까지는 괜찮다
        p.applyTime(after(5 * 60), RULE);
        assertThat(p.debuffs()).containsExactly(DebuffRules.Debuff.DIRTY);
        p.cleanPoop();
        assertThat(p.debuffs()).isEmpty();
    }

    @Test
    @DisplayName("지저분하면 트레이닝 경험치가 절반(올림)만 들어오고 스파링 컨디션이 20 낮아진다. 청소하면 돌아온다")
    void dirtyDebuff_halvesTrainingExp_andLowersCondition() {
        Tamagotchi clean = baby(20, 80);
        Tamagotchi dirty = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(20).strength(80)
                .poopCount(DebuffRules.DIRTY_AT).build();
        assertThat(clean.train(40, false).exp()).isEqualTo(15);  // 별 3개 × 5
        assertThat(dirty.train(40, false).exp()).isEqualTo(8);   // 15의 절반, 올림
        assertThat(dirty.pendingExp()).isEqualTo(8);             // 광고로 받는 몫도 같다
        assertThat(clean.sparCondition()).isEqualTo(80);
        assertThat(dirty.sparCondition()).isEqualTo(60);

        dirty.cleanPoop();
        assertThat(dirty.sparCondition()).isEqualTo(80);
        assertThat(dirty.train(40, false).exp()).isEqualTo(15);
    }

    @Test
    @DisplayName("셋 다 최악인 채로 스케줄러가 1분마다 일주일을 돌아도 dead=false, 디버프 셋이 모두 켜진다")
    void worstCase_weekOfTicks_neverDies() {
        Tamagotchi p = baby(100, 0);
        p.applyTime(T0, STRICT);
        for (long m = 1; m <= 7 * 24 * 60; m++) p.applyTime(after(m), STRICT);
        assertThat(p.isDead()).isFalse();
        assertThat(p.getDeadAt()).isNull();
        assertThat(p.getHunger()).isEqualTo(100);
        assertThat(p.getStrength()).isZero();
        assertThat(p.getPoopCount()).isEqualTo(8);
        assertThat(p.debuffs()).containsExactly(DebuffRules.Debuff.HUNGRY, DebuffRules.Debuff.WEAK, DebuffRules.Debuff.DIRTY);
    }

    @Test
    @DisplayName("알은 디버프가 없다")
    void egg_hasNoDebuffs() {
        Tamagotchi egg = Tamagotchi.builder().evolutionStage(EvolutionStage.EGG).hunger(100).strength(0).poopCount(8).build();
        assertThat(egg.debuffs()).isEmpty();
    }

    @Test
    @DisplayName("예전 규칙으로 이미 떠난 끄적이는 그대로 멈춰 있다 (데이터를 건드리지 않는다)")
    void legacyDead_staysFrozen() {
        Tamagotchi p = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(30).strength(70)
                .deadAt(T0.minusDays(1)).build();
        p.applyTime(T0, RULE);
        p.applyTime(after(20 * 60), RULE);
        assertThat(p.isDead()).isTrue();
        assertThat(p.getHunger()).isEqualTo(30);
        assertThat(p.getPoopCount()).isZero();
        assertThat(p.debuffs()).isEmpty();
    }

    @Test
    @DisplayName("예전에 떠난 끄적이도 reset하면 새 알로 시작하고 초기값으로 돌아간다")
    void reset_revives() {
        Tamagotchi p = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(0).strength(100)
                .poopCount(8).deadAt(T0).build();
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
    @DisplayName("굶주림 기록도 중단 시간만큼 뒤로 밀리고, 중단 뒤에도 떠나지 않는다")
    void downtime_shiftsStarvationMarker() {
        Tamagotchi p = baby(99, 100);
        p.applyTime(T0, STRICT);
        tickEveryMinute(p, 1, 6);              // 6분에 hunger 100 → 굶주림 시작
        long awake = 6 + 180;
        tickEveryMinute(p, 7, awake);          // 굶주린 채 3시간 경과
        assertThat(p.getHungryStartedAt()).isEqualTo(after(6));

        long resume = awake + 8 * 60;          // 8시간 중단
        p.applyTime(after(resume), STRICT);
        assertThat(p.getHungryStartedAt()).isEqualTo(after(6 + 8 * 60)); // 중단 시간은 굶은 시간으로 세지 않는다

        for (long m = resume + 1; m <= resume + 600; m++) p.applyTime(after(m), STRICT);
        assertThat(p.isDead()).isFalse();      // 굶은 시간이 아무리 길어도 떠나지 않는다
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
