package com.tamagochi.tamagochi.service.storage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
    /** 알파가 이 이상이면 보이는 점 */
    static final int VISIBLE = 128;
    /** 알파가 이보다 낮으면 없는 점 */
    static final int FAINT = 16;
    /** 줄인 그림에 그려진 점이 이보다 적으면 빈 그림으로 본다 */
    static final int MIN_POINTS = 12;

    private SpriteThumb() {
    }

    /** 앱이 보낸 작은 그림은 한 변이 이보다 크지 않다. 더 큰 그림은 풀어 보지 않는다 (작은 파일로 큰 그림을 보내 메모리를 쓰게 하는 것을 막는다) */
    static final int MAX_CHECK_SIDE = 512;

    /**
     * data URL 그림에 그려진 것이 없으면 true (투명한 빈 그림). 아이폰 앱이 가끔 빈 그림을 만들어 보내는데,
     * 그대로 받으면 광장에 이름표만 떠다닌다. 읽지 못했거나 너무 큰 그림은 빈 그림이라고 단정하지 않는다 (false)
     */
    static boolean blank(String dataUrl) {
        if (dataUrl == null) return false;
        int comma = dataUrl.indexOf(',');
        if (comma < 0) return false;
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(dataUrl.substring(comma + 1));
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) return false;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    if (reader.getWidth(0) > MAX_CHECK_SIDE || reader.getHeight(0) > MAX_CHECK_SIDE) return false;
                    BufferedImage image = reader.read(0);
                    int drawn = 0;
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            if ((image.getRGB(x, y) >>> 24) >= FAINT && ++drawn >= MIN_POINTS) return false;
                        }
                    }
                    return true;
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** 줄이지 못하면(그림이 아니거나, 읽기 실패거나, 그려진 것이 없는 빈 그림이면) null */
    static String dataUrl(byte[] image) {
        if (image == null || image.length == 0) return null;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(image));
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            double k = Math.min(1.0, (double) SIZE / Math.max(w, h));
            int tw = Math.max(1, (int) Math.round(w * k)), th = Math.max(1, (int) Math.round(h * k));
            // 가장 가까운 점을 그대로 가져온다 (부드럽게 줄이면 도트가 뭉개진다)
            int[] px = new int[tw * th];
            int solid = 0, faint = 0;
            for (int y = 0; y < th; y++) {
                int sy = Math.min(h - 1, (int) ((y + 0.5) * h / th));
                for (int x = 0; x < tw; x++) {
                    int sx = Math.min(w - 1, (int) ((x + 0.5) * w / tw));
                    int argb = src.getRGB(sx, sy);
                    px[y * tw + x] = argb;
                    int a = argb >>> 24;
                    if (a >= VISIBLE) solid++;
                    else if (a >= FAINT) faint++;
                }
            }
            // 그려진 것이 없는 그림은 줄여 봐야 이름표만 떠다닌다: 못 줄인 것으로 친다
            if (solid + faint < MIN_POINTS) return null;
            // 몸이 반투명하게 그려진 그림(유령·젤리 같은)은 알파를 자르면 몸이 통째로 사라진다: 알파를 그대로 둔다
            boolean translucent = faint > solid;
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < px.length; i++) {
                int a = px[i] >>> 24;
                // 반투명 가장자리는 보이거나 안 보이거나 둘 중 하나로: 테두리가 또렷해지고 파일도 작아진다
                out.setRGB(i % tw, i / tw, translucent ? (a < FAINT ? 0 : px[i]) : (a < VISIBLE ? 0 : px[i] | 0xFF000000));
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(out, "png", png)) return null;
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
