package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpriteBackdropTest {

    private static final int OUTLINE = 0xFF1E0A2E, PURPLE = 0xFF7B3F9E;

    /** 투명 배경 위에 테두리 두른 네모 한 개 */
    private static byte[] sprite(int outline, int body, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(40, 40, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 8; y < 32; y++) for (int x = 8; x < 32; x++) {
            boolean edge = x < 10 || y < 10 || x >= 30 || y >= 30;
            img.setRGB(x, y, edge ? outline : body);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    @DisplayName("투명 배경에 옅은 회색을 깔고, 끄적이 픽셀은 그대로 둔다 (테두리가 검은 배경에 묻히지 않게)")
    void flattensOntoLightGray() throws Exception {
        byte[] flat = SpriteBackdrop.flatten(sprite(OUTLINE, PURPLE, true));

        BufferedImage out = ImageIO.read(new ByteArrayInputStream(flat));
        assertThat(out.getColorModel().hasAlpha()).isFalse();
        assertThat(out.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xD9D9D9);
        assertThat(out.getRGB(8, 8) & 0xFFFFFF).isEqualTo(OUTLINE & 0xFFFFFF);
        assertThat(out.getRGB(20, 20) & 0xFFFFFF).isEqualTo(PURPLE & 0xFFFFFF);
    }

    @Test
    @DisplayName("몸이 회색이면 몸과 헷갈리지 않는 다른 배경색을 고른다")
    void avoidsBackdropCloseToBody() throws Exception {
        byte[] flat = SpriteBackdrop.flatten(sprite(OUTLINE, 0xFFDCDCDC, true));

        int backdrop = ImageIO.read(new ByteArrayInputStream(flat)).getRGB(0, 0) & 0xFFFFFF;
        assertThat(backdrop).isNotEqualTo(0xD9D9D9);
        assertThat(SpriteBackdrop.CANDIDATES).contains(backdrop);
    }

    @Test
    @DisplayName("투명한 곳이 없는 그림(이미 배경이 있음)과 이미지가 아닌 값은 건드리지 않는다")
    void leavesOpaqueImagesAlone() throws Exception {
        assertThat(SpriteBackdrop.flatten(sprite(OUTLINE, PURPLE, false))).isNull();
        assertThat(SpriteBackdrop.flatten(new byte[]{1, 2, 3})).isNull();
    }
}
