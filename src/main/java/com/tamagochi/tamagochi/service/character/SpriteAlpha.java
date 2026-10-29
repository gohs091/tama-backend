package com.tamagochi.tamagochi.service.character;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;

/**
 * 생성된 스프라이트의 알파를 정리한다.
 * - 알파 128 미만은 투명, 이상은 불투명으로 나눈다 (모델이 그리는 흐린 그림자·번짐·반투명 가장자리 제거).
 * - 몸 안쪽(테두리로 둘러싸인 곳)에 생긴 투명·반투명 구멍은 투명으로 두지 않고 채운다.
 *   그대로 두면 배경이 비쳐서, 배경이 바뀔 때마다 머리·날개·꼬리 색이 달라 보인다.
 * - 다리 사이처럼 바닥에 닿은 빈 공간이나 꼬리 고리처럼 거의 완전히 투명한 공간은 일부러 비어 있는 곳이라 채우지 않는다.
 */
final class SpriteAlpha {

    private static final int VISIBLE = 128;
    /** 알파가 이보다 낮은 구멍 픽셀은 색 정보를 믿을 수 없어 가까운 불투명 색을 쓴다 */
    private static final int COLOR_TRUSTED = 40;
    /**
     * 구멍인지 일부러 빈 공간인지 가르는 기준. 모델이 몸을 그리다 생긴 구멍에는 옅은 알파(색이 남은 픽셀)가 군데군데 있고,
     * 다리 사이나 꼬리 고리처럼 일부러 비운 공간은 거의 완전히 투명하다 (실제 생성 이미지 30여 곳을 재서 정한 값).
     */
    private static final int FAINT_ALPHA = 8;
    private static final double MIN_FAINT_SHARE = 0.075;
    private static final int MIN_MEAN_ALPHA = 11;
    /** 몸의 아래쪽 이 비율 안으로 내려간 빈 공간은 다리 사이 같은 진짜 빈 공간으로 본다 */
    private static final double FLOOR_ZONE = 0.15;

    private SpriteAlpha() {}

    /** 정리한 PNG를 돌려준다. 이미지가 아니거나 실패하면 null (호출한 쪽이 원본을 쓴다) */
    static byte[] clean(byte[] png) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            int[] px = src.getRGB(0, 0, w, h, null, 0, w);
            int[] out = clean(px, w, h);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, out, 0, w);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** ARGB 픽셀 배열을 정리한 새 배열 */
    static int[] clean(int[] px, int w, int h) {
        int n = w * h;
        boolean[] visible = new boolean[n];
        int top = h, bottom = -1;
        for (int i = 0; i < n; i++) {
            if ((px[i] >>> 24) >= VISIBLE) {
                visible[i] = true;
                int y = i / w;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }
        int[] out = new int[n];
        if (bottom < 0) return out; // 보이는 픽셀이 없다

        boolean[] outside = floodOutside(visible, w, h);
        int floorLine = bottom - (int) ((bottom - top) * FLOOR_ZONE);

        // 안쪽의 안 보이는 픽셀을 덩어리로 묶고, 바닥 쪽으로 내려가지 않은 덩어리만 채운다
        boolean[] fill = new boolean[n];
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int[] members = new int[n];
        for (int s = 0; s < n; s++) {
            if (visible[s] || outside[s] || seen[s]) continue;
            int count = 0, lowest = 0, faint = 0;
            long alphaSum = 0;
            seen[s] = true;
            q.add(s);
            while (!q.isEmpty()) {
                int i = q.poll();
                members[count++] = i;
                int y = i / w;
                if (y > lowest) lowest = y;
                int a = px[i] >>> 24;
                alphaSum += a;
                if (a >= FAINT_ALPHA) faint++;
                for (int j : neighbors(i, w, h)) {
                    if (j >= 0 && !visible[j] && !outside[j] && !seen[j]) { seen[j] = true; q.add(j); }
                }
            }
            boolean looksLikeHole = (double) faint / count >= MIN_FAINT_SHARE && alphaSum / count >= MIN_MEAN_ALPHA;
            if (lowest < floorLine && looksLikeHole) for (int k = 0; k < count; k++) fill[members[k]] = true;
        }

        // 채울 픽셀의 색: 알파가 어느 정도 있으면 자기 색, 거의 없으면 가까운 불투명 색
        int[] rgb = new int[n];
        boolean[] colored = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (visible[i] || (fill[i] && (px[i] >>> 24) >= COLOR_TRUSTED)) { rgb[i] = px[i] & 0xFFFFFF; colored[i] = true; }
        }
        ArrayDeque<Integer> bfs = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (colored[i]) bfs.add(i);
        while (!bfs.isEmpty()) {
            int i = bfs.poll();
            for (int j : neighbors(i, w, h)) {
                if (j >= 0 && fill[j] && !colored[j]) { rgb[j] = rgb[i]; colored[j] = true; bfs.add(j); }
            }
        }
        for (int i = 0; i < n; i++) {
            if (visible[i] || (fill[i] && colored[i])) out[i] = 0xFF000000 | rgb[i];
        }
        return out;
    }

    /** 가장자리에서 이어진 안 보이는 영역 (몸 바깥) */
    private static boolean[] floodOutside(boolean[] visible, int w, int h) {
        boolean[] outside = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) { seed(x, 0, w, visible, outside, q); seed(x, h - 1, w, visible, outside, q); }
        for (int y = 0; y < h; y++) { seed(0, y, w, visible, outside, q); seed(w - 1, y, w, visible, outside, q); }
        while (!q.isEmpty()) {
            int i = q.poll();
            for (int j : neighbors(i, w, h)) {
                if (j >= 0 && !visible[j] && !outside[j]) { outside[j] = true; q.add(j); }
            }
        }
        return outside;
    }

    private static void seed(int x, int y, int w, boolean[] visible, boolean[] outside, ArrayDeque<Integer> q) {
        int i = y * w + x;
        if (!visible[i] && !outside[i]) { outside[i] = true; q.add(i); }
    }

    /** 위·아래·왼쪽·오른쪽 이웃 (없으면 -1) */
    private static int[] neighbors(int i, int w, int h) {
        int x = i % w, y = i / w;
        return new int[]{
                x > 0 ? i - 1 : -1,
                x < w - 1 ? i + 1 : -1,
                y > 0 ? i - w : -1,
                y < h - 1 ? i + w : -1};
    }
}
