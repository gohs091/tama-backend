package com.tamagochi.tamagochi.service.storage;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/** 앱이 보낸 그림(data URL 또는 base64)을 검사해 바이트로 바꾼다 */
public record PetImage(byte[] bytes, String contentType, String extension) {

    public static final int MAX_BYTES = 3 * 1024 * 1024;

    /** "data:image/png;base64,...." 또는 base64만. PNG·JPEG만 받는다 */
    public static PetImage fromBase64(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("그림이 없어요.");
        String b64 = raw.startsWith("data:") ? raw.substring(raw.indexOf(',') + 1) : raw;
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("그림을 읽을 수 없어요.");
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("그림 크기가 맞지 않아요.");
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return new PetImage(bytes, "image/png", "png");
        }
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return new PetImage(bytes, "image/jpeg", "jpg");
        }
        throw new IllegalArgumentException("PNG나 JPEG 그림만 저장할 수 있어요.");
    }

    /** 내용 해시 앞 8자 (파일 이름 버전) */
    public String shortHash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String key(String publicId, String stage, PetImage image) {
        if (publicId == null || !publicId.matches("[0-9a-f]{32}")) throw new IllegalStateException("끄적이 공개 ID가 없어요.");
        if (!stage.matches("baby|child|adult")) throw new IllegalArgumentException("알 수 없는 단계예요.");
        return "public/pets/" + publicId + "/" + stage + "." + image.shortHash() + "." + image.extension();
    }
}
