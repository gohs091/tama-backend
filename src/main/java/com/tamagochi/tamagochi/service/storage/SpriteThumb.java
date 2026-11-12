package com.tamagochi.tamagochi.service.storage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 끄적이 그림을 작게 줄인다 (PNG data URL).
 * 원본은 1024px이고, 격자로 다시 찍기(11/10) 전에 만든 그림은 한 장이 1MB 안팎이다.
 * 여러 마리를 한 화면에 작게 보여 주는 곳에서 원본을 그대로 받게 하면 그림이 한참 뒤에 뜨거나 안 뜬다.
 */
final class SpriteThumb {

    /** 64칸 격자로 찍은 그림이면 한 칸이 정확히 2점이 된다 */
    static final int SIZE = 128;

    private SpriteThumb() {
    }

    /** 줄이지 못하면(그림이 아니거나 읽기 실패) null */
    static String dataUrl(byte[] image) {
        if (image == null || image.length == 0) return null;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(image));
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            double k = Math.min(1.0, (double) SIZE / Math.max(w, h));
            int tw = Math.max(1, (int) Math.round(w * k)), th = Math.max(1, (int) Math.round(h * k));
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            // 가장 가까운 점을 그대로 가져온다 (부드럽게 줄이면 도트가 뭉개진다)
            for (int y = 0; y < th; y++) {
                int sy = Math.min(h - 1, (int) ((y + 0.5) * h / th));
                for (int x = 0; x < tw; x++) {
                    int sx = Math.min(w - 1, (int) ((x + 0.5) * w / tw));
                    int argb = src.getRGB(sx, sy);
                    // 반투명 가장자리는 보이거나 안 보이거나 둘 중 하나로: 테두리가 또렷해지고 파일도 작아진다
                    out.setRGB(x, y, (argb >>> 24) < 128 ? 0 : argb | 0xFF000000);
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(out, "png", png)) return null;
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
