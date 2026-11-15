package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpriteHolesTest {

    private static final int BODY = 0xFF3366CC;
    private static final int W = 12, H = 12;

    private static void rect(int[] px, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) px[y * W + x] = argb;
    }

    @Test
    @DisplayName("몸 안에 갇힌 투명 구멍은 흰색으로 채우고, 몸 바깥과 몸 색은 그대로 둔다")
    void fillsEnclosedHolesWithWhite() {
        int[] px = new int[W * H];
        rect(px, 2, 2, 9, 9, BODY);
        rect(px, 4, 4, 5, 5, 0);                       // 완전히 투명한 구멍 (흰 배·창문)
        px[7 * W + 7] = (40 << 24) | 0x112233;         // 반투명 구멍

        assertThat(SpriteHoles.fillWhite(px, W, H)).isTrue();

        assertThat(px[4 * W + 4]).isEqualTo(0xFFFFFFFF);
        assertThat(px[5 * W + 5]).isEqualTo(0xFFFFFFFF);
        assertThat(px[7 * W + 7]).isEqualTo(0xFFFFFFFF);
        assertThat(px[0]).isZero();                    // 바깥은 투명 그대로
        assertThat(px[3 * W + 3]).isEqualTo(BODY);
    }

    @Test
    @DisplayName("바깥과 이어진 빈 곳(다리 사이 등)은 채우지 않는다")
    void keepsSpaceOpenToOutside() {
        int[] px = new int[W * H];
        rect(px, 2, 2, 9, 9, BODY);
        rect(px, 5, 6, 6, 11, 0);                      // 아래로 열린 틈

        assertThat(SpriteHoles.fillWhite(px, W, H)).isFalse();
        assertThat(px[7 * W + 5]).isZero();
    }

    @Test
    @DisplayName("PNG로 넣으면 구멍을 채운 PNG가 나오고, 채울 곳이 없으면 받은 그림 그대로다")
    void pngRoundTrip() throws Exception {
        int[] px = new int[W * H];
        rect(px, 2, 2, 9, 9, BODY);
        byte[] solid = png(px);
        assertThat(SpriteHoles.fillWhite(solid)).isSameAs(solid);

        rect(px, 4, 4, 5, 5, 0);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(SpriteHoles.fillWhite(png(px))));
        assertThat(out.getRGB(4, 4)).isEqualTo(0xFFFFFFFF);
        assertThat(out.getRGB(0, 0) >>> 24).isZero();
    }

    private static byte[] png(int[] px) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, W, H, px, 0, W);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }
}
