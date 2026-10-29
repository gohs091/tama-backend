package com.tamagochi.tamagochi.domain.land;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LandCatalogAbsorbTest {

    private static List<String> familyInkIds(int n) {
        return LandCatalog.regions().stream().flatMap(r -> r.inks().stream())
                .filter(i -> i.grade() == LandCatalog.Grade.FAMILY).map(LandCatalog.Ink::id).distinct().limit(n).toList();
    }

    @Test
    @DisplayName("흡수 비율: 잉크 개수로 정하고 단계 한도를 넘지 않는다 (유체 15 / 성체 50 / 완전체 90)")
    void ratio_capsByStage() {
        assertThat(LandCatalog.absorbRatio(2, "baby")).isEqualTo(15);
        assertThat(LandCatalog.absorbRatio(4, "child")).isEqualTo(50);
        assertThat(LandCatalog.absorbRatio(2, "child")).isEqualTo(50);
        assertThat(LandCatalog.absorbRatio(2, "adult")).isEqualTo(60);
        assertThat(LandCatalog.absorbRatio(3, "adult")).isEqualTo(85);
        assertThat(LandCatalog.absorbRatio(9, "adult")).isEqualTo(85);
        assertThat(LandCatalog.absorbRatio(0, "adult")).isZero();
    }

    @Test
    @DisplayName("고른 잉크가 없으면 null")
    void noInk_null() {
        assertThat(LandCatalog.absorbedPrompt(null, List.of(), "adult")).isNull();
    }

    @Test
    @DisplayName("성체: 단계 한도(50%) 때문에 부위 하나만 바뀌고 체형·정체를 유지한다 (체형을 바꾸는 잉크도 마찬가지)")
    void child_replacesOnePart() {
        String p = LandCatalog.absorbedPrompt("tank", familyInkIds(2), "child");

        assertThat(p).contains("Replace exactly ONE body part").contains("Keep the doodle's identity");
        assertThat(p).contains("a small turret cannon on the back").doesNotContain("caterpillar tracks");  // 부위 문구만 쓴다
        assertThat(p).doesNotContain(LandCatalog.BODY_CHANGE_MARK).doesNotContain(LandCatalog.ORIGIN_TOKEN);
    }

    @Test
    @DisplayName("완전체 + 체형 잉크(희귀): 하체 분류가 바뀌고 다리 개수를 못박는다")
    void adult_rareInk_changesBody() {
        String p = LandCatalog.absorbedPrompt("tank", List.of(), "adult");

        assertThat(p).doesNotContain("Replace exactly ONE body part").doesNotContain("Keep the doodle's identity");
        assertThat(p).contains(LandCatalog.ORIGIN_TOKEN);
        assertThat(p).contains("Change its lower body type to a TANK-LIKE lower body").contains("HARD RULE: ZERO legs are visible");
        assertThat(p).contains("caterpillar tracks");   // 강한 구간에서는 잉크의 전체 문구를 쓴다
    }

    @Test
    @DisplayName("체형 분류: 전설 잉크는 해당 분류로, 흔한 잉크는 체형을 바꾸지 않는다")
    void bodyClass_byGrade() {
        assertThat(LandCatalog.absorbedPrompt("leviathan", List.of(), "adult")).contains("a LEGLESS single-trunk body").contains("ZERO");
        assertThat(LandCatalog.absorbedPrompt("t-rex", List.of(), "adult")).contains("a REVERSE-JOINTED lower body");
        assertThat(LandCatalog.absorbedPrompt("lion-king", List.of(), "adult")).contains("a FOUR-LEGGED lower body");
        assertThat(LandCatalog.absorbedPrompt("rocket", List.of(), "adult")).contains("a WINGED FLYING build");
        // 흔한 잉크는 체형 분류가 없다
        assertThat(LandCatalog.absorbedPrompt("rabbit", List.of(), "adult")).doesNotContain(LandCatalog.BODY_CHANGE_MARK);
        assertThat(LandCatalog.absorbedPrompt("car", familyInkIds(2), "adult")).doesNotContain(LandCatalog.BODY_CHANGE_MARK);
    }

    @Test
    @DisplayName("흔한 잉크는 동반·키메라 없이 얼굴 유지/부위 이식만 쓴다")
    void commonInk_onlyFaceOrPart() {
        for (String id : List.of("rabbit", "fox", "deer", "crab", "owl", "helicopter", "cat", "puppy")) {
            String p = LandCatalog.absorbedPrompt(id, familyInkIds(2), "adult");   // 85%
            assertThat(p).contains(LandCatalog.ORIGIN_TOKEN);
            assertThat(p).doesNotContain("companion").doesNotContain("chimera");
        }
    }

    @Test
    @DisplayName("같은 입력은 같은 결과 / 계열 잉크는 질감·분위기만")
    void deterministic_andFamilyTexture() {
        String a = LandCatalog.absorbedPrompt("t-rex", familyInkIds(2), "adult");
        String b = LandCatalog.absorbedPrompt("t-rex", familyInkIds(2), "adult");

        assertThat(a).isEqualTo(b);
        assertThat(a).contains("texture, pattern and mood");
    }

    @Test
    @DisplayName("계열 잉크만 고르면 질감·분위기만 반영하고 몸은 바꾸지 않는다")
    void familyOnly_textureOnly() {
        String p = LandCatalog.absorbedPrompt(null, familyInkIds(2), "adult");

        assertThat(p).contains("texture, pattern and mood").doesNotContain("truly").doesNotContain(LandCatalog.BODY_CHANGE_MARK);
    }

    @Test
    @DisplayName("합치는 방식 문장: 구간·등급별로 달라진다")
    void mainText_bands() {
        LandCatalog.Grade rare = LandCatalog.Grade.RARE;
        assertThat(LandCatalog.mainText("full", "part", 10, 0, rare)).isEmpty();
        assertThat(LandCatalog.mainText("full", "part", 20, 0, rare)).contains("Decoration only").contains("part");
        assertThat(LandCatalog.mainText("full", "part", 40, 0, rare)).contains("Replace exactly ONE body part").contains("truly has part");
        assertThat(LandCatalog.mainText("full", "part", 60, 0, rare)).contains("keeps the face and head of " + LandCatalog.ORIGIN_TOKEN);
        assertThat(LandCatalog.mainText("full", "part", 60, 1, rare)).contains("feathered wings");
        assertThat(LandCatalog.mainText("full", "part", 60, 2, rare)).contains("MUST be clearly visible");
        assertThat(LandCatalog.mainText("full", "part", 60, 3, rare)).contains("keeps the face and head");   // 희귀 60%는 3가지 안에서만
        assertThat(LandCatalog.mainText("full", "part", 85, 3, LandCatalog.Grade.LEGEND)).contains("chimera");
        assertThat(LandCatalog.mainText("full", "part", 85, 3, rare)).doesNotContain("chimera");
    }

    @Test
    @DisplayName("자리표시 채우기: 원본 이름·특징으로, 모르면 일반 표현으로")
    void fillOrigin() {
        String text = "keeps the face of " + LandCatalog.ORIGIN_TOKEN + " with its shape" + LandCatalog.TRAITS_TOKEN + ".";

        assertThat(LandCatalog.fillOrigin(text, "a crow", "sharp beak")).isEqualTo("keeps the face of a crow with its shape (sharp beak).");
        assertThat(LandCatalog.fillOrigin(text, null, " ")).isEqualTo("keeps the face of the ORIGIN creature with its shape.");
        assertThat(LandCatalog.fillOrigin(null, "a crow", null)).isNull();
    }
}
