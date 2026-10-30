package com.tamagochi.tamagochi.domain.tamagotchi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TamagotchiEvolutionTest {

    private static final int BABY_ZONE = LevelRules.START_LEVEL + LevelRules.zoneSpan(EvolutionStage.BABY); // 낙서 진화 가능 구간 시작 레벨
    private static final int CHILD_SPAN = LevelRules.zoneSpan(EvolutionStage.CHILD);

    // hunger는 0=포만감 최대. 포만감 = 100 - hunger
    private static Tamagotchi pet(EvolutionStage stage, int satiation, int strength, int poop) {
        return Tamagotchi.builder()
                .evolutionStage(stage)
                .hunger(100 - satiation)
                .strength(strength)
                .poopCount(poop)
                .build();
    }

    /** 갓 태어난 낙서 (Lv.1에서 시작, 컨디션은 원하는 값으로) */
    private static Tamagotchi newBaby(int satiation, int strength, int poop) {
        return Tamagotchi.builder()
                .evolutionStage(EvolutionStage.BABY)
                .hunger(100 - satiation).strength(strength).poopCount(poop)
                .level(1).exp(0).stageStartLevel(1).pendingExp(0)
                .weightDg(LevelRules.START_WEIGHT_DG).might(0)
                .build();
    }

    /** 레벨을 target까지 올린다 */
    private static void levelTo(Tamagotchi p, int target) {
        while (p.level() < target) p.addExp(p.expToNext() - p.exp());
    }

    @Test
    @DisplayName("5초 탭 수에 따라 별이 1~3개 (15 미만 1, 15~32 2, 33 이상 3). 꾹 누르기(초당 5번, 25번)는 별 2개")
    void gainForTaps() {
        assertThat(EvolutionRules.gainForTaps(0)).isEqualTo(1);
        assertThat(EvolutionRules.gainForTaps(14)).isEqualTo(1);
        assertThat(EvolutionRules.gainForTaps(15)).isEqualTo(2);
        assertThat(EvolutionRules.gainForTaps(25)).isEqualTo(2);
        assertThat(EvolutionRules.gainForTaps(32)).isEqualTo(2);
        assertThat(EvolutionRules.gainForTaps(33)).isEqualTo(3);
        assertThat(EvolutionRules.gainForTaps(60)).isEqualTo(3);
    }

    @Test
    @DisplayName("기본 경험치는 별 1개당 5이고, 진화 가능 구간에서는 절반(올림)만 들어온다")
    void expForTaps() {
        assertThat(LevelRules.expForTaps(10, false)).isEqualTo(5);
        assertThat(LevelRules.expForTaps(40, false)).isEqualTo(15);
        assertThat(LevelRules.expForTaps(40, true)).isEqualTo(8);
        assertThat(LevelRules.expForTaps(10, true)).isEqualTo(3);
    }

    @Test
    @DisplayName("경험치가 넘치면 레벨이 오르고, 남은 경험치는 이어진다")
    void levelUpCarriesOver() {
        Tamagotchi p = newBaby(80, 80, 0);
        assertThat(p.level()).isEqualTo(1);
        assertThat(p.expToNext()).isEqualTo(30);

        p.addExp(75); // Lv.1→2에 30, Lv.2→3에 40, 5 남음

        assertThat(p.level()).isEqualTo(3);
        assertThat(p.exp()).isEqualTo(5);
    }

    @Test
    @DisplayName("부화하면 Lv.1 낙서이고, 낙서는 Lv.1 + zoneSpan(BABY)부터 진화 가능 구간이다")
    void babyZone() {
        Tamagotchi hatched = pet(EvolutionStage.EGG, 80, 80, 0);
        hatched.reset();
        hatched.hatch();
        assertThat(hatched.level()).isEqualTo(1);
        assertThat(hatched.evolutionLevel()).isEqualTo(BABY_ZONE);

        Tamagotchi p = newBaby(80, 80, 0);
        levelTo(p, BABY_ZONE - 1);
        assertThat(p.isInEvolutionZone()).isFalse();
        levelTo(p, BABY_ZONE);
        assertThat(p.isInEvolutionZone()).isTrue();
        assertThat(p.isEvolutionReady()).isTrue();
    }

    @Test
    @DisplayName("구간 전에는 진화할 수 없고, 구간에 들어서도 바로 진화하지 않는다 (사용자가 고른다)")
    void evolutionIsChosen() {
        Tamagotchi p = newBaby(80, 80, 0);
        levelTo(p, BABY_ZONE - 1);
        assertThatThrownBy(p::evolveIfReady).isInstanceOf(IllegalStateException.class).hasMessageContaining("Lv." + BABY_ZONE);

        levelTo(p, BABY_ZONE + 2); // 구간에서 더 키워도 그대로 낙서다
        assertThat(p.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);

        p.evolveIfReady();
        assertThat(p.getEvolutionStage()).isEqualTo(EvolutionStage.CHILD);
        assertThat(p.level()).isEqualTo(BABY_ZONE + 2); // 레벨은 진화해도 이어진다
    }

    @Test
    @DisplayName("스케치의 진화 가능 구간은 진화한 레벨 + zoneSpan(CHILD)부터다 (낙서에서 오래 키워도 바로 완성작이 되지 않는다)")
    void childZoneCountsFromEvolveLevel() {
        Tamagotchi p = newBaby(80, 80, 0);
        int evolvedAt = BABY_ZONE + 3;
        levelTo(p, evolvedAt);
        p.evolveIfReady();

        assertThat(p.evolutionLevel()).isEqualTo(evolvedAt + CHILD_SPAN);
        assertThat(p.isInEvolutionZone()).isFalse();
        levelTo(p, evolvedAt + CHILD_SPAN);
        p.evolveIfReady();
        assertThat(p.getEvolutionStage()).isEqualTo(EvolutionStage.ADULT);
    }

    @Test
    @DisplayName("포만감·파워·응가 조건을 하나라도 못 맞추면 구간이어도 진화하지 않는다")
    void conditionsMustAllPass() {
        for (Tamagotchi p : new Tamagotchi[]{
                newBaby(49, 80, 0),   // 포만감 부족
                newBaby(80, 49, 0),   // 파워 부족
                newBaby(80, 80, 1)}) { // 응가 있음
            levelTo(p, BABY_ZONE);
            assertThat(p.isInEvolutionZone()).isTrue();
            assertThat(p.isEvolutionReady()).isFalse();
            assertThatThrownBy(p::evolveIfReady).isInstanceOf(IllegalStateException.class).hasMessageContaining("컨디션");
        }
        Tamagotchi border = newBaby(50, 50, 0); // 경계값은 통과
        levelTo(border, BABY_ZONE);
        assertThat(border.isEvolutionReady()).isTrue();
    }

    @Test
    @DisplayName("완성작과 사망한 캐릭터는 진화할 수 없다")
    void adultAndDeadCannotEvolve() {
        Tamagotchi adult = pet(EvolutionStage.ADULT, 80, 80, 0);
        levelTo(adult, 30);
        assertThat(adult.evolutionLevel()).isZero();
        assertThat(adult.isEvolutionReady()).isFalse();

        Tamagotchi dead = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(20).strength(80)
                .deadAt(java.time.LocalDateTime.now()).build();
        levelTo(dead, 5);
        assertThat(dead.isEvolutionReady()).isFalse();
    }

    @Test
    @DisplayName("부화하면 알이 낙서가 되고, 알이 아니면 부화할 수 없다")
    void hatch() {
        Tamagotchi egg = pet(EvolutionStage.EGG, 80, 80, 0);
        egg.hatch();
        assertThat(egg.getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
        assertThatThrownBy(egg::hatch).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("트레이닝하면 기본 경험치를 바로 받고, 광고로 같은 만큼 더 받을 수 있게 대기한다. 위력 +1·무게 −1")
    void trainingGivesExpAndOffersDouble() {
        Tamagotchi p = newBaby(80, 80, 0);

        Tamagotchi.TrainingGain gain = p.train(40, false);

        assertThat(gain.exp()).isEqualTo(15);
        assertThat(gain.winBonus()).isZero();
        assertThat(p.exp()).isEqualTo(15);
        assertThat(p.pendingExp()).isEqualTo(15);
        assertThat(p.might()).isEqualTo(LevelRules.MIGHT_PER_TRAINING);
        assertThat(p.losses()).isEqualTo(1);
        assertThat(p.weightDg()).isEqualTo(LevelRules.START_WEIGHT_DG - LevelRules.TRAINING_WEIGHT_DG);
    }

    @Test
    @DisplayName("광고로 받으면 기본 경험치만큼 더 오르고(2배) 대기는 비워진다. 승리 보너스는 2배에 들어가지 않는다")
    void claimDoublesBaseOnly() {
        Tamagotchi p = newBaby(80, 80, 0);
        Tamagotchi.TrainingGain gain = p.train(10, true); // 기본 5 + 승리 보너스 3

        assertThat(gain.exp()).isEqualTo(8);
        assertThat(gain.winBonus()).isEqualTo(3);
        assertThat(p.wins()).isEqualTo(1);
        assertThat(p.claimExp()).isZero(); // 레벨은 오르지 않았다
        assertThat(p.exp()).isEqualTo(13);
        assertThat(p.pendingExp()).isZero();
        p.claimExp(); // 한 번 받으면 다시 받을 것이 없다
        assertThat(p.exp()).isEqualTo(13);
    }

    @Test
    @DisplayName("위력은 레벨이 오를 때 크게(+5), 트레이닝으로는 조금(+1) 오른다")
    void mightGrowsMostlyWithLevel() {
        Tamagotchi p = newBaby(80, 80, 0);
        levelTo(p, 4);
        assertThat(p.might()).isEqualTo(3 * LevelRules.MIGHT_PER_LEVEL);
        p.train(0, false);
        assertThat(p.might()).isEqualTo(3 * LevelRules.MIGHT_PER_LEVEL + LevelRules.MIGHT_PER_TRAINING);
    }

    @Test
    @DisplayName("스파링 전투력은 위력이 가장 크게 좌우한다: 위력이 두 배면 탭이 적어도 이긴다")
    void mightDominatesBattle() {
        // 위력 100·탭 15 vs 위력 40·탭 40 (컨디션·운 같음)
        assertThat(BattleRules.power(100, 15, 70, 1000)).isGreaterThan(BattleRules.power(40, 40, 70, 1000));
        // 위력이 같으면 탭이 많은 쪽이 이긴다
        assertThat(BattleRules.power(60, 35, 70, 1000)).isGreaterThan(BattleRules.power(60, 20, 70, 1000));
        // 운은 비슷한 판을 뒤집을 수 있다
        assertThat(BattleRules.power(60, 30, 70, 1100)).isGreaterThan(BattleRules.power(60, 34, 70, 950));
    }

    @Test
    @DisplayName("가상 상대는 내 위력의 ±15%, 레벨 ±1, 탭 15~40이다")
    void enemyMatchesMe() {
        for (int i = 0; i < 200; i++) {
            BattleRules.Enemy e = BattleRules.randomEnemy(10, 100);
            assertThat(e.might()).isBetween(85, 115);
            assertThat(e.level()).isBetween(9, 11);
            assertThat(e.taps()).isBetween(15, 40);
            assertThat(e.luckPermille()).isBetween(900, 1100);
            assertThat(BattleRules.ENEMY_KEYS).contains(e.key());
        }
    }

    @Test
    @DisplayName("새 훈련을 하면 받지 않은 이전 대기분은 사라진다 (훈련 1번 = 광고 1번)")
    void newTrainingReplacesPending() {
        Tamagotchi p = newBaby(80, 80, 0);
        p.train(90, false);
        p.train(10, false);
        assertThat(p.pendingExp()).isEqualTo(5);
    }

    @Test
    @DisplayName("진화 가능 구간에서는 훈련 경험치가 절반이다")
    void zoneHalvesTrainingExp() {
        Tamagotchi p = newBaby(80, 80, 0);
        levelTo(p, BABY_ZONE);
        p.train(40, false);
        assertThat(p.pendingExp()).isEqualTo(8);
    }

    @Test
    @DisplayName("진화 가능 구간의 레벨 하나에 드는 경험치는 레벨과 상관없이 같다 (후보 이미지 간격이 일정하다)")
    void zoneExpIsFixed() {
        Tamagotchi p = newBaby(80, 80, 0);
        levelTo(p, BABY_ZONE - 1);
        assertThat(p.expToNext()).isEqualTo(LevelRules.expToNext(BABY_ZONE - 1));
        levelTo(p, BABY_ZONE);
        assertThat(p.expToNext()).isEqualTo(LevelRules.ZONE_EXP_PER_LEVEL);
        levelTo(p, BABY_ZONE + 5);
        assertThat(p.expToNext()).isEqualTo(LevelRules.ZONE_EXP_PER_LEVEL);

        p.evolveIfReady(); // 스케치가 되면 다시 오르는 곡선, 구간에 들어서면 다시 고정
        assertThat(p.expToNext()).isEqualTo(LevelRules.expToNext(BABY_ZONE + 5));
        levelTo(p, BABY_ZONE + 5 + CHILD_SPAN);
        assertThat(p.expToNext()).isEqualTo(LevelRules.ZONE_EXP_PER_LEVEL);
    }

    @Test
    @DisplayName("배부를 때(포만감 80 이상) 또 먹이면 무게가 늘고, 배고플 때 먹이는 건 무게가 늘지 않는다. 훈련해도 최소 무게 아래로는 빠지지 않는다")
    void weight() {
        Tamagotchi hungry = newBaby(50, 80, 0);
        hungry.feed();
        assertThat(hungry.weightDg()).isEqualTo(LevelRules.START_WEIGHT_DG);

        Tamagotchi p = newBaby(80, 80, 0);
        p.feed();
        assertThat(p.weightDg()).isEqualTo(LevelRules.START_WEIGHT_DG + LevelRules.FEED_WEIGHT_DG);
        for (int i = 0; i < 200; i++) p.train(0, false);
        assertThat(p.weightDg()).isEqualTo(LevelRules.MIN_WEIGHT_DG);
    }

    @Test
    @DisplayName("새로 시작하면 레벨·경험치·대기·무게·위력이 처음으로 돌아간다")
    void resetClearsLevel() {
        Tamagotchi p = newBaby(80, 80, 0);
        levelTo(p, 5);
        p.train(40, true);
        p.reset();
        assertThat(p.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        assertThat(p.level()).isEqualTo(1);
        assertThat(p.exp()).isZero();
        assertThat(p.pendingExp()).isZero();
        assertThat(p.weightDg()).isEqualTo(LevelRules.START_WEIGHT_DG);
        assertThat(p.might()).isZero();
        assertThat(p.wins()).isZero();
        assertThat(p.losses()).isZero();
    }

    @Test
    @DisplayName("레벨이 생기기 전 행은 예전 진화 게이지 진행도로 레벨을 정한다")
    void migratesFromGauge() {
        Tamagotchi baby = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(20).strength(80)
                .evoGauge(7).pendingEvoGain(2).build();
        assertThat(baby.level()).isEqualTo(1 + LevelRules.zoneSpan(EvolutionStage.BABY) * 7 / 10);
        assertThat(baby.pendingExp()).isEqualTo(2 * LevelRules.EXP_PER_STAR); // 대기 게이지 2칸 = 별 2개만큼
        assertThat(baby.might()).isEqualTo(LevelRules.mightForLevel(baby.level()));

        Tamagotchi fullBaby = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(20).strength(80)
                .evoGauge(10).build();
        assertThat(fullBaby.isInEvolutionZone()).isTrue(); // 게이지가 가득이던 낙서는 바로 고를 수 있다

        Tamagotchi child = Tamagotchi.builder().evolutionStage(EvolutionStage.CHILD).hunger(20).strength(80)
                .evoGauge(30).build();
        assertThat(child.level()).isEqualTo(BABY_ZONE + CHILD_SPAN * 30 / 60);
        assertThat(child.evolutionLevel()).isEqualTo(BABY_ZONE + CHILD_SPAN);

        Tamagotchi adult = Tamagotchi.builder().evolutionStage(EvolutionStage.ADULT).hunger(20).strength(80).build();
        assertThat(adult.level()).isEqualTo(BABY_ZONE + CHILD_SPAN);
    }

    @Test
    @DisplayName("업데이트 전 앱용 게이지는 진화 가능 구간까지의 진행도다")
    void legacyGauge() {
        Tamagotchi p = newBaby(80, 80, 0);
        assertThat(p.legacyEvoGauge()).isZero();
        levelTo(p, 2);
        assertThat(p.legacyEvoGauge()).isEqualTo(10 / LevelRules.zoneSpan(EvolutionStage.BABY));
        levelTo(p, BABY_ZONE);
        assertThat(p.legacyEvoGauge()).isEqualTo(10);
        p.train(40, false);
        assertThat(p.legacyPendingEvoGain()).isEqualTo(1); // 8 → 1칸
    }
}
