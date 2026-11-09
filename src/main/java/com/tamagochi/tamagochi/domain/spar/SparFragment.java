package com.tamagochi.tamagochi.domain.spar;

import com.tamagochi.tamagochi.domain.land.LandCatalog;

import java.util.List;

/**
 * 보관함에 보여줄 모으는 중인 잉크 조각과 출처.
 * regionIds: 이 잉크를 떨어뜨리는 상대들의 진영, enemyCount: 그런 상대 수, enemies: 그중 나오는 레벨이 낮은 몇 명
 * homeRegionId: 도감에서 이 잉크가 들어가는 구역 (계열 잉크는 null). 도감 빈 칸 자리에 모으는 중으로 보여준다
 */
public record SparFragment(String inkId, String emoji, String name, LandCatalog.Grade grade, String familyName,
                           int count, int need, List<String> regionIds, int enemyCount, List<Source> enemies,
                           String homeRegionId) {

    /** 잉크를 떨어뜨리는 상대 한 명 */
    public record Source(String name, String faction, int minLevel, int maxLevel, boolean boss) {
    }

    public static final int MAX_SOURCES = 3;

    public static SparFragment of(LandCatalog.Ink ink, int count) {
        List<SparRoster.Enemy> droppers = SparRoster.droppersOf(ink.id());
        String familyName = LandCatalog.family(ink.family()).map(LandCatalog.Family::name).orElse(null);
        return new SparFragment(ink.id(), ink.emoji(), ink.name(), ink.grade(), familyName, count, SparDropRules.need(ink.grade()),
                SparRoster.factionsOf(ink.id()), droppers.size(),
                droppers.stream().limit(MAX_SOURCES)
                        .map(e -> new Source(e.name(), e.faction(), e.minLevel(), e.maxLevel(), e.boss())).toList(),
                LandCatalog.homeRegion(ink.id()).orElse(null));
    }
}
