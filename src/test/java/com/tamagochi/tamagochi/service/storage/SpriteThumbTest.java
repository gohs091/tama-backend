package com.tamagochi.tamagochi.service.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SpriteThumbTest {

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage read(String dataUrl) throws Exception {
        assertThat(dataUrl).startsWith("data:image/png;base64,");
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1))));
    }

    /** 운영 그림처럼 1024px에 64칸 격자로 찍은 그림: (cx, cy) 칸의 색은 칸마다 다르다 */
    private static BufferedImage grid() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 1024; y++) {
            for (int x = 0; x < 1024; x++) image.setRGB(x, y, cell(x / 16, y / 16));
        }
        return image;
    }

    private static int cell(int cx, int cy) {
        return (cx + cy) % 3 == 0 ? 0 : 0xFF000000 | (cx * 4) << 16 | (cy * 4) << 8 | 0x40;
    }

    @Test
    @DisplayName("64칸 격자 그림은 128px로 줄어들고, 한 칸이 정확히 2점씩 같은 색으로 남는다")
    void gridSurvives() throws Exception {
        BufferedImage thumb = read(SpriteThumb.dataUrl(png(grid())));

        assertThat(thumb.getWidth()).isEqualTo(128);
        assertThat(thumb.getHeight()).isEqualTo(128);
        for (int cy = 0; cy < 64; cy += 7) {
            for (int cx = 0; cx < 64; cx += 5) {
                int want = cell(cx, cy);
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) assertThat(thumb.getRGB(cx * 2 + dx, cy * 2 + dy)).isEqualTo(want);
                }
            }
        }
    }

    @Test
    @DisplayName("원본이 1MB쯤 되는 그림도 작은 그림은 광장 메시지에 실을 만큼 작다")
    void smallEnough() throws Exception {
        String thumb = SpriteThumb.dataUrl(png(grid()));

        assertThat(thumb.length()).isLessThan(PetThumbs.MAX_CHARS);
    }

    @Test
    @DisplayName("반투명한 점은 보이거나 안 보이거나 둘 중 하나가 된다")
    void alphaIsCut() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) image.setRGB(x, y, x < 32 ? 0x40336699 : 0xC0336699);
        }

        BufferedImage thumb = read(SpriteThumb.dataUrl(png(image)));

        assertThat(thumb.getRGB(5, 5) >>> 24).isZero();
        assertThat(thumb.getRGB(50, 5)).isEqualTo(0xFF336699);
    }

    @Test
    @DisplayName("작은 그림은 키우지 않고, 길쭉한 그림은 비율을 지킨다")
    void keepsShape() throws Exception {
        BufferedImage small = read(SpriteThumb.dataUrl(png(filled(40, 40, 0xFF336699))));
        BufferedImage wide = read(SpriteThumb.dataUrl(png(filled(512, 256, 0xFF336699))));

        assertThat(small.getWidth()).isEqualTo(40);
        assertThat(wide.getWidth()).isEqualTo(128);
        assertThat(wide.getHeight()).isEqualTo(64);
    }

    @Test
    @DisplayName("몸이 반투명하게 그려진 그림은 지우지 않고 그대로 둔다 (자르면 이름표만 떠다닌다)")
    void translucentBodyStays() throws Exception {
        // 또렷한 눈 두 점만 불투명하고 몸은 알파 0x60인 유령
        BufferedImage ghost = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 16; y < 48; y++) {
            for (int x = 16; x < 48; x++) ghost.setRGB(x, y, 0x60FFFFFF);
        }
        ghost.setRGB(24, 24, 0xFF000000);
        ghost.setRGB(40, 24, 0xFF000000);

        BufferedImage thumb = read(SpriteThumb.dataUrl(png(ghost)));

        assertThat(thumb.getRGB(30, 40) >>> 24).isEqualTo(0x60);
        assertThat(thumb.getRGB(24, 24)).isEqualTo(0xFF000000);
        assertThat(thumb.getRGB(5, 5) >>> 24).isZero();
    }

    @Test
    @DisplayName("그려진 것이 없는 그림은 null (빈 그림을 보내지 않는다)")
    void blankIsNull() throws Exception {
        assertThat(SpriteThumb.dataUrl(png(new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB)))).isNull();
        // 거의 안 보이는 얼룩뿐인 그림
        assertThat(SpriteThumb.dataUrl(png(filled(256, 256, 0x08FFFFFF)))).isNull();
    }

    private static BufferedImage filled(int w, int h, int argb) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) image.setRGB(x, y, argb);
        }
        return image;
    }

    @Test
    @DisplayName("앱이 보낸 그림: 투명한 빈 그림만 빈 그림으로 본다 (못 읽는 것, 너무 큰 것은 단정하지 않는다)")
    void blankDataUrl() throws Exception {
        String empty = "data:image/png;base64," + Base64.getEncoder().encodeToString(png(new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB)));
        String drawn = "data:image/png;base64," + Base64.getEncoder().encodeToString(png(filled(160, 160, 0xFF336699)));
        String huge = "data:image/png;base64," + Base64.getEncoder().encodeToString(png(new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_ARGB)));

        assertThat(SpriteThumb.blank(empty)).isTrue();
        assertThat(SpriteThumb.blank(drawn)).isFalse();
        assertThat(SpriteThumb.blank(huge)).isFalse();
        assertThat(SpriteThumb.blank("data:image/png;base64,AAAA")).isFalse();
        assertThat(SpriteThumb.blank("그림이 아님")).isFalse();
        assertThat(SpriteThumb.blank(null)).isFalse();
    }

    @Test
    @DisplayName("그림이 아니면 null")
    void notAnImage() {
        assertThat(SpriteThumb.dataUrl(null)).isNull();
        assertThat(SpriteThumb.dataUrl(new byte[0])).isNull();
        assertThat(SpriteThumb.dataUrl("그림이 아님".getBytes())).isNull();
    }
}
