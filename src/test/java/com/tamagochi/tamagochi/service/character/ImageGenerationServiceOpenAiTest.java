package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ImageGenerationServiceOpenAiTest {

    private ImageGenerationService service;
    private WebClient stabilityClient;

    @BeforeEach
    void setUp() {
        service = new ImageGenerationService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "stabilityApiKey", "unused");
        ReflectionTestUtils.setField(service, "provider", "openai");
        ReflectionTestUtils.setField(service, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "openAiImageModel", "gpt-image-1-mini");
        ReflectionTestUtils.setField(service, "openAiImageQuality", "medium");

        stabilityClient = mock(WebClient.class);
        ReflectionTestUtils.setField(service, "webClient", stabilityClient);
    }

    // ── 프롬프트 ─────────────────────────────────────────────

    @Test
    @DisplayName("유체 프롬프트: 빈 선화를 색으로 채우고 특징을 유지하라고 지시한다")
    void babyPrompt() {
        String p = service.buildOpenAiPrompt("nightmare-soldiers", "baby", false);
        assertThat(p).contains("empty unfilled interior");
        assertThat(p).contains("no part of the body may be see-through");
        assertThat(p).contains("whiskers");
        assertThat(p).contains("chibi version of the doodle");
        assertThat(p).contains("do not add new accessories");
        // 물체 그림은 그 물체 자체를 캐릭터로 만들고 엉뚱한 동물로 바꾸지 않는다
        assertThat(p).contains("do NOT replace it with an unrelated animal");
        assertThat(p).contains("dark purple and black");
        assertThat(p).contains("transparent background");
    }

    @Test
    @DisplayName("참조 이미지가 있으면 '같은 캐릭터의 다음 진화형'으로 지시한다")
    void evolvedPromptWithReference() {
        String p = service.buildOpenAiPrompt("dragons-roar", "child", true);
        assertThat(p).contains("Image 1 is the CURRENT form");
        assertThat(p).contains("NEXT evolved form of the SAME creature");
        assertThat(p).contains("orange and red");
        assertThat(p).doesNotContain("empty unfilled interior");
    }

    @Test
    @DisplayName("참조 이미지가 없으면 낙서 재해석 프롬프트를 쓴다")
    void evolvedPromptWithoutReference() {
        String p = service.buildOpenAiPrompt("dragons-roar", "adult", false);
        assertThat(p).contains("rough doodle drawn by a player");
        assertThat(p).contains("fully evolved");
        assertThat(p).doesNotContain("Image 1");
    }

    @Test
    @DisplayName("모든 프롬프트는 픽셀 아트 스타일 지시로 시작한다 (뒤에 두면 벡터 그림이 나옴)")
    void promptStartsWithPixelStyle() {
        DoodleSubject sword = new DoodleSubject("a sword", "object", "a blade");
        assertThat(service.buildOpenAiPrompt("trash", "baby", false)).startsWith("Create a 16-bit PIXEL ART sprite");
        assertThat(service.buildOpenAiPrompt("trash", "baby", false, sword)).startsWith("Create a 16-bit PIXEL ART sprite");
        assertThat(service.buildOpenAiPrompt("trash", "child", true)).startsWith("Create a 16-bit PIXEL ART sprite");
    }

    @Test
    @DisplayName("물체 그림(칼): 그 물체 자체가 캐릭터이고 동물로 바꾸지 않으며 부위 위치를 유지한다")
    void promptWithObjectSubject() {
        DoodleSubject sword = new DoodleSubject(
                "a sword", "object", "a long curved blade on top, a horizontal crossguard in the middle, a short handle at the bottom");
        String p = service.buildOpenAiPrompt("virus-busters", "baby", false, sword);
        assertThat(p).contains("The doodle depicts a sword");
        assertThat(p).contains("itself is the character, NOT an animal");
        assertThat(p).contains("a long curved blade on top, a horizontal crossguard in the middle");
        assertThat(p).contains("pink and white");
    }

    @Test
    @DisplayName("동물 그림: '동물이 아니다'라고 하지 않고 실루엣과 자세를 유지하라고 한다")
    void promptWithAnimalSubject() {
        DoodleSubject cat = new DoodleSubject("a cat", "animal", "two pointed ears, a tail");
        String p = service.buildOpenAiPrompt("nature-spirits", "baby", false, cat);
        assertThat(p).contains("The doodle depicts a cat");
        assertThat(p).doesNotContain("NOT an animal");
        assertThat(p).contains("keep its silhouette");
    }

    @Test
    @DisplayName("추상적인 낙서는 일반 프롬프트를 쓴다")
    void promptWithAbstractSubject_isGeneric() {
        DoodleSubject abs = new DoodleSubject("an abstract shape", "abstract", "");
        String p = service.buildOpenAiPrompt("trash", "baby", false, abs);
        assertThat(p).doesNotContain("The doodle depicts");
        assertThat(p).contains("rough doodle drawn by a player");
    }

    // ── 합체 방식 (그림 + 팩션) ──────────────────────────────

    private static final DoodleSubject SWORD = new DoodleSubject("a sword", "object",
            "a long curved blade on top, a horizontal crossguard in the middle, a short handle at the bottom");
    private static final DoodleSubject CAT = new DoodleSubject("a cat", "animal", "two pointed ears, a tail");

    @Test
    @DisplayName("SELF: 그림 자체가 캐릭터 (기존 방식과 동일)")
    void fusionSelf_sameAsBefore() {
        String p = service.buildOpenAiPrompt("dragons-roar", "baby", false, SWORD, FusionMode.SELF);
        assertThat(p).isEqualTo(service.buildOpenAiPrompt("dragons-roar", "baby", false, SWORD));
        assertThat(p).contains("itself is the character, NOT an animal");
    }

    @Test
    @DisplayName("HELD: 팩션 생물이 그림을 쥐고 있고, 그림은 크고 알아볼 수 있어야 한다")
    void fusionHeld() {
        String p = service.buildOpenAiPrompt("dragons-roar", "baby", false, SWORD, FusionMode.HELD);
        assertThat(p).contains("holding, wielding or carrying the drawn a sword");
        assertThat(p).contains("NOT the doodle itself");
        assertThat(p).contains("at least half of the sprite's size");
        assertThat(p).contains("dragon and dinosaur traits");
        assertThat(p).doesNotContain("itself is the character, NOT an animal");
    }

    @Test
    @DisplayName("FEATURE: 그림이 팩션 생물 몸의 시그니처 일부가 된다")
    void fusionFeature() {
        String p = service.buildOpenAiPrompt("metal-empire", "baby", false, SWORD, FusionMode.FEATURE);
        assertThat(p).contains("physically part of the creature's own body");
        assertThat(p).contains("horn, tail, wing, crest or shell");
        assertThat(p).contains("NOT held in a hand");
        assertThat(p).contains("at least half of the sprite's size");
    }

    @Test
    @DisplayName("MOUNT: 그림이 팩션 생물의 탈것이 된다")
    void fusionMount() {
        String p = service.buildOpenAiPrompt("wind-guardians", "baby", false, SWORD, FusionMode.MOUNT);
        assertThat(p).contains("riding on the drawn a sword");
        assertThat(p).contains("vehicle or mount");
        assertThat(p).contains("at least half of the sprite's size");
        assertThat(p).contains("canyon and valley traits");
    }

    @Test
    @DisplayName("동물 그림도 MOUNT가 가능하다 (팩션 생물이 그 동물을 탄다)")
    void fusionMount_animal() {
        String p = service.buildOpenAiPrompt("deep-savers", "baby", false, CAT, FusionMode.MOUNT);
        assertThat(p).contains("riding on the drawn a cat");
    }

    @Test
    @DisplayName("합체 방식의 유체는 처음부터 팩션 생물이 등장한다 (테마 60%, 그림은 크게 유지)")
    void fusedBabyHasFactionCreature() {
        String p = service.buildOpenAiPrompt("nightmare-soldiers", "baby", false, SWORD, FusionMode.HELD);
        assertThat(p).contains("baby creature of this faction").contains("about 60%");
        assertThat(p).contains("clearly recognizable and large");
        assertThat(p).doesNotContain("about 20%");
    }

    @Test
    @DisplayName("성체/완전체는 유체와 같은 구도(쥐기·타기·합체)를 유지하며 함께 자란다")
    void evolvedKeepsComposition() {
        for (String stage : new String[]{"child", "adult"}) {
            String p = service.buildOpenAiPrompt("dragons-roar", stage, true, null, FusionMode.SELF);
            assertThat(p).contains("Keep the same composition as image 1");
            assertThat(p).contains("holds, rides or merges with the drawn object");
            assertThat(p).contains("grows together with the creature");
        }
    }

    @Test
    @DisplayName("추상적이면 합체 방식을 쓰지 않고 일반 프롬프트")
    void abstractIgnoresMode() {
        DoodleSubject abs = new DoodleSubject("an abstract shape", "abstract", "");
        String p = service.buildOpenAiPrompt("trash", "baby", false, abs, FusionMode.MOUNT);
        assertThat(p).doesNotContain("riding on").contains("rough doodle drawn by a player");
    }

    @Test
    @DisplayName("isAnimalLike: 동물·생물·사람만 true")
    void animalLike() {
        assertThat(ImageGenerationService.isAnimalLike(CAT)).isTrue();
        assertThat(ImageGenerationService.isAnimalLike(new DoodleSubject("a dragon", "creature", ""))).isTrue();
        assertThat(ImageGenerationService.isAnimalLike(new DoodleSubject("a knight", "person", ""))).isTrue();
        assertThat(ImageGenerationService.isAnimalLike(SWORD)).isFalse();
        assertThat(ImageGenerationService.isAnimalLike(new DoodleSubject("a flower", "plant", ""))).isFalse();
    }

    // ── 팩션 테마 ────────────────────────────────────────────

    private static final String[] FACTIONS = {
            "nature-spirits", "deep-savers", "nightmare-soldiers", "wind-guardians", "metal-empire",
            "virus-busters", "dragons-roar", "jungle-troopers", "dark-area", "trash"};

    @Test
    @DisplayName("모든 팩션은 모든 단계의 프롬프트에 자기 테마가 들어간다")
    void everyFactionHasThemeInEveryStage() {
        for (String f : FACTIONS) {
            for (String stage : new String[]{"baby", "child", "adult"}) {
                for (boolean ref : new boolean[]{false, true}) {
                    String p = service.buildOpenAiPrompt(f, stage, ref);
                    assertThat(p).as("%s/%s/ref=%s", f, stage, ref).contains("Faction theme: ");
                    assertThat(p).as("%s 는 기본 테마가 아니어야 함", f).doesNotContain("a cute, neutral look");
                }
            }
        }
    }

    @Test
    @DisplayName("팩션 컨셉: 짐승/식물·곤충/기계/조류/수중/용/영웅")
    void factionConcepts() {
        assertThat(service.buildThemeText("nature-spirits", "adult")).contains("beast", "ears", "tail");
        assertThat(service.buildThemeText("jungle-troopers", "adult")).contains("plant", "insect");
        assertThat(service.buildThemeText("metal-empire", "adult")).contains("machine", "cyborg", "gears");
        assertThat(service.buildThemeText("wind-guardians", "adult")).contains("bird", "wings");
        assertThat(service.buildThemeText("deep-savers", "adult")).contains("aquatic", "fins");
        assertThat(service.buildThemeText("dragons-roar", "adult")).contains("dragon", "dinosaur");
        assertThat(service.buildThemeText("virus-busters", "adult")).contains("heroic", "justice");
        assertThat(service.buildThemeText("nightmare-soldiers", "adult")).contains("haunted house", "ghost");
        // 다크 에리어: 죽은/삭제된 데이터가 모이는 명계, 마왕형·마수형 (악한 병사 nightmare-soldiers와 구분)
        assertThat(service.buildThemeText("dark-area", "adult")).contains("demon lord", "beast-demon", "underworld", "deleted");
    }

    @Test
    @DisplayName("다크 에리어와 나이트메어 솔져는 서로 다른 테마다")
    void darkAreaAndNightmareSoldiersAreDistinct() {
        assertThat(service.buildThemeText("dark-area", "adult"))
                .isNotEqualTo(service.buildThemeText("nightmare-soldiers", "adult"));
        assertThat(service.buildThemeText("dark-area", "adult")).doesNotContain("torn dark cape");
        // 깨짐/글리치 표현은 이미지가 조각나므로 쓰지 않고, 어두운 배경에서 잘 보이도록 밝은 포인트 색을 쓴다
        assertThat(service.buildThemeText("dark-area", "adult")).doesNotContain("glitch");
        assertThat(service.buildThemeText("dark-area", "adult")).contains("readable on dark backgrounds", "fully intact");
    }

    @Test
    @DisplayName("테마 반영 강도는 유체 20% → 성체 50% → 완전체 80%로 커진다")
    void themeIntensityGrowsWithStage() {
        assertThat(service.buildThemeText("metal-empire", "baby")).contains("about 20%").contains("subtle hint");
        assertThat(service.buildThemeText("metal-empire", "child")).contains("about 50%");
        assertThat(service.buildThemeText("metal-empire", "adult")).contains("about 80%").contains("prominent");
    }

    @Test
    @DisplayName("테마는 사용자의 그림을 대체하지 못한다")
    void themeNeverReplacesDrawing() {
        for (String stage : new String[]{"baby", "child", "adult"}) {
            assertThat(service.buildThemeText("metal-empire", stage))
                    .contains("never replace the player's drawing")
                    .contains("decoration, texture and small parts");
        }
    }

    @Test
    @DisplayName("성체는 눈에 띄는 변화, 보조 색 허용")
    void childEvolvesVisiblyAndAllowsAccentColors() {
        String p = service.buildOpenAiPrompt("trash", "child", true);
        assertThat(p).contains("clearly EVOLVED form").contains("noticeably bigger").contains("one signature feature");
        assertThat(p).contains("small accent colors");
    }

    // ── OpenAI 경로 ──────────────────────────────────────────

    private void mockOpenAi(String json) {
        ReflectionTestUtils.setField(service, "openAiClient", buildStringClient(json));
    }

    private static String openAiJson(byte[] png) {
        return "{\"data\":[{\"b64_json\":\"" + Base64.getEncoder().encodeToString(png) + "\"}]}";
    }

    @Test
    @DisplayName("유체 생성: OpenAI 결과를 DONE으로 저장하고 Stability는 호출하지 않는다")
    void baby_openAi_done() throws InterruptedException {
        byte[] png = "fake-png".getBytes();
        mockOpenAi(openAiJson(png));

        service.generateAsync("baby-job", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), "nature-spirits");
        Thread.sleep(300);

        ImageJobResult r = service.getJob("baby-job");
        assertThat(r.getStatus()).isEqualTo("DONE");
        assertThat(r.getImageDataUrl()).isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        verifyNoInteractions(stabilityClient);
    }

    @Test
    @DisplayName("진화 생성: 참조 이미지를 함께 보내도 DONE으로 저장된다")
    void evolved_withReference_done() throws InterruptedException {
        mockOpenAi(openAiJson("evolved".getBytes()));
        String sketch = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String reference = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[]{9, 9, 9});

        service.generateEvolvedAsync("evo-job", null, sketch, "metal-empire", "child", reference);
        Thread.sleep(300);

        assertThat(service.getJob("evo-job").getStatus()).isEqualTo("DONE");
        verifyNoInteractions(stabilityClient);
    }

    @Test
    @DisplayName("OpenAI가 빈 data를 돌려주면 FAILED")
    void emptyResponse_failed() throws InterruptedException {
        mockOpenAi("{\"data\":[]}");
        service.generateAsync("empty-job", Base64.getEncoder().encodeToString(new byte[]{1}), "trash");
        Thread.sleep(300);
        assertThat(service.getJob("empty-job").getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("OpenAI 호출 예외 시 FAILED")
    void apiError_failed() throws InterruptedException {
        WebClient client = buildStringClient(null);
        ReflectionTestUtils.setField(service, "openAiClient", client);
        service.generateAsync("err-job", Base64.getEncoder().encodeToString(new byte[]{1}), "trash");
        Thread.sleep(300);
        assertThat(service.getJob("err-job").getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("알파 이진화: 128 미만은 투명, 이상은 불투명으로 만든다")
    void binarizeAlpha() throws Exception {
        BufferedImage img = new BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, (10 << 24) | 0x112233);    // 흐린 번짐
        img.setRGB(1, 0, (100 << 24) | 0x112233);   // 반투명 (기준 미만)
        img.setRGB(2, 0, (130 << 24) | 0x112233);   // 반투명 (기준 이상)
        img.setRGB(3, 0, (250 << 24) | 0x112233);   // 거의 불투명
        ByteArrayOutputStream in = new ByteArrayOutputStream();
        ImageIO.write(img, "png", in);

        byte[] out = service.binarizeAlpha(in.toByteArray());

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(out));
        assertThat(result.getRGB(0, 0) >>> 24).isEqualTo(0);
        assertThat(result.getRGB(1, 0) >>> 24).isEqualTo(0);
        assertThat(result.getRGB(2, 0) >>> 24).isEqualTo(255);
        assertThat(result.getRGB(3, 0) >>> 24).isEqualTo(255);
        assertThat(result.getRGB(3, 0) & 0xFFFFFF).isEqualTo(0x112233); // 색은 유지
    }

    @Test
    @DisplayName("알파 이진화: 이미지가 아닌 바이트는 그대로 반환")
    void binarizeAlpha_invalidBytesPassThrough() {
        byte[] junk = "not-a-png".getBytes();
        assertThat(service.binarizeAlpha(junk)).isEqualTo(junk);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private WebClient buildStringClient(String body) {
        WebClient client = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(any(), any())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(
                body != null ? Mono.just(body) : Mono.error(new IllegalStateException("401 Unauthorized")));
        return client;
    }
}
