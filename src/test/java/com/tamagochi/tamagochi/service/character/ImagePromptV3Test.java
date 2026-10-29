package com.tamagochi.tamagochi.service.character;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePromptV3Test {

    private static final DoodleSubject PERSON = new DoodleSubject("a person", "person",
            "head, arms, legs", "사람", "zigzag arms, very large head", "biped");
    private static final String COLORS = "sand yellow and terracotta orange color scheme";
    private static final String MOTIFS = "desert traits: cactus spines";

    private static String evolve(String stage, String absorbed) {
        return ImagePromptV3.evolve(stage, PERSON, "desert", COLORS, MOTIFS, absorbed);
    }

    @Test
    @DisplayName("낙서: 그림의 특징·몸 구조를 살리고, 탈것·동반·두 번째 몸을 만들지 않는다")
    void baby_keepsDrawingAndForbidsFusion() {
        String p = ImagePromptV3.baby(PERSON, COLORS);

        assertThat(p).startsWith("Create a 16-bit PIXEL ART sprite");
        assertThat(p).contains("zigzag arms, very large head").contains("Body plan: biped");
        assertThat(p).contains("no rider, no mount, no companion, no second head or body");
        assertThat(p).doesNotContain("riding on").doesNotContain("vehicle or mount");
        assertThat(p).contains(COLORS).contains("Do not add accessories, scarves, armor or props");
    }

    @Test
    @DisplayName("낙서: 물체 그림은 물체 자체에 얼굴을 달아 캐릭터로 만든다")
    void baby_objectGetsFace() {
        DoodleSubject train = new DoodleSubject("a train engine", "object", "chimney, wheels", "기관차", null, "vehicle");
        String p = ImagePromptV3.baby(train, COLORS);

        assertThat(p).contains("itself is the character, NOT an animal").contains("simple cute pixel face");
        assertThat(p).contains("Body plan: vehicle").doesNotContain("distinctive features of the drawing");
    }

    @Test
    @DisplayName("스케치: 직전 모습과 원본 낙서를 함께 보고, 크기만 키우지 않고 특징을 하나 더한다")
    void evolveChild_plain() {
        String p = evolve("child", null);

        assertThat(p).contains("Image 1 is the CURRENT form").contains("Image 2 is the original doodle");
        assertThat(p).contains("Origin of this creature: a person").contains("zigzag arms, very large head");
        assertThat(p).contains("recognizably the SAME character").contains("Add exactly ONE new signature feature");
        assertThat(p).contains("Keep the body plan of image 1");
        assertThat(p).doesNotContain("Absorbed ink");
    }

    @Test
    @DisplayName("완성작: 정규 진화처럼 더 강하고 멋있게 — 체격·표정·디테일·대비가 달라지되 개성은 남긴다")
    void evolveAdult_feelsLikeEvolution() {
        String p = evolve("adult", null);

        assertThat(p).contains("a true evolution").contains("visibly stronger, cooler and more mature than image 1");
        assertThat(p).contains("sharper face and a more confident or fierce expression").contains("strengthen the color contrast");
        assertThat(p).contains("distinctive features listed in the origin above must still be clearly visible");
        assertThat(p).contains("not a generic cute mascot and not a generic standing hero");
        assertThat(p).doesNotContain("strong, heroic pose").doesNotContain("imposing, richly detailed");
        assertThat(p).contains("do not simplify or drop details");
    }

    @Test
    @DisplayName("스카프·망토는 기사 진영에서만 모티프로 쓴다")
    void scarvesOnlyForKnightFaction() {
        String motifs = "desert traits: cactus spines, a wrapped scarf, a scarab tail, a cape or scarf";

        String other = ImagePromptV3.evolve("adult", PERSON, "desert", COLORS, motifs, null);
        String knights = ImagePromptV3.evolve("adult", PERSON, "virus-busters", COLORS, motifs, null);

        assertThat(other).contains("cactus spines").contains("a scarab tail").doesNotContain("scarf").doesNotContain("cape");
        assertThat(knights).contains("a wrapped scarf").contains("a cape or scarf");
        assertThat(ImagePromptV3.withoutScarves("heroic: a hero emblem, a cape or scarf, a shield")).isEqualTo("heroic: a hero emblem, a shield");
    }

    @Test
    @DisplayName("강한 구간 잉크: 자리표시를 원본 이름·특징으로 채우고, 겉모습이 크게 바뀔 수 있다고 알린다")
    void evolveAdult_strongInkFillsOrigin() {
        String ink = LandCatalog.absorbedPrompt("combining-robot", List.of(), "adult");

        String p = evolve("adult", ink);

        assertThat(ink).contains(LandCatalog.ORIGIN_TOKEN);
        assertThat(p).doesNotContain(LandCatalog.ORIGIN_TOKEN).doesNotContain(LandCatalog.TRAITS_TOKEN);
        assertThat(p).contains("a person").contains("its outer design may change a lot");
    }

    @Test
    @DisplayName("체형을 바꾸는 잉크는 '이전 체형 유지' 문장을 빼고 하체 분류를 쓴다")
    void bodyChange_replacesKeepBodyPlan() {
        String p = evolve("adult", LandCatalog.absorbedPrompt("tank", List.of(), "adult"));

        assertThat(p).contains("Change its lower body type to a TANK-LIKE lower body");
        assertThat(p).contains("HARD RULE: ZERO legs are visible");
        assertThat(p).doesNotContain("Keep the body plan of image 1");
    }

    @Test
    @DisplayName("스케치(한도 50%)에서는 같은 잉크도 부위만 바뀌고 체형 유지 문장이 남는다")
    void evolveChild_inkKeepsBodyPlan() {
        String p = evolve("child", LandCatalog.absorbedPrompt("tank", List.of(), "child"));

        assertThat(p).contains("Replace exactly ONE body part").contains("Keep the body plan of image 1");
        assertThat(p).doesNotContain(LandCatalog.BODY_CHANGE_MARK).doesNotContain("its outer design may change a lot");
    }

    @Test
    @DisplayName("저장된 그림 정체가 없어도(null) 진화 프롬프트를 만든다")
    void evolve_withoutOrigin() {
        String p = ImagePromptV3.evolve("child", null, "desert", COLORS, MOTIFS, null);

        assertThat(p).doesNotContain("Origin of this creature").contains("Image 2 is the original doodle");
    }

    private static final DoodleSubject SWORD_TOOL = new DoodleSubject("a sword", "tool", "a blade, a crossguard", "칼", "red zigzag handle", "other", "ears");

    @Test
    @DisplayName("도구(칼·방패): 도구에 얼굴을 붙이지 않고 몸의 한 부분이 된 생물로 만든다")
    void babyTool_becomesBodyPart() {
        String p = ImagePromptV3.baby(SWORD_TOOL, "silver and gold");

        assertThat(p).contains("Do NOT draw the a sword itself with a face stuck on it");
        assertThat(p).contains("two long, narrow, pointed EARS").contains("red zigzag handle").contains("silver and gold");
        assertThat(p).doesNotContain("tiny stubby arms and legs");
    }

    @Test
    @DisplayName("도구가 되는 부위: 껍질·손·날개, 모르면 귀")
    void toolPart_byAttach() {
        assertThat(ImagePromptV3.toolPart("shell", "a shield")).contains("SHELL");
        assertThat(ImagePromptV3.toolPart("hands", "a hammer")).contains("HANDS");
        assertThat(ImagePromptV3.toolPart("wings", "a fan")).contains("wing-like fins");
        assertThat(ImagePromptV3.toolPart(null, "a sword")).contains("EARS");
        assertThat(ImagePromptV3.toolPart("???", "a sword")).contains("EARS");
    }

    @Test
    @DisplayName("기차·자동차 같은 완성된 물체(object)는 지금처럼 하나의 캐릭터로 만든다")
    void finishedObject_keepsOldWay() {
        DoodleSubject train = new DoodleSubject("a train engine", "object", "chimney, wheels", "기관차", null, "vehicle");

        String p = ImagePromptV3.baby(train, "silver");

        assertThat(p).contains("itself is the character").contains("tiny stubby arms and legs").doesNotContain("EARS");
    }

    @Test
    @DisplayName("도구를 진화시킬 때는 손에 든 무기가 아니라 몸의 일부로 남긴다")
    void evolveTool_staysBodyPart() {
        String p = ImagePromptV3.evolve("child", SWORD_TOOL, "metal-empire", "silver", "plates", null);

        assertThat(p).contains("is NOT a weapon or object the creature holds").contains("must NOT hold the a sword in its hand");
        assertThat(ImagePromptV3.evolve("child", PERSON, "metal-empire", "silver", "plates", null)).doesNotContain("NOT a weapon");
    }
}
