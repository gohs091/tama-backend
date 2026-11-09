package com.tamagochi.tamagochi.domain.farm;

import com.tamagochi.tamagochi.domain.farm.FarmCatalog.Quest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarmTest {

    private final Instant t0 = Instant.parse("2026-11-10T01:00:00Z");
    // 곁다리 퀘스트가 있는 날과 없는 날
    private final LocalDate sideDay = LocalDate.ofEpochDay(20_400);
    private final LocalDate plainDay = LocalDate.ofEpochDay(20_401);

    @Test
    void startsWithFourOpenPlotsAndUnlocksInPriceOrder() {
        Farm farm = new Farm(null, t0);
        assertThat(FarmCatalog.INITIAL_OPEN).allMatch(farm::isOpen);
        assertThat(farm.isOpen(8)).isFalse();
        assertThat(farm.unlockPrice()).isEqualTo(15);
        farm.unlock(8);
        assertThat(farm.unlockPrice()).isEqualTo(30);
        assertThatThrownBy(() -> farm.unlock(8)).hasMessageContaining("이미 열린");
        assertThatThrownBy(() -> farm.plant(2, "strawberry", t0)).hasMessageContaining("열지 않은");
    }

    @Test
    void growsByTimeAndReplantsTheSameCropOnHarvest() {
        Farm farm = new Farm(null, t0);
        farm.plant(0, "blueberry", t0);
        assertThat(farm.nextSeq(0)).isEqualTo(2);
        Instant t1 = t0.plus(Duration.ofHours(3));
        farm.settle(t1);
        assertThat(farm.ripe(0)).isFalse();
        assertThat(farm.remainingSeconds(0, t1)).isEqualTo(Duration.ofHours(1).toSeconds());
        assertThatThrownBy(() -> farm.harvest(0, true, t1)).hasMessageContaining("자라는 중");

        Instant t2 = t0.plus(Duration.ofHours(4));
        assertThat(farm.harvest(0, true, t2)).isEqualTo(3);
        assertThat(farm.plot(0).getCrop()).isEqualTo("blueberry");
        assertThat(farm.progress(0)).isZero();
        assertThat(farm.nextSeq(0)).isEqualTo(3);

        farm.settle(t2.plus(Duration.ofHours(4)));
        farm.harvest(0, false, t2.plus(Duration.ofHours(4)));
        assertThat(farm.plot(0).getCrop()).isNull();
    }

    @Test
    void waterDoublesGrowthOnlyWhileItLasts() {
        Farm farm = new Farm(null, t0);
        farm.plant(0, "blueberry", t0);
        farm.addWater(Duration.ofHours(1), t0);
        assertThat(farm.waterSeconds(t0)).isEqualTo(3600);
        // 물 1시간 동안 2시간어치, 그 뒤 1시간은 그대로 → 3시간
        Instant t1 = t0.plus(Duration.ofHours(2));
        farm.settle(t1);
        assertThat(farm.plot(0).getGrown()).isEqualTo(Duration.ofHours(3).toSeconds());
        assertThat(farm.waterSeconds(t1)).isZero();
        // 남은 1시간: 물 30분이면 30분 만에
        farm.addWater(Duration.ofMinutes(30), t1);
        assertThat(farm.remainingSeconds(0, t1)).isEqualTo(Duration.ofMinutes(30).toSeconds());
        // 물은 8시간까지만
        farm.addWater(Duration.ofHours(20), t1);
        assertThat(farm.waterSeconds(t1)).isEqualTo(FarmCatalog.WATER_MAX.toSeconds());
    }

    @Test
    void dailyQuestsGiveWaterAndBonusRipensEverything() {
        Farm farm = new Farm(null, t0);
        farm.plant(0, "pumpkin", t0);
        farm.progress(Quest.MEAT, 2, plainDay);
        assertThatThrownBy(() -> farm.claim(Quest.MEAT, plainDay, t0)).hasMessageContaining("아직");
        farm.progress(Quest.MEAT, 5, plainDay);
        assertThat(farm.questCount(Quest.MEAT, plainDay)).isEqualTo(3);
        farm.claim(Quest.MEAT, plainDay, t0);
        assertThat(farm.waterSeconds(t0)).isEqualTo(Duration.ofMinutes(20).toSeconds());
        assertThatThrownBy(() -> farm.claim(Quest.MEAT, plainDay, t0)).hasMessageContaining("이미");

        for (Quest q : new Quest[]{Quest.PILL, Quest.TRAIN}) { farm.progress(q, 3, plainDay); farm.claim(q, plainDay, t0); }
        assertThat(farm.bonusReady(plainDay)).isTrue();
        farm.claimBonus(plainDay, t0);
        assertThat(farm.ripe(0)).isTrue();
        assertThat(farm.bonusReady(plainDay)).isFalse();

        // 다음 날이면 처음부터
        assertThat(farm.questCount(Quest.MEAT, plainDay.plusDays(1))).isZero();
        assertThat(farm.questClaimed(Quest.MEAT, plainDay.plusDays(1))).isFalse();
    }

    @Test
    void sideQuestOnlyOnItsDays() {
        assertThat(FarmCatalog.sideQuest(sideDay)).contains(Quest.BATTLE);
        assertThat(FarmCatalog.sideQuest(plainDay)).isEmpty();
        Farm farm = new Farm(null, t0);
        farm.progress(Quest.BATTLE, 1, plainDay);
        assertThat(farm.questCount(Quest.BATTLE, plainDay)).isZero();
        farm.progress(Quest.BATTLE, 1, sideDay);
        assertThat(farm.claim(Quest.BATTLE, sideDay, t0)).isEqualTo(5);
    }

    @Test
    void adWaterIsLimitedPerDay() {
        Farm farm = new Farm(null, t0);
        for (int i = 0; i < FarmCatalog.AD_PER_DAY; i++) farm.adWater(plainDay, t0);
        assertThat(farm.adsLeft(plainDay)).isZero();
        assertThatThrownBy(() -> farm.adWater(plainDay, t0)).hasMessageContaining("오늘은");
        assertThat(farm.adsLeft(plainDay.plusDays(1))).isEqualTo(FarmCatalog.AD_PER_DAY);
    }
}
