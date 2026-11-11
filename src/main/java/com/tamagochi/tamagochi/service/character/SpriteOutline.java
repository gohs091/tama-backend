package com.tamagochi.tamagochi.service.character;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * 새로 그린 그림이 원래 그림의 테두리를 잃었는지 본다.
 * 모델이 가끔 어두운 테두리를 투명하게 비워 버린다 (그러면 테두리가 없고, 눈·입 선 자리에 구멍이 난다).
 * 가장자리 픽셀(바깥 투명과 맞닿은 픽셀)의 밝기로 판단한다: 원래 그림은 가장자리가 몸보다 뚜렷이 어두운데
 * 새 그림의 가장자리는 그만큼 어둡지 않으면 테두리를 잃은 것이다.
 */
final class SpriteOutline {

    private static final int VISIBLE = 128;
    /** 원래 그림에 테두리가 있다고 보는 기준: 가장자리가 몸 안쪽보다 이만큼 이상 어둡다 */
    static final int MIN_CONTRAST = 25;
    /** 새 그림의 가장자리 픽셀이 원래 테두리 밝기 + 이 값보다 어두우면 테두리로 친다 */
    static final int TOLERANCE = 40;
    /** 새 그림의 가장자리 가운데 테두리인 비율이 이보다 낮으면 테두리를 잃은 것 */
    static final double MIN_SHARE = 0.6;

    private SpriteOutline() {}

    /** 원래 그림(reference)에는 테두리가 있는데 새 그림(result)에는 없으면 true. 판단할 수 없으면 false */
    static boolean lost(byte[] reference, byte[] result) {
        int[][] ref = lumas(reference), out = lumas(result);
        if (ref == null || out == null || ref[0].length == 0 || ref[1].length == 0 || out[0].length == 0) return false;
        int refEdge = median(ref[0]), refInside = median(ref[1]);
        if (refInside - refEdge < MIN_CONTRAST) return false;   // 원래부터 테두리가 뚜렷하지 않은 그림 (검은 몸 등)
        return share(out[0], refEdge + TOLERANCE) < MIN_SHARE;
    }

    /** 가장자리 픽셀 가운데 밝기가 limit 이하인 비율 */
    static double share(int[] edgeLumas, int limit) {
        if (edgeLumas.length == 0) return 0;
        int dark = 0;
        for (int l : edgeLumas) if (l <= limit) dark++;
        return (double) dark / edgeLumas.length;
    }

    /** [가장자리 픽셀들의 밝기, 안쪽 픽셀들의 밝기]. 이미지가 아니면 null */
    static int[][] lumas(byte[] png) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            return lumas(img.getRGB(0, 0, w, h, null, 0, w), w, h);
        } catch (Exception e) {
            return null;
        }
    }

    static int[][] lumas(int[] px, int w, int h) {
        int[] edge = new int[px.length], inside = new int[px.length];
        int ne = 0, ni = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = px[y * w + x];
                if ((p >>> 24) < VISIBLE) continue;
                boolean onEdge = x == 0 || y == 0 || x == w - 1 || y == h - 1
                        || (px[y * w + x - 1] >>> 24) < VISIBLE || (px[y * w + x + 1] >>> 24) < VISIBLE
                        || (px[(y - 1) * w + x] >>> 24) < VISIBLE || (px[(y + 1) * w + x] >>> 24) < VISIBLE;
                int luma = (((p >> 16) & 255) * 30 + ((p >> 8) & 255) * 59 + (p & 255) * 11) / 100;
                if (onEdge) edge[ne++] = luma; else inside[ni++] = luma;
            }
        }
        return new int[][]{Arrays.copyOf(edge, ne), Arrays.copyOf(inside, ni)};
    }

    private static int median(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
