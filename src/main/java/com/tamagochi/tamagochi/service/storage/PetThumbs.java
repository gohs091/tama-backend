package com.tamagochi.tamagochi.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * S3에 올라간 끄적이 그림의 작은 판. 처음 필요할 때 원본을 받아 줄이고 기억해 둔다.
 * 그림 키에 내용 해시가 들어 있어서 같은 키의 그림은 바뀌지 않는다 (기억한 것을 버릴 일이 없다).
 */
@Service
@Slf4j
public class PetThumbs {

    static final int CACHE_MAX = 500;
    /** 광장은 메시지 하나에 여러 마리가 실린다. 한 장이 이보다 길면 쓰지 않는다 */
    static final int MAX_CHARS = 40_000;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private final Function<String, byte[]> fetch;
    private final Map<String, String> cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_MAX;
        }
    });
    /** 64×64 그림. 키가 곧 내용이라 바뀌지 않아 같은 방식으로 기억한다 */
    private final Map<String, String> dotCache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_MAX;
        }
    });

    @Autowired
    public PetThumbs() {
        this(PetThumbs::download);
    }

    PetThumbs(Function<String, byte[]> fetch) {
        this.fetch = fetch;
    }

    /** 한 번 받은 원본에서 만든 두 그림. 못 만든 쪽은 null */
    public record Both(String thumb, String dot64) {}

    /** 이 키의 작은 그림(data URL). 키가 없거나, 원본을 받지 못했거나, 줄이지 못했으면 빈 값 (실패는 기억하지 않아 다음에 다시 해 본다) */
    public Optional<String> of(String imageKey) {
        return Optional.ofNullable(both(imageKey).thumb());
    }

    /** 이 키의 64×64 그림(PNG data URL). 못 만들면 null */
    public String dot64(String imageKey) {
        return both(imageKey).dot64();
    }

    /**
     * 원본을 한 번만 받아 작은 그림(128)과 64×64 그림을 같이 만든다. 둘 다 이미 기억해 둔 것이면 받지 않는다.
     * 만든 것은 기억하고, 못 만든 쪽은 기억하지 않아 다음에 다시 해 본다
     */
    public Both both(String imageKey) {
        String url = PetImageUrls.of(imageKey);
        if (url == null) return new Both(null, null);
        String thumb = cache.get(imageKey), dot = dotCache.get(imageKey);
        if (thumb != null && dot != null) return new Both(thumb, dot);
        byte[] original = fetch.apply(url);
        if (thumb == null) {
            thumb = SpriteThumb.dataUrl(original);
            if (thumb == null || thumb.length() > MAX_CHARS) {
                log.warn("작은 그림을 만들지 못함 - key={}, 원본={}바이트, 결과={}자", imageKey, original == null ? 0 : original.length, thumb == null ? 0 : thumb.length());
                thumb = null;
            } else {
                cache.put(imageKey, thumb);
            }
        }
        if (dot == null) {
            dot = makeDot64(imageKey, original);
            if (dot != null) dotCache.put(imageKey, dot);
        }
        return new Both(thumb, dot);
    }

    /** 3D 광장용 64×64 그림 한 변의 칸 수 (그림 한 칸 = 화면 도트 한 칸) */
    static final int DOT = 64;

    private static String makeDot64(String imageKey, byte[] original) {
        try {
            if (original == null || original.length == 0) return null;
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) return null;
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(snap64(src), "png", png)) return null;
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception e) {
            log.warn("64칸 그림을 만들지 못함 - key={}", imageKey, e);
            return null;
        }
    }

    /**
     * 그림을 64×64 칸으로 나눠 칸마다 가장 많은 색 하나로 칠한다 (투명이 절반 넘으면 투명).
     * PixelSnap 은 같은 크기로 다시 찍고 색 수도 줄이는 다른 일이라, 칸마다 색 하나를 뽑는 이 계산만 따로 둔다.
     * 이미 64칸을 키운 도트 그림은 원래 칸 그대로 돌아온다.
     */
    static BufferedImage snap64(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(DOT, DOT, BufferedImage.TYPE_INT_ARGB);
        for (int gy = 0; gy < DOT; gy++) {
            int y0 = gy * h / DOT, y1 = Math.max(y0 + 1, (gy + 1) * h / DOT);
            for (int gx = 0; gx < DOT; gx++) {
                int x0 = gx * w / DOT, x1 = Math.max(x0 + 1, (gx + 1) * w / DOT);
                Map<Integer, Integer> counts = new HashMap<>();
                int total = 0, opaque = 0, best = 0, bestCount = 0;
                for (int y = y0; y < Math.min(y1, h); y++) {
                    for (int x = x0; x < Math.min(x1, w); x++) {
                        total++;
                        int p = src.getRGB(x, y);
                        if ((p >>> 24) < 128) continue;
                        opaque++;
                        int rgb = p | 0xFF000000;
                        int n = counts.merge(rgb, 1, Integer::sum);
                        if (n > bestCount) { bestCount = n; best = rgb; }
                    }
                }
                if (total > 0 && opaque * 2 >= total) out.setRGB(gx, gy, best);
            }
        }
        return out;
    }

    /** 앱이 보낸 작은 그림(data URL)이 그려진 것 없는 빈 그림인지. 읽지 못한 그림은 빈 그림으로 치지 않는다 */
    public static boolean blank(String dataUrl) {
        return SpriteThumb.blank(dataUrl);
    }

    private static byte[] download(String url) {
        try {
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
