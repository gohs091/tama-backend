package com.tamagochi.tamagochi.service.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PetThumbsDot64Test {

    private String cdnBefore;

    @BeforeEach
    void setUp() {
        String probe = PetImageUrls.of("x");
        cdnBefore = probe == null ? "" : probe.substring(0, probe.length() - 2);
        PetImageUrls.setCdnBase("https://cdn.test");
    }

    @AfterEach
    void tearDown() {
        PetImageUrls.setCdnBase(cdnBefore);
    }

    @Test
    void 네_배로_키운_도트_그림은_원래_칸으로_돌아온다() {
        Random r = new Random(7);
        BufferedImage dots = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) dots.setRGB(x, y, 0xFF000000 | r.nextInt(0x1000000));
        BufferedImage big = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) big.setRGB(x, y, dots.getRGB(x / 4, y / 4));

        BufferedImage back = PetThumbs.snap64(big);

        assertThat(back.getWidth()).isEqualTo(64);
        assertThat(back.getHeight()).isEqualTo(64);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) assertThat(back.getRGB(x, y)).isEqualTo(dots.getRGB(x, y));
    }

    @Test
    void 큰_원본도_64칸이_된다() {
        BufferedImage big = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int y = 256; y < 768; y++) for (int x = 256; x < 768; x++) big.setRGB(x, y, 0xFFCC3355);

        BufferedImage out = PetThumbs.snap64(big);

        assertThat(out.getWidth()).isEqualTo(64);
        assertThat(out.getHeight()).isEqualTo(64);
        assertThat(out.getRGB(32, 32)).isEqualTo(0xFFCC3355);
        assertThat(out.getRGB(2, 2) >>> 24).isZero();   // 바깥은 투명
    }

    @Test
    void 투명이_절반_넘는_칸은_투명이다() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        // 한 칸은 2x2점: 왼쪽 위 칸은 1점만 불투명(투명이 절반 넘음), 그 옆 칸은 3점 불투명
        img.setRGB(0, 0, 0xFF112233);
        img.setRGB(2, 0, 0xFF112233); img.setRGB(3, 0, 0xFF112233); img.setRGB(2, 1, 0xFF112233);
        BufferedImage out = PetThumbs.snap64(img);
        assertThat(out.getRGB(0, 0) >>> 24).isZero();
        assertThat(out.getRGB(1, 0)).isEqualTo(0xFF112233);
    }

    @Test
    void dot64는_64칸_png_data_url을_준다() throws Exception {
        BufferedImage big = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) big.setRGB(x, y, 0xFF2266AA);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(big, "png", bos);
        PetThumbs thumbs = new PetThumbs(url -> bos.toByteArray());

        String url = thumbs.dot64("pets/abc.png");

        assertThat(url).startsWith("data:image/png;base64,");
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1))));
        assertThat(read.getWidth()).isEqualTo(64);
        assertThat(read.getHeight()).isEqualTo(64);
    }

    @Test
    void dot64는_키가_없거나_원본을_못_받으면_null이다() {
        assertThat(new PetThumbs(url -> null).dot64(null)).isNull();
        assertThat(new PetThumbs(url -> null).dot64("pets/abc.png")).isNull();
    }
}
