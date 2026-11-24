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
    void 목록에_없는_말은_통과한다() {
        assertThat(ChatRules.banned("안녕 반가워")).isFalse();
        assertThat(ChatRules.banned("")).isFalse();
    }
}
