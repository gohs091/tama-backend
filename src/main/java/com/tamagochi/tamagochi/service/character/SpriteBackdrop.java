package com.tamagochi.tamagochi.service.character;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 투명 배경 그림에 옅은 단색 배경을 깐다 (모델에 "지금 모습"으로 보내는 입력용).
 * 투명 배경을 그대로 보내면 모델은 그 자리를 검은색으로 본다. 그러면 어두운 테두리가 배경에 묻혀
 * 모델이 테두리를 배경으로 여기고, 새로 그릴 때 테두리·감은 눈·입 선을 투명하게 비워 버린다
 * (2026-11-12 자는 모습에서 확인: 투명 그대로 3장 모두 테두리 없음, 회색 배경을 깔면 3장 모두 테두리 유지).
 * 배경색이 몸 색과 비슷하면 이번에는 몸이 배경에 묻히므로, 끄적이의 색들과 충분히 떨어진 색을 고른다.
 */
final class SpriteBackdrop {

    /** 먼저 고르는 순서. 옅은 회색이 기본이고, 끄적이 색과 가까우면 다음 색으로 넘어간다 */
    static final int[] CANDIDATES = {0xD9D9D9, 0xBFD9EC, 0xD9C7B0, 0x9FB8A8, 0x6F7F8F};
    /** 끄적이의 모든 주요 색과 이만큼(RGB 거리) 떨어져 있으면 그 배경색을 쓴다 */
    static final double FAR_ENOUGH = 60;
    /** 불투명 픽셀의 이 비율 이상을 차지하는 색만 끄적이의 주요 색으로 본다 */
    private static final double MAJOR_SHARE = 0.01;
    private static final int VISIBLE = 128;

    private SpriteBackdrop() {}

    /** 배경을 깐 PNG. 투명한 곳이 없거나(이미 배경이 있는 그림) 읽을 수 없으면 null (호출한 쪽이 원본을 쓴다) */
    static byte[] flatten(byte[] image) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(image));
            if (src == null || !src.getColorModel().hasAlpha()) return null;
            int w = src.getWidth(), h = src.getHeight();
            int[] px = src.getRGB(0, 0, w, h, null, 0, w);
            boolean anyClear = false;
            for (int p : px) if ((p >>> 24) < VISIBLE) { anyClear = true; break; }
            if (!anyClear) return null;
            int backdrop = 0xFF000000 | pick(px);
            // 반투명 가장자리가 배경색과 섞여 얼룩지지 않게, 보이는 픽셀만 남기고 나머지는 배경색으로 채운다
            for (int i = 0; i < px.length; i++) px[i] = (px[i] >>> 24) >= VISIBLE ? 0xFF000000 | px[i] : backdrop;
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            out.setRGB(0, 0, w, h, px, 0, w);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** 끄적이의 주요 색들과 가장 덜 헷갈리는 배경색(RGB) */
    static int pick(int[] argb) {
        Map<Integer, int[]> bins = new HashMap<>();   // 4비트로 묶은 색 → [개수, r합, g합, b합]
        int opaque = 0;
        for (int p : argb) {
            if ((p >>> 24) < VISIBLE) continue;
            opaque++;
            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            int[] s = bins.computeIfAbsent(((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4), k -> new int[4]);
            s[0]++; s[1] += r; s[2] += g; s[3] += b;
        }
        if (opaque == 0) return CANDIDATES[0];
        int best = CANDIDATES[0];
        double bestGap = -1;
        for (int c : CANDIDATES) {
            int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
            double gap = Double.MAX_VALUE;
            for (int[] s : bins.values()) {
                if (s[0] < opaque * MAJOR_SHARE) continue;
                double dr = cr - (double) s[1] / s[0], dg = cg - (double) s[2] / s[0], db = cb - (double) s[3] / s[0];
                gap = Math.min(gap, Math.sqrt(dr * dr + dg * dg + db * db));
            }
            if (gap >= FAR_ENOUGH) return c;
            if (gap > bestGap) { bestGap = gap; best = c; }
        }
        return best;
    }
}
