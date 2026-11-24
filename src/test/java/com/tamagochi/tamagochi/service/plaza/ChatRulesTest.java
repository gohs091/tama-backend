package com.tamagochi.tamagochi.service.plaza;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRulesTest {

    @Test
    void 서른_자로_자른다() {
        String out = ChatRules.clean("가".repeat(45));
        assertThat(out).isEqualTo("가".repeat(30));
    }

    @Test
    void 꺾쇠와_제어_문자를_지운다() {
        assertThat(ChatRules.clean("  안<b>녕\u0007 ​반가워\t\n  ")).isEqualTo("안b녕 반가워");
    }

    @Test
    void 전화번호_같은_숫자는_가린다() {
        assertThat(ChatRules.clean("01012345678 연락줘")).isEqualTo("*********** 연락줘");
        assertThat(ChatRules.clean("123456 은 그대로")).isEqualTo("123456 은 그대로");
    }

    @Test
    void 빈_말은_null() {
        assertThat(ChatRules.clean(null)).isNull();
        assertThat(ChatRules.clean("   ")).isNull();
        assertThat(ChatRules.clean("<>​")).isNull();
    }

    @Test
    void 금칙어를_찾는다() {
        assertThat(ChatRules.banned("너 미친놈이야")).isTrue();
    }

    @Test
    void 띄어_쓰고_기호를_섞어도_찾는다() {
        assertThat(ChatRules.banned("미 친.놈")).isTrue();
        assertThat(ChatRules.banned("미1친~놈")).isTrue();
    }

    @Test
    void 두_글자_욕은_단독으로도_막힌다() {
        assertThat(ChatRules.banned("병신")).isTrue();
        assertThat(ChatRules.banned("너 진짜 병신 같다")).isTrue();
    }

    @Test
    void 한_글자씩_띄우거나_숫자와_기호를_끼워도_두_글자_욕은_막힌다() {
        assertThat(ChatRules.banned("병 신")).isTrue();
        assertThat(ChatRules.banned("병 1 신")).isTrue();
        assertThat(ChatRules.banned("병.신")).isTrue();
        assertThat(ChatRules.banned("병1신")).isTrue();
    }

    @Test
    void 띄어_쓴_평범한_문장은_짧은_낱말에_안_걸린다() {
        assertThat(ChatRules.banned("다시 발표 하자")).isFalse();   // 다 붙이면 "시발" 이 되는 문장
        assertThat(ChatRules.banned("병원 신기해")).isFalse();      // 다 붙이면 "병신" 이 되는 문장
        assertThat(ChatRules.banned("a sex b")).isTrue();           // 짧은 영문 낱말은 낱말로 있으면 막힌다
    }

    @Test
    void 긴_낱말은_띄어_써도_막힌다() {
        assertThat(ChatRules.banned("미 친 놈")).isTrue();
        assertThat(ChatRules.banned("미 친 놈 아")).isTrue();
    }

    @Test
    void 목록에_없는_말은_통과한다() {
        assertThat(ChatRules.banned("안녕 반가워")).isFalse();
        assertThat(ChatRules.banned("")).isFalse();
    }
}
