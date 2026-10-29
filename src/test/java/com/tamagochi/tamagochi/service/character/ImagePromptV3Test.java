package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePromptV3Test {

    private static final DoodleSubject PERSON = new DoodleSubject("a person", "person",
            "head, arms, legs", "사람", "zigzag arms, very large head", "biped");
    private static final String COLORS = "sand yellow and terracotta orange color scheme";
    private static final String MOTIFS = "desert traits: cactus spines";

    @Test
    @DisplayName("유체: 그림의 특징·몸 구조를 살리고, 탈것·동반·두 번째 몸을 만들지 않는다")
    void baby_keepsDrawingAndForbidsFusion() {
        String p = ImagePromptV3.baby(PERSON, COLORS);

        assertThat(p).startsWith("Create a 16-bit PIXEL ART sprite");
        assertThat(p).contains("zigzag arms, very large head").contains("Body plan: biped");
        assertThat(p).contains("no rider, no mount, no companion, no second head or body");
        assertThat(p).doesNotContain("riding on").doesNotContain("vehicle or mount");
        assertThat(p).contains(COLORS).contains("Do not add accessories, scarves, armor or props");
    }

    @Test
    @DisplayName("유체: 물체 그림은 물체 자체에 얼굴을 달아 캐릭터로 만든다")
    void baby_objectGetsFace() {
        DoodleSubject train = new DoodleSubject("a train engine", "object", "chimney, wheels", "기관차", null, "vehicle");
        String p = ImagePromptV3.baby(train, COLORS);

        assertThat(p).contains("itself is the character, NOT an animal").contains("simple cute pixel face");
        assertThat(p).contains("Body plan: vehicle").doesNotContain("distinctive features of the drawing");
    }

    @Test
    @DisplayName("성체: 직전 모습과 원본 낙서를 함께 보고, 크기만 키우지 않고 특징을 하나 더한다")
    void evolveChild_plain() {
        String p = ImagePromptV3.evolve("child", PERSON, COLORS, MOTIFS, null);

        assertThat(p).contains("Image 1 is the CURRENT form").contains("Image 2 is the original doodle");
        assertThat(p).contains("Origin of this creature: a person").contains("zigzag arms, very large head");
        assertThat(p).contains("recognizably the SAME character").contains("Add exactly ONE new signature feature");
        assertThat(p).contains("Keep the body plan of image 1");
        assertThat(p).doesNotContain("Absorbed ink");
    }

    @Test
    @DisplayName("완전체: 영웅 포즈·위압감을 강요하지 않고 몸 구조에 맞는 포즈를 고르게 한다")
    void evolveAdult_noGenericHeroPose() {
        String p = ImagePromptV3.evolve("adult", PERSON, COLORS, MOTIFS, null);

        assertThat(p).doesNotContain("strong, heroic pose").doesNotContain("imposing");
        assertThat(p).contains("Do NOT default to a generic standing hero pose");
        assertThat(p).contains("do not simplify or drop details");
        assertThat(p).contains("Use at most two of these motifs");
    }

    @Test
    @DisplayName("흡수한 잉크가 있는 완전체: 겉모습이 크게 달라질 수 있고 잉크 문장이 들어간다")
    void evolveAdult_withInk() {
        String ink = "Absorbed ink data chosen by the player: A chimera that clearly shows both.";
        String p = ImagePromptV3.evolve("adult", PERSON, COLORS, MOTIFS, ink);

        assertThat(p).contains(ink).contains("its outer design may change a lot");
        assertThat(p).contains("unless the absorbed ink below says otherwise");
    }

    @Test
    @DisplayName("흡수한 잉크가 있어도 성체는 몸 구조를 바꾸지 않는다")
    void evolveChild_withInk_keepsBodyPlan() {
        String p = ImagePromptV3.evolve("child", PERSON, COLORS, MOTIFS, "Absorbed ink data chosen by the player: x");

        assertThat(p).doesNotContain("unless the absorbed ink below says otherwise").doesNotContain("its outer design may change a lot");
    }

    @Test
    @DisplayName("진화 프롬프트의 진영 모티프에서 스카프·망토는 빠진다 (모든 캐릭터가 비슷해 보이는 원인)")
    void evolve_dropsScarves() {
        String motifs = "desert traits: cactus spines, a wrapped scarf, a scarab tail";
        String p = ImagePromptV3.evolve("adult", PERSON, COLORS, motifs, null);

        assertThat(p).contains("cactus spines").contains("a scarab tail").doesNotContain("wrapped scarf");
        assertThat(ImagePromptV3.withoutScarves("heroic: a hero emblem, a cape or scarf, a shield")).isEqualTo("heroic: a hero emblem, a shield");
    }

    @Test
    @DisplayName("저장된 그림 정체가 없어도(null) 진화 프롬프트를 만든다")
    void evolve_withoutOrigin() {
        String p = ImagePromptV3.evolve("child", null, COLORS, MOTIFS, null);

        assertThat(p).doesNotContain("Origin of this creature").contains("Image 2 is the original doodle");
    }
}
