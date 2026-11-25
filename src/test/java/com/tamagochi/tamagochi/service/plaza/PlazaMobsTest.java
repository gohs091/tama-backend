package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.domain.spar.SparRoster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlazaMobsTest {

    @Test
    void 자리는_스물네_곳이고_상대는_명단에_있다() {
        List<PlazaMobs.Spawn> spawns = PlazaMobs.load();
        assertThat(spawns).hasSize(24);
        for (PlazaMobs.Spawn s : spawns) {
            assertThat(SparRoster.find(s.enemy())).as(s.enemy()).isPresent();
            assertThat(s.x()).isBetween(0, 1000);
            assertThat(s.y()).isBetween(0, 1000);
        }
    }

    @Test
    void 구역마다_여덟_곳이고_구역의_상대만_나온다() {
        List<PlazaMobs.Spawn> spawns = PlazaMobs.load();
        List<String> west = List.of("ns-rabbit", "ns-ladybug", "ns-snail");
        List<String> middle = List.of("ns-hedgehog", "ns-raccoon", "ns-mushroom");
        List<String> east = List.of("ns-wisp", "ns-boar", "ns-tree-spirit");
        List<PlazaMobs.Spawn> w = spawns.stream().filter(s -> s.x() <= 333).toList();
        List<PlazaMobs.Spawn> m = spawns.stream().filter(s -> s.x() >= 334 && s.x() <= 666).toList();
        List<PlazaMobs.Spawn> e = spawns.stream().filter(s -> s.x() >= 667).toList();
        assertThat(w).hasSize(8);
        assertThat(m).hasSize(8);
        assertThat(e).hasSize(8);
        assertThat(w).extracting(PlazaMobs.Spawn::enemy).isSubsetOf(west);
        assertThat(m).extracting(PlazaMobs.Spawn::enemy).isSubsetOf(middle);
        assertThat(e).extracting(PlazaMobs.Spawn::enemy).isSubsetOf(east);
    }

    @Test
    void 어떤_몬스터_자리도_시작_자리에서_세상_단위_둘_안이_아니다() {
        // 시작 자리 천분율 x 167..257, y 132..217 사각형까지의 거리를 잰다
        for (PlazaMobs.Spawn s : PlazaMobs.load()) {
            double dx = Math.max(0, Math.max(167 - s.x(), s.x() - 257)) * 72 / 1000.0;
            double dz = Math.max(0, Math.max(132 - s.y(), s.y() - 217)) * 30.4 / 1000.0;
            assertThat(Math.hypot(dx, dz)).as("%d,%d %s", s.x(), s.y(), s.enemy()).isGreaterThanOrEqualTo(2.0);
        }
    }

    @Test
    void 체력은_약함3_보통5_셈7() {
        assertThat(PlazaMobs.kind("ns-rabbit").hp()).isEqualTo(3);
        assertThat(PlazaMobs.kind("ns-hedgehog").hp()).isEqualTo(5);
        assertThat(PlazaMobs.kind("ns-boar").hp()).isEqualTo(7);
        assertThat(PlazaMobs.kind("ns-rabbit").minLevel()).isEqualTo(1);
        assertThat(PlazaMobs.kind("ns-rabbit").maxLevel()).isEqualTo(5);
    }

    @Test
    void 피해는_레벨이_낮으면1_범위면2_높으면3_열_넘게_높으면_한_방() {
        PlazaMobs.Kind k = new PlazaMobs.Kind(5, 6, 14);
        assertThat(PlazaMobs.damage(5, k)).isEqualTo(1);
        assertThat(PlazaMobs.damage(6, k)).isEqualTo(2);
        assertThat(PlazaMobs.damage(14, k)).isEqualTo(2);
        assertThat(PlazaMobs.damage(15, k)).isEqualTo(3);
        assertThat(PlazaMobs.damage(24, k)).isEqualTo(5);
    }

    @Test
    void 다섯_안이면_가깝다() {
        PlazaMobs.Spawn s = new PlazaMobs.Spawn(500, 500, "ns-rabbit");
        assertThat(PlazaMobs.near(569, 500, s)).isTrue();    // 4.97
        assertThat(PlazaMobs.near(570, 500, s)).isFalse();   // 5.04
        assertThat(PlazaMobs.near(500, 500, s)).isTrue();
    }
}
