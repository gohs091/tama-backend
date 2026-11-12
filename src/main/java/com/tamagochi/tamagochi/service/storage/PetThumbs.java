package com.tamagochi.tamagochi.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    @Autowired
    public PetThumbs() {
        this(PetThumbs::download);
    }

    PetThumbs(Function<String, byte[]> fetch) {
        this.fetch = fetch;
    }

    /** 이 키의 작은 그림(data URL). 키가 없거나, 원본을 받지 못했거나, 줄이지 못했으면 빈 값 (실패는 기억하지 않아 다음에 다시 해 본다) */
    public Optional<String> of(String imageKey) {
        String url = PetImageUrls.of(imageKey);
        if (url == null) return Optional.empty();
        String known = cache.get(imageKey);
        if (known != null) return Optional.of(known);
        byte[] original = fetch.apply(url);
        String thumb = SpriteThumb.dataUrl(original);
        if (thumb == null || thumb.length() > MAX_CHARS) {
            log.warn("작은 그림을 만들지 못함 - key={}, 원본={}바이트, 결과={}자", imageKey, original == null ? 0 : original.length, thumb == null ? 0 : thumb.length());
            return Optional.empty();
        }
        cache.put(imageKey, thumb);
        return Optional.of(thumb);
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
