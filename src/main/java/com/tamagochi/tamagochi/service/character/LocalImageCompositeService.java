package com.tamagochi.tamagochi.service.character;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;

/**
 * 외부 AI API 없이 Java AWT만으로 손그림 합성·필터링을 수행하는 모듈.
 * 파이프라인: 실루엣 추출 → 팩션 배경 합성 → 빈티지 필터 → base64 반환
 */
@Service
@Slf4j
public class LocalImageCompositeService {

    private static final int SIZE = 512;

    public String generate(String sketchBase64, String faction) throws Exception {
        byte[] sketchBytes = decodeBase64(sketchBase64);
        BufferedImage sketch = ImageIO.read(new ByteArrayInputStream(sketchBytes));
        if (sketch == null) {
            throw new IllegalArgumentException("유효하지 않은 이미지 데이터입니다.");
        }

        BufferedImage silhouette = extractSilhouette(sketch);
        BufferedImage background = generateFactionBackground(faction);
        BufferedImage composed   = composite(background, silhouette);
        BufferedImage result     = applyVintageFilter(composed);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(result, "PNG", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // ----------------------------------------------------------------
    // Step 1: 밝은 픽셀(배경)을 투명하게 처리, 어두운 픽셀(선)은 유지
    // ----------------------------------------------------------------
    private BufferedImage extractSilhouette(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int srcAlpha = (argb >> 24) & 0xFF;
                // 원본에 알파가 있으면 그 알파를 사용, 없으면 밝기로 판단
                if (srcAlpha < 128) {
                    out.setRGB(x, y, 0x00000000);
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int brightness = (r * 299 + g * 587 + b * 114) / 1000;

                if (brightness < 200) {
                    // 어두운 픽셀: 선으로 간주하여 그대로 유지
                    out.setRGB(x, y, (255 << 24) | (r << 16) | (g << 8) | b);
                } else {
                    // 밝은 픽셀: 배경이므로 투명화
                    out.setRGB(x, y, 0x00000000);
                }
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // Step 2: 팩션 배경(뒤) + 실루엣(앞) 합성
    // ----------------------------------------------------------------
    private BufferedImage composite(BufferedImage background, BufferedImage silhouette) {
        BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 배경 그리기
        g.drawImage(background, 0, 0, SIZE, SIZE, null);

        // 실루엣 중앙 배치 (75% 크기로 스케일)
        int maxDim = (int) (SIZE * 0.75);
        float scaleRatio = Math.min((float) maxDim / silhouette.getWidth(),
                                    (float) maxDim / silhouette.getHeight());
        int sw = (int) (silhouette.getWidth() * scaleRatio);
        int sh = (int) (silhouette.getHeight() * scaleRatio);
        int ox = (SIZE - sw) / 2;
        int oy = (SIZE - sh) / 2;

        g.drawImage(silhouette, ox, oy, sw, sh, null);
        g.dispose();
        return result;
    }

    // ----------------------------------------------------------------
    // Step 3: 빈티지 필터 (세피아 50% + 노이즈 + 비네트 + 소프트 블러)
    // ----------------------------------------------------------------
    private BufferedImage applyVintageFilter(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rng = new Random(12345L);

        double cx = w / 2.0;
        double cy = h / 2.0;
        double maxDist = Math.sqrt(cx * cx + cy * cy);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // 세피아 50% 믹스
                int sr = clamp((int) (r * 0.393 + g * 0.769 + b * 0.189));
                int sg = clamp((int) (r * 0.349 + g * 0.686 + b * 0.168));
                int sb = clamp((int) (r * 0.272 + g * 0.534 + b * 0.131));
                r = (r + sr) / 2;
                g = (g + sg) / 2;
                b = (b + sb) / 2;

                // 노이즈 (±10)
                int noise = (int) (rng.nextGaussian() * 5);
                r = clamp(r + noise);
                g = clamp(g + noise);
                b = clamp(b + noise);

                // 비네트 (가장자리 어둡게)
                double dx = x - cx;
                double dy = y - cy;
                double dist = Math.sqrt(dx * dx + dy * dy) / maxDist;
                double vignette = 1.0 - dist * 0.45;
                r = clamp((int) (r * vignette));
                g = clamp((int) (g * vignette));
                b = clamp((int) (b * vignette));

                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        // 소프트 가우시안 블러 (3×3)
        float[] kernel = {
            1/16f, 2/16f, 1/16f,
            2/16f, 4/16f, 2/16f,
            1/16f, 2/16f, 1/16f
        };
        ConvolveOp blur = new ConvolveOp(new Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurred = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        blur.filter(out, blurred);
        return blurred;
    }

    // ----------------------------------------------------------------
    // 팩션별 배경 생성 (Java2D 그라디언트 + 장식 도형)
    // ----------------------------------------------------------------
    private BufferedImage generateFactionBackground(String faction) {
        BufferedImage bg = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        FactionTheme t = getTheme(faction);
        GradientPaint gp = new GradientPaint(0, 0, t.top, SIZE, SIZE, t.bottom);
        g.setPaint(gp);
        g.fillRect(0, 0, SIZE, SIZE);

        drawDecorations(g, t, faction);
        g.dispose();
        return bg;
    }

    private void drawDecorations(Graphics2D g, FactionTheme t, String faction) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(t.accent);

        switch (faction) {
            case "nature-spirits"     -> drawCircles(g, 18, 20, 60);
            case "deep-savers"        -> drawBubbles(g, 22, 15, 55);
            case "nightmare-soldiers" -> drawStars(g, 28, 6, 18);
            case "wind-guardians"     -> drawClouds(g, 5);
            case "metal-empire"       -> drawGears(g, 7, 35, 65);
            case "virus-busters"      -> drawHexagons(g, 14, 30);
            case "dragons-roar"       -> drawFlames(g, 20, 30, 70);
            case "jungle-troopers"    -> drawLeaves(g, 15);
            case "dark-area"          -> drawStars(g, 40, 4, 12);
            default                   -> drawCircles(g, 12, 15, 50);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    private void drawCircles(Graphics2D g, int count, int minR, int maxR) {
        Random rng = new Random(77L);
        for (int i = 0; i < count; i++) {
            int r = minR + rng.nextInt(maxR - minR);
            int x = rng.nextInt(SIZE - r * 2);
            int y = rng.nextInt(SIZE - r * 2);
            g.fillOval(x, y, r, r);
        }
    }

    private void drawBubbles(Graphics2D g, int count, int minR, int maxR) {
        Random rng = new Random(88L);
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < count; i++) {
            int r = minR + rng.nextInt(maxR - minR);
            int x = rng.nextInt(SIZE - r * 2);
            int y = rng.nextInt(SIZE - r * 2);
            g.drawOval(x, y, r, r);
        }
    }

    private void drawStars(Graphics2D g, int count, int minR, int maxR) {
        Random rng = new Random(99L);
        for (int i = 0; i < count; i++) {
            int r = minR + rng.nextInt(maxR - minR);
            int x = rng.nextInt(SIZE);
            int y = rng.nextInt(SIZE);
            drawStar(g, x, y, r / 2, r);
        }
    }

    private void drawStar(Graphics2D g, int cx, int cy, int inner, int outer) {
        int points = 5;
        int[] xs = new int[points * 2];
        int[] ys = new int[points * 2];
        for (int i = 0; i < points * 2; i++) {
            double angle = Math.PI / points * i - Math.PI / 2;
            int radius = (i % 2 == 0) ? outer : inner;
            xs[i] = cx + (int) (Math.cos(angle) * radius);
            ys[i] = cy + (int) (Math.sin(angle) * radius);
        }
        g.fillPolygon(xs, ys, points * 2);
    }

    private void drawClouds(Graphics2D g, int count) {
        Random rng = new Random(55L);
        for (int i = 0; i < count; i++) {
            int x = rng.nextInt(SIZE - 120);
            int y = rng.nextInt(SIZE / 2);
            g.fillOval(x,      y + 20, 80, 50);
            g.fillOval(x + 20, y,      60, 55);
            g.fillOval(x + 50, y + 15, 70, 50);
        }
    }

    private void drawGears(Graphics2D g, int count, int minR, int maxR) {
        Random rng = new Random(66L);
        g.setStroke(new BasicStroke(3f));
        for (int i = 0; i < count; i++) {
            int r = minR + rng.nextInt(maxR - minR);
            int cx = rng.nextInt(SIZE);
            int cy = rng.nextInt(SIZE);
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
            for (int t = 0; t < 8; t++) {
                double angle = Math.PI * 2 / 8 * t;
                int x1 = cx + (int) (Math.cos(angle) * r);
                int y1 = cy + (int) (Math.sin(angle) * r);
                int x2 = cx + (int) (Math.cos(angle) * (r + 10));
                int y2 = cy + (int) (Math.sin(angle) * (r + 10));
                g.drawLine(x1, y1, x2, y2);
            }
        }
    }

    private void drawHexagons(Graphics2D g, int count, int size) {
        Random rng = new Random(44L);
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < count; i++) {
            int cx = rng.nextInt(SIZE);
            int cy = rng.nextInt(SIZE);
            int[] xs = new int[6];
            int[] ys = new int[6];
            for (int j = 0; j < 6; j++) {
                double angle = Math.PI / 3 * j;
                xs[j] = cx + (int) (Math.cos(angle) * size);
                ys[j] = cy + (int) (Math.sin(angle) * size);
            }
            g.drawPolygon(xs, ys, 6);
        }
    }

    private void drawFlames(Graphics2D g, int count, int minH, int maxH) {
        Random rng = new Random(33L);
        for (int i = 0; i < count; i++) {
            int h = minH + rng.nextInt(maxH - minH);
            int x = rng.nextInt(SIZE - 20);
            int y = SIZE - rng.nextInt(SIZE / 3);
            int[] xs = {x, x + 10, x + 20, x + 15, x + 10, x + 5};
            int[] ys = {y, y - h, y, y - h / 2, y - h + 10, y - h / 2};
            g.fillPolygon(xs, ys, 6);
        }
    }

    private void drawLeaves(Graphics2D g, int count) {
        Random rng = new Random(22L);
        for (int i = 0; i < count; i++) {
            int cx = rng.nextInt(SIZE);
            int cy = rng.nextInt(SIZE);
            int w = 20 + rng.nextInt(30);
            int h = 10 + rng.nextInt(20);
            g.fillOval(cx - w / 2, cy - h / 2, w, h);
        }
    }

    // ----------------------------------------------------------------
    // 팩션 테마 색상
    // ----------------------------------------------------------------
    private record FactionTheme(Color top, Color bottom, Color accent) {}

    private FactionTheme getTheme(String faction) {
        return switch (faction) {
            case "nature-spirits"     -> new FactionTheme(new Color(144, 210, 90),  new Color(60, 130, 40),   new Color(255, 230, 80));
            case "deep-savers"        -> new FactionTheme(new Color(30, 100, 200),  new Color(10, 40, 120),   new Color(100, 220, 220));
            case "nightmare-soldiers" -> new FactionTheme(new Color(60, 20, 80),    new Color(20, 10, 40),    new Color(200, 100, 240));
            case "wind-guardians"     -> new FactionTheme(new Color(140, 200, 255), new Color(60, 140, 210),  new Color(255, 255, 255));
            case "metal-empire"       -> new FactionTheme(new Color(160, 160, 175), new Color(80, 85, 95),    new Color(200, 180, 100));
            case "virus-busters"      -> new FactionTheme(new Color(200, 230, 255), new Color(80, 160, 240),  new Color(0, 220, 255));
            case "dragons-roar"       -> new FactionTheme(new Color(240, 120, 30),  new Color(180, 40, 10),   new Color(255, 220, 50));
            case "jungle-troopers"    -> new FactionTheme(new Color(60, 160, 60),   new Color(20, 90, 20),    new Color(180, 230, 80));
            case "dark-area"          -> new FactionTheme(new Color(20, 10, 50),    new Color(5, 5, 20),      new Color(180, 130, 255));
            case "trash"              -> new FactionTheme(new Color(220, 200, 100), new Color(160, 120, 60),  new Color(255, 100, 150));
            default                   -> new FactionTheme(new Color(200, 180, 240), new Color(120, 100, 180), new Color(255, 220, 120));
        };
    }

    // ----------------------------------------------------------------
    // 유틸
    // ----------------------------------------------------------------
    private byte[] decodeBase64(String base64) {
        String clean = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        return Base64.getDecoder().decode(clean);
    }

    private int clamp(int v) {
        return Math.min(255, Math.max(0, v));
    }
}
