package com.tamagochi.tamagochi.service.character;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 몸 안에 남은 투명 구멍(그림 가장자리와 이어지지 않은 투명한 곳)을 흰색으로 채운다.
 * 모델이 흰 배·흰 눈·창문을 배경처럼 투명하게 그리면 그 자리로 배경이 비친다. 알파 정리(SpriteAlpha)는 색이 남은 구멍만 채우고
 * 완전히 투명한 구멍은 남겨 두는데, 전에는 그 나머지를 앱이 흰색으로 채웠다. 그런데 서버(S3)에 남는 그림은 채우기 전 그림이라,
 * 서버 그림을 받아 쓰는 곳(폰 저장소가 지워져 되살린 폰, 다른 기기)에서는 구멍이 그대로 보였다. 그래서 서버에서 채워서 남긴다.
 * 몸 안 틈이 진짜 배경인 그림(v5·v6 스케치: 고리·망토 사이)에는 쓰지 않는다.
 */
public final class SpriteHoles {

    private static final int VISIBLE = 128;

    private SpriteHoles() {}

    /** 구멍을 채운 PNG. 채울 곳이 없거나 읽을 수 없으면 받은 그림 그대로 */
    public static byte[] fillWhite(byte[] png) {
        if (png == null || png.length == 0) return png;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
            if (src == null) return png;
            int w = src.getWidth(), h = src.getHeight();
            int[] px = src.getRGB(0, 0, w, h, null, 0, w);
            if (!fillWhite(px, w, h)) return png;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return png;
        }
    }

    /** ARGB 픽셀 배열의 구멍을 그 자리에서 채운다. 채운 곳이 있으면 true */
    static boolean fillWhite(int[] px, int w, int h) {
        int n = w * h;
        // 가장자리에서 이어진 투명 영역 = 몸 바깥
        boolean[] outside = new boolean[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) {
            tail = seed(px, outside, queue, tail, x);
            tail = seed(px, outside, queue, tail, (h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            tail = seed(px, outside, queue, tail, y * w);
            tail = seed(px, outside, queue, tail, y * w + w - 1);
        }
        while (head < tail) {
            int i = queue[head++];
            int x = i % w;
            if (x > 0) tail = seed(px, outside, queue, tail, i - 1);
            if (x < w - 1) tail = seed(px, outside, queue, tail, i + 1);
            if (i >= w) tail = seed(px, outside, queue, tail, i - w);
            if (i < n - w) tail = seed(px, outside, queue, tail, i + w);
        }
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            if (outside[i] || (px[i] >>> 24) >= VISIBLE) continue;
            px[i] = 0xFFFFFFFF;
            changed = true;
        }
        return changed;
    }

    private static int seed(int[] px, boolean[] outside, int[] queue, int tail, int i) {
        if (outside[i] || (px[i] >>> 24) >= VISIBLE) return tail;
        outside[i] = true;
        queue[tail] = i;
        return tail + 1;
    }
}
