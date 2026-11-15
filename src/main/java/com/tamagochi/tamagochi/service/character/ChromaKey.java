package com.tamagochi.tamagochi.service.character;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * 단색 배경으로 받은 그림에서 배경색만 투명하게 바꾼다.
 * 모델에게 투명 배경을 직접 달라고 하면 어디를 투명으로 둘지를 모델이 정해서, 흰 배·창문·눈의 반짝임 같은 몸 안쪽을
 * 배경처럼 비워 버린다 (프롬프트로 막아도 가끔 나온다). 대신 배경을 캐릭터에 쓰지 않는 한 가지 색으로 칠해 달라고 하면
 * 모델이 모든 칸을 색으로 칠하므로 몸 안에 투명이 생길 수 없고, 꼬리 고리 안쪽·다리 사이 같은 진짜 빈 곳은 배경색으로 칠해져 온다.
 */
final class ChromaKey {

    /** 배경으로 칠하게 할 색. 유저 그림에 쓰인 색과 가장 먼 것을 고른다 (분홍 끄적이를 자홍색 배경에 그리면 몸까지 지워진다) */
    enum Key {
        MAGENTA(0xFF00FF, "magenta (#FF00FF)", "magenta, hot pink or purple", "pink", "purple", "violet", "rainbow"),
        GREEN(0x00FF00, "green (#00FF00)", "bright green or lime", "green", "mint", "rainbow");

        final int rgb;
        final String name;
        final String banned;
        /** 진영 색 설명에 이 낱말이 있으면 몸이 이 배경색과 비슷하게 칠해진다 */
        final String[] factionWords;

        Key(int rgb, String name, String banned, String... factionWords) {
            this.rgb = rgb;
            this.name = name;
            this.banned = banned;
            this.factionWords = factionWords;
        }
    }

    /** 배경색과 이 거리(RGB) 안쪽인 칸을 배경으로 본다 */
    private static final int TOLERANCE = 100;
    /** 가장자리 칸의 이 비율 이상이 한 색이어야 단색 배경으로 본다 */
    private static final double MIN_BORDER_SHARE = 0.9;
    /** 지우고 남은 그림이 캔버스의 이 비율보다 작으면 몸까지 지운 것으로 본다 (끄적이는 캔버스의 약 70% 폭으로 그려진다) */
    static final double MIN_BODY_SHARE = 0.05;
    /**
     * 몸으로 둘러싸인 채 지워진 곳이 남은 몸의 이 비율을 넘으면 몸 안쪽 색을 지운 것으로 본다.
     * 꼬리 고리·바퀴 사이 같은 진짜 빈 곳은 몸의 2~3%였다 (낙서는 몸 색이 한두 가지라, 그 색이 배경색과 겹치면 한꺼번에 크게 지워진다)
     */
    static final double MAX_ENCLOSED_SHARE = 0.12;
    /** 유저 그림에서 이 비율 이상 쓰인 색만 본다 (JPEG 잡티 제외) */
    private static final double MIN_DOODLE_SHARE = 0.005;

    private ChromaKey() {}

    /** 프롬프트의 "transparent background" 자리에 단색 배경 지시를 넣는다 */
    static String prompt(String prompt, Key key) {
        return prompt.replace("transparent background",
                "a perfectly flat, solid pure " + key.name + " background covering the whole canvas edge to edge with no gradient, " +
                "no shadow and no texture, and the same flat " + key.name.substring(0, key.name.indexOf(' ')) +
                " showing through every open gap between body parts (between legs, between wheels, inside a curled tail); " +
                "never use " + key.banned + " anywhere on the character itself");
    }

