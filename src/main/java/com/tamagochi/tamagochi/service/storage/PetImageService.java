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
    private final com.tamagochi.tamagochi.repository.user.UserRepository userRepository;

    // 그림이 바라보는 쪽 분석 (없으면 분석하지 않는다)
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.tamagochi.tamagochi.service.character.DoodleSubjectService doodleSubjectService;

    /**
     * 서버가 방금 만든 단계 그림을 S3에 올리고 끄적이에 적어 둔다 (S3가 꺼져 있으면 아무것도 하지 않는다).
     * 전에는 서버가 만든 그림을 폰에 돌려주고 폰이 다시 올려야 서버에 남았다. 그 사이(막 진화한 직후 등)에는 서버에 그림이 없어서
     * 광장이 폰이 만든 작은 그림에 기댔고, 그 그림이 빈 그림으로 만들어지면 이름표만 떠다녔다.
     * 그림 만들기를 망치지 않게 실패는 기록만 하고 넘어간다
     */
    @Transactional
    public void saveGenerated(String tossUserId, String stage, byte[] image) {
        if (!store.enabled() || tossUserId == null || tossUserId.isBlank() || image == null || image.length == 0) return;
        if (!stage.matches("baby|child|adult")) return;
        try {
            User user = userRepository.findByTossUserId(tossUserId).orElse(null);
            if (user == null) return;
            Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId()).orElse(null);
            if (pet == null) return;
            PetImage petImage = PetImage.fromBase64(java.util.Base64.getEncoder().encodeToString(image));
            String key = store.put(pet.getPublicId(), stage, petImage);
            pet.keepGeneratedImage(stage, key);
            log.info("만든 그림 저장 - user={}, pet={}, stage={}, bytes={}", user.getId(), pet.getPublicId(), stage, image.length);
        } catch (Exception e) {
            log.warn("만든 그림 저장 실패 - stage={}: {}", stage, e.getMessage());
        }
    }

    /** 서버가 방금 만든 단계 그림이 바라보는 쪽을 끄적이에 적어 둔다 (S3와 상관없이). 실패는 기록만 하고 넘어간다 */
    @Transactional
    public void saveGeneratedFacing(String tossUserId, String stage, String facing) {
        if (tossUserId == null || tossUserId.isBlank() || stage == null || !stage.matches("baby|child|adult")) return;
        try {
            User user = userRepository.findByTossUserId(tossUserId).orElse(null);
            if (user == null) return;
            tamagotchiRepository.findActiveByUserId(user.getId()).ifPresent(pet -> pet.keepGeneratedFacing(stage, facing));
        } catch (Exception e) {
            log.warn("그림 방향 저장 실패 - stage={}: {}", stage, e.getMessage());
        }
    }

    /**
     * 지금 단계 그림의 방향이 서버에 없을 때 채운다 (이 기능 전에 만든 끄적이).
     * 서버에 이미 있으면 그대로 둔다. 폰이 알고 있으면(clientFacing) 그 값을, 모르면 그림을 분석한다:
     * 폰이 보낸 그림(imageBase64)이 있으면 그것으로, 없으면 서버에 저장된 그림으로. 분석은 끄적이·단계마다 한 번이면 된다
     */
    @Transactional
    public Tamagotchi fillFacing(User user, String clientFacing, String imageBase64) {
        Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        String stage = pet.currentStageName();
        if (stage == null || pet.getCurrentFacing() != null) return pet;
        boolean known = Tamagotchi.isFacing(clientFacing);
        String facing = known ? clientFacing : detectFacing(pet, imageBase64);
        if (facing != null) {
            pet.saveStageFacing(stage, facing);
            log.info("그림 방향 채움 - user={}, pet={}, stage={}, facing={}, 폰이 알려 줌={}", user.getId(), pet.getPublicId(), stage, facing, known);
        }
        return pet;
    }

    private String detectFacing(Tamagotchi pet, String imageBase64) {
        if (doodleSubjectService == null) return null;
        try {
            byte[] bytes;
            if (imageBase64 != null && !imageBase64.isBlank()) {
                bytes = PetImage.fromBase64(imageBase64).bytes();
            } else {
                String url = PetImageUrls.of(pet.getCurrentImageKey());
                if (url == null) return null;
                var response = java.net.http.HttpClient.newHttpClient().send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(10)).build(),
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) return null;
                bytes = response.body();
            }
            return doodleSubjectService.facing(bytes).orElse(null);
        } catch (Exception e) {
            log.warn("그림 방향 분석 실패 - pet={}: {}", pet.getPublicId(), e.getMessage());
            return null;
        }
    }

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
