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
        BufferedImage small = read(SpriteThumb.dataUrl(png(new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB))));
        BufferedImage wide = read(SpriteThumb.dataUrl(png(new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB))));

        assertThat(small.getWidth()).isEqualTo(40);
        assertThat(wide.getWidth()).isEqualTo(128);
        assertThat(wide.getHeight()).isEqualTo(64);
    }

    @Test
    @DisplayName("그림이 아니면 null")
    void notAnImage() {
        assertThat(SpriteThumb.dataUrl(null)).isNull();
        assertThat(SpriteThumb.dataUrl(new byte[0])).isNull();
        assertThat(SpriteThumb.dataUrl("그림이 아님".getBytes())).isNull();
    }
}
