package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ChromaKeyTest {

    private static final int MAGENTA = 0xFFFF00FF;
    private static final int OUTLINE = 0xFF202030;
    private static final int BODY = 0xFF3399EE;
    private static final int W = 40, H = 40;

    private static int[] canvas() {
        int[] px = new int[W * H];
        Arrays.fill(px, MAGENTA);
        return px;
    }

    private static void rect(int[] px, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) px[y * W + x] = argb;
    }

    @Test
    @DisplayName("배경색만 투명해지고 몸과 흰 눈은 그대로 남는다. 꼬리 고리 안쪽처럼 배경색으로 칠해진 작은 틈도 투명해진다")
    void cutsOnlyBackground() {
        int[] px = canvas();
        rect(px, 8, 8, 31, 31, OUTLINE);
        rect(px, 10, 10, 29, 29, BODY);
        rect(px, 12, 12, 13, 13, 0xFFFFFFFF);     // 흰 눈
        rect(px, 24, 24, 26, 26, MAGENTA);        // 몸으로 둘러싸인 진짜 빈 곳

        assertThat(ChromaKey.cut(px, W, H)).isTrue();

        assertThat(px[0]).isZero();
        assertThat(px[15 * W + 15]).isEqualTo(BODY);
        assertThat(px[12 * W + 12]).isEqualTo(0xFFFFFFFF);
        assertThat(px[25 * W + 25]).isZero();
    }

    @Test
    @DisplayName("몸 안쪽 색이 배경색과 비슷해 통째로 지워지면 실패로 본다")
    void rejectsWhenBodyIsCut() {
        int[] px = canvas();
        rect(px, 8, 8, 31, 31, OUTLINE);
        rect(px, 10, 10, 29, 29, 0xFFFF30E0);     // 배경색과 비슷한 분홍 몸

        assertThat(ChromaKey.cut(px, W, H)).isFalse();
    }

    @Test
    @DisplayName("테두리 없는 몸이 배경색과 비슷해 거의 다 지워져도 실패로 본다")
    void rejectsWhenAlmostNothingIsLeft() {
        int[] px = canvas();
        rect(px, 8, 8, 31, 31, 0xFFFF30E0);
        rect(px, 12, 12, 13, 13, OUTLINE);        // 눈만 남는다

        assertThat(ChromaKey.cut(px, W, H)).isFalse();
    }

    @Test
    @DisplayName("가장자리가 한 가지 색이 아니면(모델이 배경을 단색으로 칠하지 않음) 실패로 본다")
    void rejectsUnevenBackground() {
        int[] px = canvas();
        rect(px, 0, 0, W - 1, 12, 0xFF88CCFF);    // 위쪽에 하늘을 그려 넣었다
        rect(px, 10, 14, 29, 29, BODY);

        assertThat(ChromaKey.cut(px, W, H)).isFalse();
    }

    @Test
    @DisplayName("유저 그림에 분홍·보라가 쓰였으면 초록 배경을, 아니면 자홍색 배경을 고른다")
    void picksKeyAwayFromDoodleColors() {
        int[] line = new int[W * H];
        Arrays.fill(line, 0xFFFFFFFF);
        rect(line, 5, 5, 30, 6, 0xFF191F28);
        assertThat(ChromaKey.pick(line)).isEqualTo(ChromaKey.Key.MAGENTA);

        int[] pink = line.clone();
        rect(pink, 10, 10, 25, 25, 0xFFFF69B4);
        assertThat(ChromaKey.pick(pink)).isEqualTo(ChromaKey.Key.GREEN);

        int[] green = line.clone();
        rect(green, 10, 10, 25, 25, 0xFF2EC487);
        assertThat(ChromaKey.pick(green)).isEqualTo(ChromaKey.Key.MAGENTA);
    }

    @Test
    @DisplayName("진영 색과 겹치는 배경색은 고르지 않고, 둘 다 겹치면 단색 배경을 쓰지 않는다")
    void picksKeyAwayFromFactionColors() {
        byte[] unreadable = new byte[0];
        assertThat(ChromaKey.pick(unreadable, "pink and white color scheme")).isEqualTo(ChromaKey.Key.GREEN);
        assertThat(ChromaKey.pick(unreadable, "royal purple and gold color scheme")).isEqualTo(ChromaKey.Key.GREEN);
        assertThat(ChromaKey.pick(unreadable, "olive green and tan color scheme")).isEqualTo(ChromaKey.Key.MAGENTA);
        assertThat(ChromaKey.pick(unreadable, "blue and cyan color scheme")).isEqualTo(ChromaKey.Key.MAGENTA);
        assertThat(ChromaKey.pick(unreadable, "pastel pink, mint and cream color scheme")).isNull();
        assertThat(ChromaKey.pick(unreadable, "rainbow multicolor scheme")).isNull();
    }

    @Test
    @DisplayName("프롬프트의 투명 배경 문구가 단색 배경 지시로 바뀐다")
    void rewritesPrompt() {
        String out = ChromaKey.prompt("Single character, transparent background, no text.", ChromaKey.Key.GREEN);
        assertThat(out).doesNotContain("transparent background").contains("green (#00FF00) background").contains("never use bright green or lime");
    }
}
