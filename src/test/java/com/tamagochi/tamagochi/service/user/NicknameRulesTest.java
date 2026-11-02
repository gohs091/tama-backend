package com.tamagochi.tamagochi.service.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameRulesTest {

    @Test
    @DisplayName("무작위 닉네임은 '형용사 명사 4자리' 모양이고 규칙을 통과한다")
    void randomNickname() {
        for (int i = 0; i < 200; i++) {
            String n = NicknameRules.randomNickname();
            assertThat(n).matches("\\S+ \\S+ \\d{4}");
            assertThat(NicknameRules.clean(n)).isEqualTo(n);
        }
    }

    @Test
    @DisplayName("유저 코드는 헷갈리는 글자 없이 8자, 화면에서는 4-4로 끊는다")
    void userCode() {
        String code = NicknameRules.randomUserCode();
        assertThat(code).hasSize(8).doesNotContain("I", "L", "O", "U");
        assertThat(NicknameRules.formatCode("K7Q29XMA")).isEqualTo("K7Q2-9XMA");
    }

    @Test
    @DisplayName("입력한 코드는 하이픈·공백·소문자·헷갈리는 글자를 보정해 찾는다")
    void normalizeCode() {
        assertThat(NicknameRules.normalizeCode(" k7q2-9xma ")).isEqualTo("K7Q29XMA");
        assertThat(NicknameRules.normalizeCode("O0I1-L234")).isEqualTo("00111234");
        assertThat(NicknameRules.normalizeCode("ABC")).isNull();
        assertThat(NicknameRules.normalizeCode("UUUU-UUUU")).isNull();
    }

    @Test
    @DisplayName("닉네임 정리: 앞뒤 공백 제거, 여러 칸 공백은 한 칸, 중복은 허용")
    void clean() {
        assertThat(NicknameRules.clean("  무야호   할아버지 ")).isEqualTo("무야호 할아버지");
        assertThat(NicknameRules.clean("ㅋㅋㅋ")).isEqualTo("ㅋㅋㅋ");
    }

    @Test
    @DisplayName("너무 길거나, 비었거나, 운영자를 흉내 내거나, 꺾쇠가 있으면 막는다")
    void reject() {
        assertThatThrownBy(() -> NicknameRules.clean("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NicknameRules.clean("가나다라마바사아자차카타파")).hasMessageContaining("12글자");
        assertThatThrownBy(() -> NicknameRules.clean("끄적이 운영자")).hasMessageContaining("운영자");
        assertThatThrownBy(() -> NicknameRules.clean("Admin짱")).hasMessageContaining("운영자");
        assertThatThrownBy(() -> NicknameRules.clean("<b>hi</b>")).hasMessageContaining("쓸 수 없는 글자");
        assertThatThrownBy(() -> NicknameRules.clean("toss_abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tossUserId가 닉네임에 들어가 있던 예전 유저는 새 닉네임이 필요하다")
    void needsNickname() {
        assertThat(NicknameRules.needsNickname("toss_abc", "toss_abc")).isTrue();
        assertThat(NicknameRules.needsNickname(null, "toss_abc")).isTrue();
        assertThat(NicknameRules.needsNickname("용감한 연필 0001", "toss_abc")).isFalse();
    }
}
