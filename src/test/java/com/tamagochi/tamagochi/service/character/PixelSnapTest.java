package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PixelSnapTest {

    @Test
    void 칸마다_한_색으로_찍고_색_수를_줄인다() throws Exception {
        int size = 1024, cell = size / PixelSnap.GRID;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(1);
        for (int y = 200; y < 800; y++) for (int x = 200; x < 800; x++) {
            int v = 100 + rnd.nextInt(80);                    // 자글자글한 보라 점
            img.setRGB(x, y, 0xFF000000 | (v << 16) | (40 << 8) | (v + 50));
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);

        BufferedImage out = ImageIO.read(new ByteArrayInputStream(PixelSnap.snap(bos.toByteArray())));

        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int p = out.getRGB(x, y);
            if ((p >>> 24) != 0) colors.add(p);
            int gx = x / cell * cell, gy = y / cell * cell;           // 같은 칸은 같은 색
            assertThat(p).isEqualTo(out.getRGB(gx, gy));
        }
        assertThat(colors.size()).isLessThanOrEqualTo(PixelSnap.MAX_COLORS);
        assertThat(out.getRGB(10, 10) >>> 24).isZero();               // 배경은 투명
    }
}
