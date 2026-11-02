package com.tamagochi.tamagochi.service.storage;

/**
 * S3 키 → 앱이 쓰는 주소. DB에는 키만 두고 CDN 주소(storage.cdn-base)는 서버 설정으로 붙인다.
 * 그래서 나중에 도메인을 바꿔도 DB를 고칠 필요가 없다. 응답 DTO(정적 from)에서 쓰려고 정적으로 둔다.
 */
public final class PetImageUrls {

    private static volatile String cdnBase = "";
    private static volatile boolean uploadEnabled = false;

    private PetImageUrls() {
    }

    static void setCdnBase(String base) {
        cdnBase = base == null ? "" : (base.endsWith("/") ? base.substring(0, base.length() - 1) : base);
    }

    static void setUploadEnabled(boolean enabled) {
        uploadEnabled = enabled;
    }

    /** 앱이 그림을 서버에 올려도 되는지 (S3가 켜져 있을 때만. 꺼져 있으면 DB가 커지지 않게 올리지 않는다) */
    public static boolean uploadEnabled() {
        return uploadEnabled;
    }

    /** 키가 없거나 CDN 주소가 설정되지 않았으면 null */
    public static String of(String key) {
        if (key == null || key.isBlank() || cdnBase.isBlank()) return null;
        return cdnBase + "/" + key;
    }
}
