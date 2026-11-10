package com.tamagochi.tamagochi.service.character;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 잉크 진화 시안 v1(속성 재해석): 부위를 붙이지 않고 색·재질·실루엣을 바꾸며, 귀여움 기준을 지킨다 */
class InkStyleV1Test {

    private static final DoodleSubject CAT = new DoodleSubject("a cat", "animal", "ears, tail", "고양이", "pointed ears", "four-legged", null);

    @Test
    void v1은_속성_재해석_문장과_귀여움_기준을_쓴다() {
        String ink = LandCatalog.absorbedPrompt("wyvern", List.of(), "child", LandCatalog.INK_STYLE_V1);
        String prompt = ImagePromptV3.evolve("child", CAT, "nature-spirits", "green", "fur tufts", ink);
        assertThat(prompt).contains(LandCatalog.THEME_MARK).contains(ImagePromptV3.CUTE_ANCHOR)
                .doesNotContain("Replace exactly ONE body part");
    }

    @Test
    void v1_완성작은_강하고_무섭게_문장을_쓰지_않는다() {
        String ink = LandCatalog.absorbedPrompt("salamander", List.of(), "adult", LandCatalog.INK_STYLE_V1);
        String prompt = ImagePromptV3.evolve("adult", CAT, "nature-spirits", "green", "fur tufts", ink);
        assertThat(prompt).contains("FINAL form").doesNotContain("fierce expression");
    }

    @Test
    void v0와_낙서_단계는_지금과_같다() {
        assertThat(LandCatalog.absorbedPrompt("wyvern", List.of(), "child", LandCatalog.INK_STYLE_V0))
                .isEqualTo(LandCatalog.absorbedPrompt("wyvern", List.of(), "child"));
        assertThat(LandCatalog.absorbedPrompt("wyvern", List.of(), "baby", LandCatalog.INK_STYLE_V1))
                .isEqualTo(LandCatalog.absorbedPrompt("wyvern", List.of(), "baby"));
    }
}