    /** 유저 그림에 쓰인 색과 가장 먼 배경색. 그림을 읽지 못하면 자홍색 */
    static Key pick(byte[] doodle) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(doodle));
            if (src == null) return Key.MAGENTA;
            int w = src.getWidth(), h = src.getHeight();
            return pick(src.getRGB(0, 0, w, h, null, 0, w));
        } catch (Exception e) {
            return Key.MAGENTA;
        }
    }

    /**
     * 유저 그림과 진영 색 모두와 겹치지 않는 배경색. 진영 색이 두 배경색과 다 겹치면(분홍+민트, 무지개) null —
     * 그때는 단색 배경으로 받지 않는다 (몸이 배경색으로 칠해져 지워지고 다시 그리느라 비용이 두 배가 된다)
     */
    static Key pick(byte[] doodle, String factionColors) {
        String colors = factionColors == null ? "" : factionColors.toLowerCase();
        java.util.List<Key> allowed = new java.util.ArrayList<>();
        for (Key key : Key.values()) {
            boolean clash = false;
            for (String word : key.factionWords) if (colors.contains(word)) clash = true;
            if (!clash) allowed.add(key);
        }
        if (allowed.isEmpty()) return null;
        if (allowed.size() == 1) return allowed.get(0);
        return pick(doodle);
    }

    static Key pick(int[] px) {
        // 색을 굵게 묶어 세고(채널마다 8단계), 충분히 쓰인 색마다 각 배경색까지의 거리를 본다
        int[] counts = new int[512];
        for (int p : px) counts[(((p >> 16) & 255) >> 5) << 6 | (((p >> 8) & 255) >> 5) << 3 | ((p & 255) >> 5)]++;
        Key best = Key.MAGENTA;
        long bestDistance = -1;
        for (Key key : Key.values()) {
            long nearest = Long.MAX_VALUE;
            for (int bin = 0; bin < counts.length; bin++) {
                if (counts[bin] < px.length * MIN_DOODLE_SHARE) continue;
                int r = ((bin >> 6) & 7) * 32 + 16, g = ((bin >> 3) & 7) * 32 + 16, b = (bin & 7) * 32 + 16;
                // 흰 종이·검은 선·회색처럼 색기가 없는 색은 선명한 배경색과 헷갈릴 일이 없어 보지 않는다
                if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 64) continue;
                int dr = r - ((key.rgb >> 16) & 255), dg = g - ((key.rgb >> 8) & 255), db = b - (key.rgb & 255);
                nearest = Math.min(nearest, (long) dr * dr + dg * dg + db * db);
            }
            if (nearest > bestDistance) { bestDistance = nearest; best = key; }
        }
        return best;
    }

    /** 배경을 투명하게 바꾼 PNG. 가장자리가 단색이 아니거나(모델이 지시를 어김) 몸까지 지워졌거나 읽을 수 없으면 null */
    static byte[] cut(byte[] png) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            int[] px = src.getRGB(0, 0, w, h, null, 0, w);
            if (!cut(px, w, h)) return null;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** ARGB 픽셀 배열에서 배경색 칸을 그 자리에서 투명하게 바꾼다. 가장자리가 단색이 아니거나 몸까지 지워졌으면 false */
    static boolean cut(int[] px, int w, int h) {
        int[] border = new int[2 * w + 2 * h];
        int n = 0;
        for (int x = 0; x < w; x++) { border[n++] = px[x]; border[n++] = px[(h - 1) * w + x]; }
        for (int y = 0; y < h; y++) { border[n++] = px[y * w]; border[n++] = px[y * w + w - 1]; }
        int r = median(border, 16), g = median(border, 8), b = median(border, 0);
        int near = 0;
        for (int p : border) if (isBackground(p, r, g, b)) near++;
        if (near < border.length * MIN_BORDER_SHARE) return false;
        for (int i = 0; i < px.length; i++) if (isBackground(px[i], r, g, b)) px[i] = 0;
        return !overCut(px, w, h);
    }

    /**
     * 몸까지 지웠는지 본다: 남은 그림이 거의 없거나, 몸으로 둘러싸인 채 지워진 곳이 몸에 비해 너무 넓으면 그렇다.
     * (테두리 안쪽이 배경색과 비슷한 색으로 칠해져 있었다는 뜻이다)
     */
    static boolean overCut(int[] px, int w, int h) {
        int total = w * h;
        boolean[] outside = new boolean[total];
        int[] queue = new int[total];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) { tail = seed(px, outside, queue, tail, x); tail = seed(px, outside, queue, tail, (h - 1) * w + x); }
        for (int y = 0; y < h; y++) { tail = seed(px, outside, queue, tail, y * w); tail = seed(px, outside, queue, tail, y * w + w - 1); }
        while (head < tail) {
            int i = queue[head++];
            int x = i % w;
            if (x > 0) tail = seed(px, outside, queue, tail, i - 1);
            if (x < w - 1) tail = seed(px, outside, queue, tail, i + 1);
            if (i >= w) tail = seed(px, outside, queue, tail, i - w);
            if (i < total - w) tail = seed(px, outside, queue, tail, i + w);
        }
        int body = 0, enclosed = 0;
        for (int i = 0; i < total; i++) {
            if (px[i] != 0) body++;
            else if (!outside[i]) enclosed++;
        }
        return body < total * MIN_BODY_SHARE || enclosed > body * MAX_ENCLOSED_SHARE;
    }

    private static int seed(int[] px, boolean[] outside, int[] queue, int tail, int i) {
        if (outside[i] || px[i] != 0) return tail;
        outside[i] = true;
        queue[tail] = i;
        return tail + 1;
    }

    private static boolean isBackground(int p, int r, int g, int b) {
        int dr = ((p >> 16) & 255) - r, dg = ((p >> 8) & 255) - g, db = (p & 255) - b;
        return dr * dr + dg * dg + db * db <= TOLERANCE * TOLERANCE;
    }

    private static int median(int[] px, int shift) {
        int[] v = new int[px.length];
        for (int i = 0; i < px.length; i++) v[i] = (px[i] >> shift) & 255;
        Arrays.sort(v);
        return v[v.length / 2];
    }
}
