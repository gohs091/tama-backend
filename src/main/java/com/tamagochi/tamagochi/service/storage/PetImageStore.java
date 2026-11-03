package com.tamagochi.tamagochi.service.storage;

import java.util.Optional;

/**
 * 끄적이 그림 저장소. 켜져 있으면(S3) 그림을 올리고 키를 돌려준다. 꺼져 있으면(로컬·테스트) 아무것도 하지 않고,
 * 그림은 예전처럼 DB에 남는다.
 */
public interface PetImageStore {

    boolean enabled();

    /**
     * 그림을 올리고 S3 키를 돌려준다. 키: public/pets/{publicId}/{stage}.{내용 해시 8자}.{png|jpg}
     * 같은 그림은 같은 키라서 다시 올려도 덮어쓰기만 된다. 그림이 바뀌면 키도 바뀌어 CDN 캐시를 지울 필요가 없다.
     */
    String put(String publicId, String stage, PetImage image);

    /** 키 → 앱이 쓰는 주소 (CDN 주소 + 키). 키가 없으면 빈 값 */
    Optional<String> url(String key);
}
