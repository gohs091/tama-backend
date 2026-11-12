package com.tamagochi.tamagochi.service.storage;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PetImageTest {

    // 1x1 PNG
    static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    /** 메모리에만 저장하는 가짜 S3 */
    static class FakeStore implements PetImageStore {
        final Map<String, PetImage> objects = new LinkedHashMap<>();
        public boolean enabled() { return true; }
        public String put(String publicId, String stage, PetImage image) {
            String key = PetImage.key(publicId, stage, image);
            objects.put(key, image);
            return key;
        }
        public Optional<String> url(String key) { return Optional.ofNullable(PetImageUrls.of(key)); }
    }

    private static Tamagotchi pet(EvolutionStage stage) {
        return Tamagotchi.builder().id(5L).name("콩이").evolutionStage(stage).hunger(50).strength(50).build();
    }

    @Test
    @DisplayName("PNG·JPEG만 받고, 키는 public/pets/{공개ID}/{단계}.{해시}.png 모양이다")
    void keyFormat() {
        PetImage img = PetImage.fromBase64(PNG);
        assertThat(img.contentType()).isEqualTo("image/png");
        String id = "0123456789abcdef0123456789abcdef";
        assertThat(PetImage.key(id, "child", img)).matches("public/pets/" + id + "/child\\.[0-9a-f]{8}\\.png");
        assertThat(PetImage.key(id, "child", img)).isEqualTo(PetImage.key(id, "child", PetImage.fromBase64(PNG)));

        assertThatThrownBy(() -> PetImage.fromBase64("data:image/gif;base64,R0lGODlhAQABAAAAACw=")).hasMessageContaining("PNG나 JPEG");
        assertThatThrownBy(() -> PetImage.key(id, "../x", img)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PetImage.key("toss_abc", "baby", img)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("S3가 켜져 있으면 그림을 올리고 DB에는 키만 남긴다 (base64는 지운다). 응답 주소는 CDN + 키")
    void saveToS3() {
        FakeStore store = new FakeStore();
        TamagotchiRepository repo = mock(TamagotchiRepository.class);
        Tamagotchi pet = pet(EvolutionStage.CHILD);
        pet.saveStageImage("child", "old-base64");
        when(repo.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        PetImageService service = new PetImageService(store, repo, mock(TamagotchiService.class), mock(com.tamagochi.tamagochi.repository.user.UserRepository.class));

        service.saveImage(User.builder().id(1L).build(), "child", PNG);

        assertThat(pet.getChildImageBase64()).isNull();
        assertThat(pet.getChildImageKey()).startsWith("public/pets/" + pet.getPublicId() + "/child.");
        assertThat(store.objects).containsKey(pet.getChildImageKey());
        PetImageUrls.setCdnBase("https://cdn.example/");
        assertThat(PetImageUrls.of(pet.getCurrentImageKey())).isEqualTo("https://cdn.example/" + pet.getChildImageKey());
        PetImageUrls.setCdnBase("");
    }

    @Test
    @DisplayName("원본 그림(sketch)도 S3에 올려 키를 남기고, 지금 단계 그림은 그대로 둔다. 새로 시작하면 원본 키도 지운다")
    void saveSketch() {
        FakeStore store = new FakeStore();
        TamagotchiRepository repo = mock(TamagotchiRepository.class);
        Tamagotchi pet = pet(EvolutionStage.CHILD);
        pet.saveStageImageKey("child", "public/pets/x/child.1.png");
        when(repo.findActiveByUserId(1L)).thenReturn(Optional.of(pet));

        new PetImageService(store, repo, mock(TamagotchiService.class), mock(com.tamagochi.tamagochi.repository.user.UserRepository.class)).saveImage(User.builder().id(1L).build(), "sketch", PNG);

        assertThat(pet.getSketchImageKey()).startsWith("public/pets/" + pet.getPublicId() + "/sketch.");
        assertThat(pet.getCurrentImageKey()).isEqualTo("public/pets/x/child.1.png");
        pet.reset();
        assertThat(pet.getSketchImageKey()).isNull();
    }

    @Test
    @DisplayName("S3가 꺼져 있으면 원본 그림은 저장하지 않는다 (DB가 커지지 않게)")
    void sketchNotStoredInDb() {
        PetImageStore off = mock(PetImageStore.class);
        when(off.enabled()).thenReturn(false);
        TamagotchiService legacy = mock(TamagotchiService.class);

        new PetImageService(off, mock(TamagotchiRepository.class), legacy, mock(com.tamagochi.tamagochi.repository.user.UserRepository.class)).saveImage(User.builder().id(1L).build(), "sketch", PNG);

        verify(legacy, never()).saveImage(any(), any(), any());
    }

    @Test
    @DisplayName("S3가 꺼져 있으면(로컬·테스트) 예전처럼 DB에 둔다")
    void fallbackToDb() {
        PetImageStore off = mock(PetImageStore.class);
        when(off.enabled()).thenReturn(false);
        TamagotchiService legacy = mock(TamagotchiService.class);
        User u = User.builder().id(1L).build();

        new PetImageService(off, mock(TamagotchiRepository.class), legacy, mock(com.tamagochi.tamagochi.repository.user.UserRepository.class)).saveImage(u, "baby", PNG);

        verify(legacy).saveImage(u, "baby", PNG);
        verify(off, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("이전 작업: DB에 남은 base64 그림을 단계별로 S3에 올리고 base64를 지운다. 깨진 그림은 남기고 다음으로 넘어간다")
    @SuppressWarnings("unchecked")
    void migrate() {
        FakeStore store = new FakeStore();
        TamagotchiRepository repo = mock(TamagotchiRepository.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any(TransactionCallback.class))).thenAnswer(i -> ((TransactionCallback<Object>) i.getArgument(0)).doInTransaction(null));

        Tamagotchi good = pet(EvolutionStage.ADULT);
        good.saveStageImage("baby", PNG);
        good.saveStageImage("adult", PNG);
        Tamagotchi broken = Tamagotchi.builder().id(6L).name("x").evolutionStage(EvolutionStage.BABY).hunger(50).strength(50).build();
        broken.saveStageImage("baby", "not-an-image");
        when(repo.findIdsWithBase64Images(eq(0L), any())).thenReturn(List.of(5L, 6L));
        when(repo.findIdsWithBase64Images(eq(6L), any())).thenReturn(List.of());
        when(repo.findById(5L)).thenReturn(Optional.of(good));
        when(repo.findById(6L)).thenReturn(Optional.of(broken));

        new PetImageS3Migration(store, repo, tx, true).migrateAll();

        assertThat(good.getBabyImageBase64()).isNull();
        assertThat(good.getAdultImageBase64()).isNull();
        assertThat(good.getBabyImageKey()).isNotNull();
        assertThat(good.getAdultImageKey()).isNotNull();
        assertThat(store.objects).hasSize(2);
        assertThat(broken.getBabyImageBase64()).isEqualTo("not-an-image");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("서버가 만든 그림은 바로 S3에 올라가고, 다음 단계 그림이면 진화하는 순간 현재 그림이 된다")
    void generatedImageIsStoredAndAdopted() {
        FakeStore store = new FakeStore();
        TamagotchiRepository repo = mock(TamagotchiRepository.class);
        var users = mock(com.tamagochi.tamagochi.repository.user.UserRepository.class);
        Tamagotchi pet = pet(EvolutionStage.BABY);
        pet.assignPublicIdIfMissing();
        when(users.findByTossUserId("toss_a")).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(repo.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        byte[] png = java.util.Base64.getDecoder().decode(PNG.substring(PNG.indexOf(',') + 1));
        PetImageService service = new PetImageService(store, repo, mock(TamagotchiService.class), users);

        service.saveGenerated("toss_a", "child", png);
        assertThat(store.objects).hasSize(1);
        assertThat(pet.getCurrentImageKey()).isNull();   // 아직 낙서다

        pet.evolve(EvolutionStage.CHILD);
        assertThat(pet.getCurrentImageKey()).isEqualTo(store.objects.keySet().iterator().next());

        // 모르는 유저, 그림이 아닌 것은 조용히 넘어간다 (그림 만들기를 망치지 않는다)
        service.saveGenerated("toss_none", "child", png);
        service.saveGenerated("toss_a", "adult", "그림 아님".getBytes());
        service.saveGenerated(null, "adult", png);
        assertThat(store.objects).hasSize(1);
    }
}
