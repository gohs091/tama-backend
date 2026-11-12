package com.tamagochi.tamagochi.service.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PetThumbsTest {

    private static final String KEY = "public/pets/0123456789abcdef0123456789abcdef/child.1a2b3c4d.png";

    private String cdnBefore;
    private final List<String> asked = new ArrayList<>();

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

    private static byte[] sprite() throws Exception {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int y = 300; y < 700; y++) {
            for (int x = 300; x < 700; x++) image.setRGB(x, y, 0xFF8BA64E);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("원본을 한 번만 받아 줄이고, 같은 키는 기억한 것을 쓴다")
    void fetchesOnce() throws Exception {
        byte[] original = sprite();
        PetThumbs thumbs = new PetThumbs(url -> { asked.add(url); return original; });

        String first = thumbs.of(KEY).orElseThrow();
        String second = thumbs.of(KEY).orElseThrow();

        assertThat(first).startsWith("data:image/png;base64,").isEqualTo(second);
        assertThat(asked).containsExactly("https://cdn.test/" + KEY);
    }

    @Test
    @DisplayName("그림 키가 없으면 원본을 받으러 가지 않는다")
    void noKey() {
        PetThumbs thumbs = new PetThumbs(url -> { asked.add(url); return null; });

        assertThat(thumbs.of(null)).isEmpty();
        assertThat(thumbs.of(" ")).isEmpty();
        assertThat(asked).isEmpty();
    }

    @Test
    @DisplayName("원본을 받지 못하면 빈 값이고, 다음에 다시 받아 본다")
    void retriesAfterFailure() throws Exception {
        byte[] original = sprite();
        PetThumbs thumbs = new PetThumbs(url -> { asked.add(url); return asked.size() == 1 ? null : original; });

        assertThat(thumbs.of(KEY)).isEmpty();
        assertThat(thumbs.of(KEY)).isPresent();
        assertThat(asked).hasSize(2);
    }

    @Test
    @DisplayName("줄여도 너무 긴 그림(잡음 같은 그림)은 쓰지 않는다")
    void tooLong() throws Exception {
        BufferedImage noise = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) noise.setRGB(x, y, 0xFF000000 | random.nextInt(0x1000000));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(noise, "png", out);
        PetThumbs thumbs = new PetThumbs(url -> out.toByteArray());

        assertThat(thumbs.of(KEY)).isEmpty();
    }
}
