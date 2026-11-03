package com.tamagochi.tamagochi.domain.spar;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SparRosterTest {

    @Test
    @DisplayName("상대 412명이 모두 읽히고, 떨어뜨리는 잉크는 1~5칸이며 모두 잉크 목록에 있다")
    void rosterLoads() {
        List<SparRoster.Enemy> all = SparRoster.all();
        assertThat(all).hasSize(412);
        for (SparRoster.Enemy e : all) {
            assertThat(e.drops()).as(e.id()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
            assertThat(e.minLevel()).as(e.id()).isLessThanOrEqualTo(e.maxLevel());
            assertThat(e.id().length()).as("enemy_key 칸 길이 20").isLessThanOrEqualTo(20);
            for (String ink : e.drops()) assertThat(LandCatalog.ink(ink)).as(e.id() + " → " + ink).isPresent();
        }
        assertThat(all.stream().filter(SparRoster.Enemy::boss)).hasSize(71);
    }

    @Test
    @DisplayName("스파링 잉크 303종과 새 계열 12개가 잉크 목록에 들어가고, 지역 탐험에는 나오지 않는다")
    void sparInksRegistered() {
        assertThat(LandCatalog.ink("bonghwang")).get().extracting(LandCatalog.Ink::grade).isEqualTo(LandCatalog.Grade.LEGEND);
        assertThat(LandCatalog.ink("family.wind")).get().extracting(LandCatalog.Ink::grade).isEqualTo(LandCatalog.Grade.FAMILY);
        assertThat(LandCatalog.family("ghost")).isPresent();
        assertThat(LandCatalog.regions().stream().flatMap(r -> r.inks().stream()).map(LandCatalog.Ink::id))
                .doesNotContain("bonghwang", "family.wind");
        // 새 잉크도 진화 문장으로 바뀐다
        assertThat(LandCatalog.absorbedPrompt("bonghwang", List.of("family.wind"), "adult")).contains("bonghwang");
    }

    @Test
    @DisplayName("상대는 내 레벨 범위 안에서 고르고, 범위 밖 레벨이면 가장 가까운 상대를 고른다")
    void pickByLevel() {
        SplittableRandom r = new SplittableRandom(1);
        for (int level : new int[]{1, 10, 30, 50}) {
            for (int i = 0; i < 100; i++) {
                SparRoster.Enemy e = SparRoster.pick(level, r);
                assertThat(level).as(e.id()).isBetween(e.minLevel(), e.maxLevel());
            }
        }
        int top = SparRoster.all().stream().mapToInt(SparRoster.Enemy::maxLevel).max().orElseThrow();
        assertThat(SparRoster.pick(top + 40, r).maxLevel()).isEqualTo(top);
    }

    @Test
    @DisplayName("가중치 고르기: 굴린 값에 따라 가중치 구간의 잉크가 나온다")
    void pickWeighted() {
        List<String> items = List.of("c", "r", "l"); // 4 / 1.5 / 0.5 → 합 6
        java.util.Map<String, Double> w = java.util.Map.of("c", 4.0, "r", 1.5, "l", 0.5);
        assertThat(SparDropRules.pickWeighted(items, w::get, 0.0)).isEqualTo("c");
        assertThat(SparDropRules.pickWeighted(items, w::get, 0.66)).isEqualTo("c");
        assertThat(SparDropRules.pickWeighted(items, w::get, 0.70)).isEqualTo("r");
        assertThat(SparDropRules.pickWeighted(items, w::get, 0.95)).isEqualTo("l");
        assertThat(SparDropRules.pickWeighted(List.<String>of(), w::get, 0.5)).isNull();
    }
}
