package com.tamagochi.tamagochi.service.storage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;

/**
 * 앱이 광장으로 보낸 그림을 방 모두에게 돌려줘도 되는지 본다 (순수 계산, DB · 네트워크 없음).
 * svg 처럼 글로 된 그림이나 8000칸짜리 그림을 돌려주면 그 방에 들어오는 모든 앱이 멈춘다.
 * 그림 전체를 풀지 않고 머리의 가로 · 세로만 읽는다 (압축 폭탄을 피한다).
 * 머리말에 적은 종류와 내용의 실제 형식이 같아야 받는다 (png 라 하고 gif · tiff 를 싣는 것을 막는다).
 */
public final class PlazaImageCheck {

    /** 한 변이 이보다 크면 받지 않는다 */
    public static final int MAX_SIDE = 1024;

    private static final String[][] KINDS = {
            {"data:image/png;base64,", "png"},
            {"data:image/jpeg;base64,", "jpeg"},
            {"data:image/webp;base64,", "webp"}};

    private PlazaImageCheck() {
    }

    public static boolean accepts(String dataUrl) {
        if (dataUrl == null) return false;
        String prefix = null, kind = null;
        for (String[] k : KINDS) if (dataUrl.startsWith(k[0])) { prefix = k[0]; kind = k[1]; break; }
        if (prefix == null) return false;
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
            if (bytes.length == 0) return false;
            // 임시 파일을 만들지 않는 메모리 읽기 (광장 잠금 안에서 돈다)
            try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) return false;   // 읽개가 없으면(webp 를 이 JVM 이 못 읽는 경우 포함) 받지 않는다
                ImageReader reader = readers.next();
                try {
                    if (!sameKind(kind, reader.getFormatName())) return false;
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

    private static boolean sameKind(String kind, String formatName) {
        String f = formatName == null ? "" : formatName.toLowerCase(Locale.ROOT);
        return kind.equals("jpeg") ? (f.equals("jpeg") || f.equals("jpg")) : kind.equals(f);
    }
}
