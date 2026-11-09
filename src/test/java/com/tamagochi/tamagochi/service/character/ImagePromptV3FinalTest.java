package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePromptV3FinalTest {

    private final DoodleSubject cat = new DoodleSubject("an orange tabby cat", "animal", "pointed ears, long tail", "고양이",
            "orange stripes, pointed ears, long curled tail", "quadruped");

    @Test
    void 디자인_지시는_대표_특징을_지키고_몸_구조는_열어둔다() {
        String p = ImagePromptV3.finalDesigner(cat);
        assertThat(p).contains("orange stripes, pointed ears, long curled tail")
                .contains("NOT a collage").contains("BODY PLAN is NOT part of identity").contains("ONE single creature");
    }

    @Test
    void 설계서대로_그리는_프롬프트() throws Exception {
        var brief = new ObjectMapper().readTree("""
                {"concept":"storm falcon cat knight","palette":"orange, silver, ice blue, gold","bodyPlan":"upright biped",
                 "silhouette":"tall","focal":"glowing eyes","parts":["ice wing pauldrons","glove gauntlet"]}""");
        String p = ImagePromptV3.finalFromBrief(brief);
        assertThat(p).startsWith(ImageGenerationService.PIXEL_STYLE)
                .contains("storm falcon cat knight").contains("ice wing pauldrons glove gauntlet")
                .contains("never a front view").contains("transparent background");
    }
}
