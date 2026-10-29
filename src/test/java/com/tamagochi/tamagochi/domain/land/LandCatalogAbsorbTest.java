package com.tamagochi.tamagochi.domain.land;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LandCatalogAbsorbTest {

    private static String mainInkId() {
        return LandCatalog.regions().stream().flatMap(r -> r.inks().stream())
                .filter(i -> i.grade() != LandCatalog.Grade.FAMILY).findFirst().orElseThrow().id();
    }

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
    @DisplayName("성체: 단계 한도(50%) 때문에 부위 하나만 바뀌고 원래 정체를 유지한다")
    void child_replacesOnePart() {
        String p = LandCatalog.absorbedPrompt(mainInkId(), familyInkIds(2), "child");

        assertThat(p).contains("Replace exactly ONE body part").contains("Keep the doodle's identity");
        assertThat(p).doesNotContain("chimera").doesNotContain("companion");
    }

    @Test
    @DisplayName("완전체: 대표 잉크만으로도 얼굴 유지·부위 이식·동반 중 하나로 크게 바뀐다")
    void adult_mainOnly_strongMode() {
        String p = LandCatalog.absorbedPrompt(mainInkId(), List.of(), "adult");

        assertThat(p).doesNotContain("Replace exactly ONE body part").doesNotContain("Keep the doodle's identity");
        assertThat(p).containsAnyOf("keeps the ORIGIN's face", "keeps the ORIGIN's own version", "accompanied by the ORIGIN");
        assertThat(p).doesNotContain("chimera");
    }

    @Test
    @DisplayName("완전체 + 계열 잉크 2개(85%): 키메라도 고를 수 있고, 같은 입력은 같은 결과")
    void adult_full_deterministic() {
        String a = LandCatalog.absorbedPrompt(mainInkId(), familyInkIds(2), "adult");
        String b = LandCatalog.absorbedPrompt(mainInkId(), familyInkIds(2), "adult");

        assertThat(a).isEqualTo(b);
        assertThat(a).contains("texture, pattern and mood");
    }

    @Test
    @DisplayName("계열 잉크만 고르면 질감·분위기만 반영하고 몸은 바꾸지 않는다")
    void familyOnly_textureOnly() {
        String p = LandCatalog.absorbedPrompt(null, familyInkIds(2), "adult");

        assertThat(p).contains("texture, pattern and mood").doesNotContain("truly");
    }

    @Test
    @DisplayName("합치는 방식 문장: 구간별로 달라진다")
    void mainText_bands() {
        assertThat(LandCatalog.mainText("g", 10, 0)).isEmpty();
        assertThat(LandCatalog.mainText("g", 20, 0)).contains("Decoration only");
        assertThat(LandCatalog.mainText("g", 40, 0)).contains("Replace exactly ONE body part");
        assertThat(LandCatalog.mainText("g", 60, 0)).contains("keeps the ORIGIN's face");
        assertThat(LandCatalog.mainText("g", 60, 1)).contains("keeps the ORIGIN's own version");
        assertThat(LandCatalog.mainText("g", 60, 2)).contains("accompanied by the ORIGIN");
        assertThat(LandCatalog.mainText("g", 60, 3)).contains("keeps the ORIGIN's face"); // 60%는 3가지 중에서만 고른다
        assertThat(LandCatalog.mainText("g", 85, 3)).contains("chimera");
    }
}
