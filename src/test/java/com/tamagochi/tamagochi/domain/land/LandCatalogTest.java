package com.tamagochi.tamagochi.domain.land;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LandCatalogTest {

    @Test
    void hasAllSeventeenRegionsWithFourOpen() {
        assertThat(LandCatalog.regions()).hasSize(17);
        assertThat(LandCatalog.regions().stream().filter(LandCatalog.Region::open).map(LandCatalog.Region::id))
                .containsExactly("nature-spirits", "deep-savers", "jungle-troopers", "metal-empire");
    }

    @Test
    void everyRegionHasHomeInksAndEachSpecificInkHasOneHome() {
        for (LandCatalog.Region r : LandCatalog.regions()) {
            assertThat(LandCatalog.homeInks(r.id())).as(r.id()).isNotEmpty()
                    .allMatch(i -> i.grade() != LandCatalog.Grade.FAMILY)
                    .allMatch(i -> LandCatalog.homeRegion(i.id()).orElseThrow().equals(r.id()));
        }
        assertThat(LandCatalog.homeRegion("tiger")).contains("nature-spirits");
        assertThat(LandCatalog.homeRegion("family.beast")).isEmpty();
        assertThat(LandCatalog.familyCount()).isPositive();
    }

    @Test
    void openRegionsFirstFindAtOneMinuteAndStrictlyIncreaseToEighteenHours() {
        for (LandCatalog.Region r : LandCatalog.regions()) {
            if (!r.open()) continue;
            List<Integer> at = r.discoverAt();
            assertThat(at).hasSameSizeAs(r.inks());
            assertThat(at.get(0)).isEqualTo(1);
            for (int i = 1; i < at.size(); i++) assertThat(at.get(i)).isGreaterThan(at.get(i - 1));
            assertThat(at.get(12)).isEqualTo(LandCatalog.TIER1_END);
            assertThat(r.totalMinutes()).isEqualTo(LandCatalog.TIER3_END);
        }
    }

    @Test
    void legendsComeLastAndFamiliesFirst() {
        for (LandCatalog.Region r : LandCatalog.regions()) {
            if (!r.open()) continue;
            List<LandCatalog.Ink> inks = r.inks();
            assertThat(inks.subList(0, 4)).allMatch(i -> i.grade() == LandCatalog.Grade.FAMILY);
            assertThat(inks.subList(inks.size() - 3, inks.size())).allMatch(i -> i.grade() == LandCatalog.Grade.LEGEND);
        }
    }

    @Test
    void everyInkHasNameEmojiPromptAndKnownFamily() {
        for (LandCatalog.Region r : LandCatalog.regions()) {
            for (LandCatalog.Ink i : r.inks()) {
                assertThat(i.name()).endsWith("의 잉크");
                assertThat(i.emoji()).isNotBlank();
                assertThat(i.prompt()).isNotBlank();
                assertThat(LandCatalog.family(i.family())).isPresent();
            }
        }
    }
}
