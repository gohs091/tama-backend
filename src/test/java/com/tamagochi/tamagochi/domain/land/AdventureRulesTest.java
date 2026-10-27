package com.tamagochi.tamagochi.domain.land;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdventureRulesTest {

    @Test
    void firstAdventureOfTheDayPicksTwoInkEveryTwentySecondsForThreeMinutes() {
        assertThat(AdventureRules.pickedInk(19, true)).isZero();
        assertThat(AdventureRules.pickedInk(20, true)).isEqualTo(2);
        assertThat(AdventureRules.pickedInk(60, true)).isEqualTo(6);
        assertThat(AdventureRules.pickedInk(180, true)).isEqualTo(18);
        assertThat(AdventureRules.pickedInk(240, true)).isEqualTo(19); // 4분째부터 1분에 +1
    }

    @Test
    void laterAdventuresPickOneInkPerMinuteThenEveryTwoMinutesAfterThirty() {
        assertThat(AdventureRules.pickedInk(60, false)).isEqualTo(1);
        assertThat(AdventureRules.pickedInk(30 * 60, false)).isEqualTo(30);
        assertThat(AdventureRules.pickedInk(31 * 60, false)).isEqualTo(30);
        assertThat(AdventureRules.pickedInk(32 * 60, false)).isEqualTo(31);
        assertThat(AdventureRules.pickedInk(40 * 60, false)).isEqualTo(35);
    }

    @Test
    void costGrowsWithExplorationDepth() {
        assertThat(AdventureRules.spentInk(10, 0)).isEqualTo(20);
        assertThat(AdventureRules.spentInk(2, 239)).isEqualTo(2 + 3);
    }

    @Test
    void endsWhenNextMinuteWouldDropBelowReserve() {
        // 잔액 30, 보너스 없음: 1분마다 -2 +1 → 순 -1. 10분 뒤 20, 11분째에 19가 되므로 10분에서 멈춘다
        AdventureRules.Progress p = AdventureRules.progress(60 * 60, 0, 1080, false, 30);
        assertThat(p.ended()).isEqualTo(AdventureRules.EndReason.LOW_INK);
        assertThat(p.minutes()).isEqualTo(10);
    }

    @Test
    void endsAfterFourHours() {
        AdventureRules.Progress p = AdventureRules.progress(10 * 60 * 60, 0, 1080, false, 10_000);
        assertThat(p.ended()).isEqualTo(AdventureRules.EndReason.TIME_UP);
        assertThat(p.minutes()).isEqualTo(240);
    }

    @Test
    void endsWhenRegionIsFullyExplored() {
        AdventureRules.Progress p = AdventureRules.progress(60 * 60, 1070, 1080, false, 10_000);
        assertThat(p.ended()).isEqualTo(AdventureRules.EndReason.COMPLETE);
        assertThat(p.minutes()).isEqualTo(10);
    }

    @Test
    void keepsGoingWhileNothingEndsIt() {
        AdventureRules.Progress p = AdventureRules.progress(90 * 60 + 30, 0, 1080, true, 500);
        assertThat(p.ended()).isNull();
        assertThat(p.minutes()).isEqualTo(90);
    }

    @Test
    void needsReservePlusFirstMinuteToStart() {
        assertThat(AdventureRules.canAfford(22, 0)).isTrue();
        assertThat(AdventureRules.canAfford(21, 0)).isFalse();
    }
}
