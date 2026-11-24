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
        // 구역마다 여덟 곳, 상대 셋을 차례로 돌려 넣는다
        assertThat(spawns.subList(0, 8)).extracting(PlazaMobs.Spawn::enemy)
                .containsExactly("ns-rabbit", "ns-ladybug", "ns-snail", "ns-rabbit", "ns-ladybug", "ns-snail", "ns-rabbit", "ns-ladybug");
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
