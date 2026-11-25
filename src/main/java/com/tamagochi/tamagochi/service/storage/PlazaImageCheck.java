package com.tamagochi.tamagochi.service.storage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Iterator;

/**
 * 앱이 광장으로 보낸 그림을 방 모두에게 돌려줘도 되는지 본다 (순수 계산, DB · 네트워크 없음).
 * svg 처럼 글로 된 그림이나 8000칸짜리 그림을 돌려주면 그 방에 들어오는 모든 앱이 멈춘다.
 * 그림 전체를 풀지 않고 머리의 가로 · 세로만 읽는다 (압축 폭탄을 피한다).
 */
public final class PlazaImageCheck {

    /** 한 변이 이보다 크면 받지 않는다 */
    public static final int MAX_SIDE = 1024;

    private static final String[] PREFIXES = {
            "data:image/png;base64,", "data:image/jpeg;base64,", "data:image/webp;base64,"};

    private PlazaImageCheck() {
    }

    public static boolean accepts(String dataUrl) {
        if (dataUrl == null) return false;
        String prefix = null;
        for (String p : PREFIXES) if (dataUrl.startsWith(p)) { prefix = p; break; }
        if (prefix == null) return false;
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
            if (bytes.length == 0) return false;
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (in == null) return false;
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) return false;   // 읽개가 없으면(webp 를 이 JVM 이 못 읽는 경우 포함) 받지 않는다
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in, true, true);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    return w > 0 && h > 0 && w <= MAX_SIDE && h <= MAX_SIDE;
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception | LinkageError e) {
            return false;
        }
    }
}
