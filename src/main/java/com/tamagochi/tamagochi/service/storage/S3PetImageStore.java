package com.tamagochi.tamagochi.service.storage;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

/**
 * S3에 끄적이 그림을 올린다. storage.s3.enabled=false(기본)면 S3 클라이언트를 만들지 않고 아무것도 하지 않는다.
 * 권한은 AWS 기본 자격 증명 순서(EC2 역할 등)로 받는다. 키 파일을 서버에 두지 않는다.
 */
@Component
@Slf4j
public class S3PetImageStore implements PetImageStore {

    /** 그림은 내용이 바뀌면 파일 이름이 바뀌므로 오래 캐시해도 된다 */
    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final boolean enabled;
    private final String bucket;
    private final String cdnBase;
    private final S3Client s3;

    public S3PetImageStore(@Value("${storage.s3.enabled:false}") boolean enabled,
                           @Value("${storage.s3.bucket:doodle-image}") String bucket,
                           @Value("${storage.s3.region:ap-northeast-2}") String region,
                           @Value("${storage.cdn-base:}") String cdnBase) {
        this.enabled = enabled;
        this.bucket = bucket;
        this.cdnBase = cdnBase.endsWith("/") ? cdnBase.substring(0, cdnBase.length() - 1) : cdnBase;
        PetImageUrls.setCdnBase(this.cdnBase);
        PetImageUrls.setUploadEnabled(enabled);
        this.s3 = enabled ? S3Client.builder().region(Region.of(region)).build() : null;
        log.info("끄적이 그림 저장소: {}", enabled ? "S3 " + bucket : "DB(base64)");
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String put(String publicId, String stage, PetImage image) {
        if (!enabled) throw new IllegalStateException("S3 저장소가 꺼져 있어요.");
        String key = PetImage.key(publicId, stage, image);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key)
                        .contentType(image.contentType())
                        .cacheControl(CACHE_CONTROL)
                        .build(),
                RequestBody.fromBytes(image.bytes()));
        return key;
    }

    @Override
    public Optional<String> url(String key) {
        return Optional.ofNullable(PetImageUrls.of(key));
    }

    @PreDestroy
    void close() {
        if (s3 != null) s3.close();
    }
}
