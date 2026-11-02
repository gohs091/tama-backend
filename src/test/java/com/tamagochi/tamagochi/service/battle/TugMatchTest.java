package com.tamagochi.tamagochi.service.battle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TugMatchTest {

    @Test
    void 사람_손으로_낼_수_없는_연타는_버린다() {
        TugMatch m = new TugMatch(0);
        m.addTaps(true, 50, 1000); // 1초에 50번
        assertThat(m.hostTaps()).isEqualTo(MeetBattleRules.MAX_TAPS_PER_SECOND + MeetBattleRules.TAP_BURST);
    }

    @Test
    void 크게_앞서면_바로_끝난다() {
        TugMatch m = new TugMatch(0);
        m.addTaps(true, 40, 3000);
        assertThat(m.decided(3000)).hasValueSatisfying(o -> {
            assertThat(o.winner()).isEqualTo(1);
            assertThat(o.ko()).isTrue();
        });
    }

    @Test
    void 시간이_다_되면_많이_누른_쪽이_이기고_같으면_무승부() {
        TugMatch m = new TugMatch(0);
        m.addTaps(true, 10, 2000);
        m.addTaps(false, 12, 2000);
        assertThat(m.decided(4000)).isEmpty();
        assertThat(m.decided(MeetBattleRules.FIGHT_MS)).hasValueSatisfying(o -> assertThat(o.winner()).isEqualTo(-1));

        TugMatch d = new TugMatch(0);
        assertThat(d.decided(MeetBattleRules.FIGHT_MS)).hasValueSatisfying(o -> assertThat(o.winner()).isZero());
    }
}
