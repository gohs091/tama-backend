package com.tamagochi.tamagochi.service.character;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 모델이 그린 그림을 정해진 격자로 다시 찍는다.
 * 모델마다·그림마다 도트 크기가 제각각(잘게, 굵게, 아예 매끈하게)이라 모든 그림을 같은 칸 크기로 맞춘다.
 *  - 캔버스를 GRID×GRID 칸으로 나눠 칸마다 가장 많은 색 하나로 칠한다 (반 이상 투명하면 투명)
 *  - 색 수를 MAX_COLORS 이하로 줄여 자글자글한 점을 없앤다
 * 실패하면 null (호출하는 쪽이 원본을 쓴다).
 */
final class PixelSnap {

    /** 한 변의 칸 수. 그림이 캔버스의 약 70%를 채우므로 캐릭터 폭은 약 45칸 */
    static final int GRID = 64;
    static final int MAX_COLORS = 16;

    private PixelSnap() {}

    static byte[] snap(byte[] png) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            if (w < GRID * 4 || h < GRID * 4) return png;   // 이미 작은 그림(아이콘·테스트)은 그대로
            double cw = (double) w / GRID, ch = (double) h / GRID;

            int[] cells = new int[GRID * GRID];   // ARGB, 0 = 투명
            for (int gy = 0; gy < GRID; gy++) {
                for (int gx = 0; gx < GRID; gx++) {
                    int x0 = (int) Math.round(gx * cw), x1 = (int) Math.round((gx + 1) * cw);
                    int y0 = (int) Math.round(gy * ch), y1 = (int) Math.round((gy + 1) * ch);
                    Map<Integer, int[]> bins = new HashMap<>();   // 4비트로 묶은 색 → [개수, r합, g합, b합]
                    int opaque = 0, total = 0;
                    for (int y = y0; y < y1; y++) {
                        for (int x = x0; x < x1; x++) {
                            total++;
                            int p = src.getRGB(x, y);
                            if ((p >>> 24) < 128) continue;
                            opaque++;
                            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                            int key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                            int[] s = bins.computeIfAbsent(key, k -> new int[4]);
                            s[0]++; s[1] += r; s[2] += g; s[3] += b;
                        }
                    }
                    if (total == 0 || opaque * 2 < total) continue;
                    int[] best = null;
                    for (int[] s : bins.values()) if (best == null || s[0] > best[0]) best = s;
                    cells[gy * GRID + gx] = 0xFF000000 | ((best[1] / best[0]) << 16) | ((best[2] / best[0]) << 8) | (best[3] / best[0]);
                }
            }

            reducePalette(cells);

            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                int gy = Math.min(GRID - 1, (int) (y / ch));
                for (int x = 0; x < w; x++) {
                    out.setRGB(x, y, cells[gy * GRID + Math.min(GRID - 1, (int) (x / cw))]);
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** 가장 가까운 두 색을 (칸 수로 가중 평균해) 합치기를 MAX_COLORS 이하가 될 때까지 반복한다 */
    private static void reducePalette(int[] cells) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int c : cells) if (c != 0) counts.merge(c, 1, Integer::sum);
        List<double[]> pal = new ArrayList<>();   // [r, g, b, 개수]
        List<List<Integer>> members = new ArrayList<>();
        counts.forEach((c, n) -> {
            pal.add(new double[]{(c >> 16) & 255, (c >> 8) & 255, c & 255, n});
            List<Integer> m = new ArrayList<>(); m.add(c); members.add(m);
        });
        while (pal.size() > MAX_COLORS) {
            int bi = 0, bj = 1; double bd = Double.MAX_VALUE;
            for (int i = 0; i < pal.size(); i++) {
                for (int j = i + 1; j < pal.size(); j++) {
                    double[] a = pal.get(i), b = pal.get(j);
                    double dr = a[0] - b[0], dg = a[1] - b[1], db = a[2] - b[2];
                    // 색 거리만 본다 (개수로 가중하면 눈·번개 같은 작은 포인트 색이 먼저 사라진다)
                    double d = dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11;
                    if (d < bd) { bd = d; bi = i; bj = j; }
                }
            }
            double[] a = pal.get(bi), b = pal.get(bj);
            double n = a[3] + b[3];
            // 많은 쪽 색을 유지한다 (평균하면 원래 없던 탁한 색이 생긴다)
            double[] keep = a[3] >= b[3] ? a : b;
            pal.set(bi, new double[]{keep[0], keep[1], keep[2], n});
            members.get(bi).addAll(members.get(bj));
            pal.remove(bj); members.remove(bj);
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < pal.size(); i++) {
            double[] p = pal.get(i);
            int c = 0xFF000000 | ((int) p[0] << 16) | ((int) p[1] << 8) | (int) p[2];
            for (int m : members.get(i)) map.put(m, c);
        }
        for (int i = 0; i < cells.length; i++) if (cells[i] != 0) cells[i] = map.get(cells[i]);
    }
}
