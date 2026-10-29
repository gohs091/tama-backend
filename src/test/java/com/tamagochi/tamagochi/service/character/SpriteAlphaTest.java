package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpriteAlphaTest {

    private static final int BODY = 0xFF3366CC;
    private static final int W = 12, H = 20;

    private static int[] blank() {
        return new int[W * H];
    }

    private static void rect(int[] px, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) px[y * W + x] = argb;
    }

    @Test
    @DisplayName("몸 안쪽에서 옅게 색이 남은 구멍은 채운다: 남은 색이 믿을 만하면 그 색, 아니면 가까운 몸 색")
    void fillsInteriorHoles() {
        int[] px = blank();
        rect(px, 2, 2, 9, 14, BODY);
        rect(px, 3, 4, 5, 6, (60 << 24) | 0x112233);        // 알파는 낮지만 색이 남아 있는 구멍
        px[5 * W + 4] = 0;                                  // 그 한가운데는 색도 없다
        rect(px, 7, 4, 8, 5, (30 << 24) | 0x445566);        // 색 정보를 믿기 어려울 만큼 옅은 구멍

        int[] out = SpriteAlpha.clean(px, W, H);

        assertThat(out[4 * W + 3] >>> 24).isEqualTo(255);
        assertThat(out[4 * W + 3] & 0xFFFFFF).isEqualTo(0x112233);          // 남아 있던 색을 쓴다
        assertThat(out[5 * W + 4] >>> 24).isEqualTo(255);
        assertThat(out[5 * W + 4] & 0xFFFFFF).isEqualTo(0x112233);          // 가까운 색을 쓴다
        assertThat(out[4 * W + 7] >>> 24).isEqualTo(255);
        assertThat(out[4 * W + 7] & 0xFFFFFF).isEqualTo(BODY & 0xFFFFFF);   // 몸 색을 쓴다
        assertThat(out[0]).isZero();                                         // 바깥은 그대로 투명
    }

    @Test
    @DisplayName("몸 안쪽이라도 거의 완전히 투명한 공간(꼬리 고리 등)은 일부러 비운 곳이라 채우지 않는다")
    void keepsFullyTransparentEnclosedSpace() {
        int[] px = blank();
        rect(px, 2, 2, 9, 14, BODY);
        rect(px, 4, 5, 6, 8, 0);                            // 몸 한가운데에 갇힌 완전 투명 공간

        int[] out = SpriteAlpha.clean(px, W, H);

        assertThat(out[6 * W + 5] >>> 24).isZero();
    }

    @Test
    @DisplayName("알파 128 기준: 미만은 투명, 이상은 불투명, 흐린 번짐은 지운다")
    void binarizesOutsideEdges() {
        int[] px = blank();
        rect(px, 2, 2, 9, 12, BODY);
        px[1 * W + 1] = (100 << 24) | 0xAAAAAA;   // 바깥의 흐린 번짐
        px[2 * W + 1] = (200 << 24) | 0xAAAAAA;   // 바깥의 진한 가장자리

        int[] out = SpriteAlpha.clean(px, W, H);

        assertThat(out[1 * W + 1]).isZero();
        assertThat(out[2 * W + 1] >>> 24).isEqualTo(255);
    }

    @Test
    @DisplayName("다리 사이처럼 바닥에 닿은 빈 공간은 채우지 않는다")
    void keepsGapBetweenLegs() {
        int[] px = blank();
        rect(px, 2, 2, 9, 10, BODY);       // 몸통
        rect(px, 2, 11, 4, 17, BODY);      // 왼쪽 다리
        rect(px, 7, 11, 9, 17, BODY);      // 오른쪽 다리
        rect(px, 2, 18, 9, 18, BODY);      // 발바닥이 이어져 다리 사이가 갇힌다
        // 갇힌 공간: x 5~6, y 11~17 (바닥 쪽으로 내려가 있다)

        int[] out = SpriteAlpha.clean(px, W, H);

        assertThat(out[14 * W + 5] >>> 24).isZero();
        assertThat(out[14 * W + 6] >>> 24).isZero();
    }

    @Test
    @DisplayName("보이는 픽셀이 없으면 모두 투명, 이미지가 아닌 바이트는 null")
    void emptyAndInvalid() {
        assertThat(SpriteAlpha.clean(blank(), W, H)).containsOnly(0);
        assertThat(SpriteAlpha.clean("not-a-png".getBytes())).isNull();
    }
}
