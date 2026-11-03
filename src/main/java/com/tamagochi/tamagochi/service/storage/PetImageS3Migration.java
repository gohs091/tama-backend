package com.tamagochi.tamagochi.service.storage;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * DB(base64)에 남은 끄적이 그림을 S3로 옮긴다. storage.s3.enabled와 storage.s3.migrate(S3_MIGRATE_EXISTING)가 둘 다 켜져 있을 때만,
 * 서버가 뜬 뒤 백그라운드에서 한 번 돈다. 옮긴 그림은 DB의 base64를 지우고 키만 남긴다. 여러 번 돌아도 안전하다.
 * 다 옮긴 뒤에는 S3_MIGRATE_EXISTING을 끄고, base64 칸이 모두 비었는지 확인한 다음 칸을 지운다.
 */
@Component
@Order(2) // 공개 ID를 먼저 채운다 (PetIdentityMigration)
@Slf4j
public class PetImageS3Migration implements ApplicationRunner {

    private static final int BATCH = 20;

    private final PetImageStore store;
    private final TamagotchiRepository repository;
    private final TransactionTemplate tx;
    private final boolean migrate;

    public PetImageS3Migration(PetImageStore store, TamagotchiRepository repository, TransactionTemplate tx,
                               @Value("${storage.s3.migrate:false}") boolean migrate) {
        this.store = store;
        this.repository = repository;
        this.tx = tx;
        this.migrate = migrate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!store.enabled() || !migrate) return;
        Thread t = new Thread(this::migrateAll, "pet-image-s3-migration");
        t.setDaemon(true);
        t.start();
    }

    void migrateAll() {
        long after = 0;
        int moved = 0, failed = 0;
        log.info("끄적이 그림 S3 이전 시작");
        while (true) {
            List<Long> ids = repository.findIdsWithBase64Images(after, PageRequest.of(0, BATCH));
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                after = id;
                try {
                    Integer n = tx.execute(s -> migrateOne(id));
                    moved += n == null ? 0 : n;
                } catch (Exception e) {
                    failed++;
                    log.warn("끄적이 그림 S3 이전 실패 - pet id={}: {}", id, e.getMessage());
                }
            }
        }
        log.info("끄적이 그림 S3 이전 끝 - 옮긴 그림 {}장, 실패 {}마리", moved, failed);
    }

    /** 한 마리의 단계 그림을 옮긴다. 옮긴 장 수를 돌려준다 */
    int migrateOne(Long id) {
        Tamagotchi pet = repository.findById(id).orElse(null);
        if (pet == null) return 0;
        pet.assignPublicIdIfMissing();
        int n = 0;
        n += move(pet, "baby", pet.getBabyImageBase64());
        n += move(pet, "child", pet.getChildImageBase64());
        n += move(pet, "adult", pet.getAdultImageBase64());
        return n;
    }

    private int move(Tamagotchi pet, String stage, String base64) {
        if (base64 == null || base64.isBlank()) return 0;
        String key = store.put(pet.getPublicId(), stage, PetImage.fromBase64(base64));
        pet.saveStageImageKey(stage, key);
        return 1;
    }
}
