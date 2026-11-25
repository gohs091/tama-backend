package com.tamagochi.tamagochi.service.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PlazaImageCheckTest {

    private static byte[] encode(int w, int h, String format) throws Exception {
        BufferedImage img = new BufferedImage(w, h, format.equals("jpg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static String url(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void 광장에_보내는_160칸_PNG는_받는다() throws Exception {
        assertThat(PlazaImageCheck.accepts(url("image/png", encode(160, 160, "png")))).isTrue();
    }

    @Test
    void 광장에_보내는_64칸_PNG는_받는다() throws Exception {
        assertThat(PlazaImageCheck.accepts(url("image/png", encode(64, 64, "png")))).isTrue();
    }

    @Test
    void JPEG는_받는다() throws Exception {
        assertThat(PlazaImageCheck.accepts(url("image/jpeg", encode(100, 100, "jpg")))).isTrue();
    }

    @Test
    void svg_data_URL은_받지_않는다() {
        assertThat(PlazaImageCheck.accepts("data:image/svg+xml,<svg width=\"8000\" height=\"8000\"></svg>")).isFalse();
        assertThat(PlazaImageCheck.accepts("data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString("<svg width=\"8000\" height=\"8000\"/>".getBytes(StandardCharsets.UTF_8)))).isFalse();
    }

    @Test
    void 머리에_8000칸이라고만_적힌_작은_PNG도_받지_않는다() throws Exception {
        byte[] png = encode(8, 8, "png");
        // IHDR 가로 · 세로(16~23바이트)만 8000 으로 고친다. 파일은 그대로 작다
        for (int off : new int[]{16, 20}) {
            png[off] = 0; png[off + 1] = 0; png[off + 2] = 0x1F; png[off + 3] = 0x40;
        }
        assertThat(PlazaImageCheck.accepts(url("image/png", png))).isFalse();
    }

    @Test
    void 진짜_2000칸_PNG는_받지_않는다() throws Exception {
        assertThat(PlazaImageCheck.accepts(url("image/png", encode(2000, 10, "png")))).isFalse();
        assertThat(PlazaImageCheck.accepts(url("image/png", encode(10, 2000, "png")))).isFalse();
    }

    @Test
    void 가로_세로_1024는_받고_1025는_받지_않는다() throws Exception {
        assertThat(PlazaImageCheck.accepts(url("image/png", encode(1024, 4, "png")))).isTrue();
        assertThat(PlazaImageCheck.accepts(url("image/png", encode(1025, 4, "png")))).isFalse();
    }

    @Test
    void 깨진_base64는_받지_않는다() {
        assertThat(PlazaImageCheck.accepts("data:image/png;base64,@@@@!!!")).isFalse();
        assertThat(PlazaImageCheck.accepts("data:image/png;base64,AAAA")).isFalse();
        assertThat(PlazaImageCheck.accepts("data:image/png;base64,")).isFalse();
    }

    @Test
    void PNG라고_했지만_내용이_svg_글이면_받지_않는다() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"8000\" height=\"8000\"></svg>";
        assertThat(PlazaImageCheck.accepts(url("image/png", svg.getBytes(StandardCharsets.UTF_8)))).isFalse();
    }

    @Test
    void 허용하지_않는_종류와_빈_값은_받지_않는다() throws Exception {
        assertThat(PlazaImageCheck.accepts(url("image/gif", encode(8, 8, "gif")))).isFalse();
        assertThat(PlazaImageCheck.accepts("")).isFalse();
        assertThat(PlazaImageCheck.accepts(null)).isFalse();
        assertThat(PlazaImageCheck.accepts("https://cdn.example/a.png")).isFalse();
    }
}
