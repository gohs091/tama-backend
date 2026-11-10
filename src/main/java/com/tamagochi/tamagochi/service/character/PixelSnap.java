package com.tamagochi.tamagochi.service.character;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 격자 다시 찍기: 새 이미지 모델(sunburst)은 "도트처럼 칠한 그림"을 그려 한 칸 크기가 잘고 들쭉날쭉하다.
 * 그림을 grid×grid 칸으로 나눠 칸마다 가장 많은 색 하나로 채워 진짜 도트로 만든다 (크기는 원본 그대로).
 * 칸 가장자리는 번짐이 많아 가운데 60%만 본다. 투명/불투명은 다수결.
 * 64칸이 디테일과 도트감의 균형이 가장 좋았다(48칸은 얇은 외곽선이 끊긴다, 2026-11-10 앱 화면 비교).
 */
final class PixelSnap {

    static final int GRID = 64;

    private PixelSnap() {}

    static byte[] snap(byte[] png, int grid) {
        if (png == null || png.length == 0) return png;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
            if (src == null) return png;
            int w = src.getWidth(), h = src.getHeight();
            double cw = (double) w / grid, ch = (double) h / grid;
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int gy = 0; gy < grid; gy++) {
                for (int gx = 0; gx < grid; gx++) {
                    int color = cellColor(src, gx * cw, gy * ch, cw, ch);
                    int x0 = (int) Math.round(gx * cw), x1 = (int) Math.round((gx + 1) * cw);
                    int y0 = (int) Math.round(gy * ch), y1 = (int) Math.round((gy + 1) * ch);
                    for (int y = y0; y < Math.min(y1, h); y++) for (int x = x0; x < Math.min(x1, w); x++) out.setRGB(x, y, color);
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            return png;
        }
    }

    /** 칸 가운데 60%에서 불투명이 절반 이상이면, 비슷한 색 묶음 중 가장 많은 묶음의 평균색. 아니면 투명 */
    private static int cellColor(BufferedImage im, double left, double top, double cw, double ch) {
        int x0 = (int) (left + cw * 0.2), x1 = (int) (left + cw * 0.8), y0 = (int) (top + ch * 0.2), y1 = (int) (top + ch * 0.8);
        int maxX = im.getWidth() - 1, maxY = im.getHeight() - 1;
        Map<Integer, long[]> groups = new HashMap<>(); // 묶음 → [개수, r합, g합, b합]
        int total = 0, opaque = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int p = im.getRGB(Math.min(x, maxX), Math.min(y, maxY));
                total++;
                if ((p >>> 24) < 128) continue;
                opaque++;
                int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
                long[] acc = groups.computeIfAbsent((r / 12) << 16 | (g / 12) << 8 | (b / 12), k -> new long[4]);
                acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b;
            }
        }
        if (opaque * 2 < total || groups.isEmpty()) return 0;
        long[] best = groups.values().stream().max((a, b) -> Long.compare(a[0], b[0])).orElseThrow();
        return 0xFF000000 | (int) (best[1] / best[0]) << 16 | (int) (best[2] / best[0]) << 8 | (int) (best[3] / best[0]);
    }
}
