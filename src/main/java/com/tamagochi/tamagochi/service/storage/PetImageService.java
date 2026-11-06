package com.tamagochi.tamagochi.service.storage;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 끄적이 단계 그림 저장. S3가 켜져 있으면 S3에 올리고 DB에는 키만 남긴다(base64는 지운다).
 * 꺼져 있으면(로컬·테스트) 예전처럼 DB에 base64로 둔다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PetImageService {

    private final PetImageStore store;
    private final TamagotchiRepository tamagotchiRepository;
    private final TamagotchiService tamagotchiService;

    @Transactional
    public void saveImage(User user, String stage, String imageBase64) {
        if (!store.enabled()) {
            if ("sketch".equals(stage)) return; // 원본 그림은 S3에만 둔다 (DB가 커지지 않게)
            tamagotchiService.saveImage(user, stage, imageBase64);
            return;
        }
        Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        PetImage image = PetImage.fromBase64(imageBase64);
        String key = store.put(pet.getPublicId(), stage, image);
        pet.saveStageImageKey(stage, key);
        log.info("끄적이 그림 저장 - user={}, pet={}, stage={}, bytes={}", user.getId(), pet.getPublicId(), stage, image.bytes().length);
    }
}
