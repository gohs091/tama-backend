package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tamagochi.tamagochi.domain.land.LandCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 잉크 진화 시안 v6: v5에서 스케치만 바꾼다 (한 줄 설명 후보 3개 → 고른 하나를 새로 그린다) */
class InkStyleV6Test {

    private static final DoodleSubject CAT = new DoodleSubject("a cat", "animal", "ears, tail", "고양이", "pointed ears, long whiskers", "quadruped", null);
    private final ObjectMapper om = new ObjectMapper();

    @Test
    @DisplayName("v6은 스케치만 다른 v5다: 낙서·완성작·자는 모습은 v5 방식을 그대로 쓴다")
    void v6IsV5ExceptSketch() {
        assertThat(LandCatalog.isV6("v6")).isTrue();
        assertThat(LandCatalog.isV5("v6")).isTrue();
        assertThat(LandCatalog.isV6("v5")).isFalse();
        assertThat(LandCatalog.isV5("v5")).isTrue();
        assertThat(LandCatalog.isV5("v0")).isFalse();

        ImageGenerationService service = new ImageGenerationService(om);
        ReflectionTestUtils.setField(service, "inkStyle", LandCatalog.INK_STYLE_V6);
        assertThat(service.rebornFinal(com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage.ADULT)).isTrue();
    }

    @Test
    @DisplayName("그림 문장: 화풍 참고 두 장과 아기 그림을 구분해 알리고, 진영 이름과 한 줄 설명을 담는다")
    void drawPromptCarriesTheLine() {
        String line = "An olive-green cat with a sleeker body, wearing a tiny mossy leaf crown.";
        String p = ImagePromptV3.sketchV6("nature-spirits", line);

        assertThat(p).startsWith("Create a 16-bit PIXEL ART sprite");
        assertThat(p).contains("Images 1 and 2 are STYLE REFERENCES only").contains("Image 3 is THIS pet as a baby");
        assertThat(p).contains("from the forest spirits theme: " + line);
        assertThat(p).contains("grown up in exactly the way the description says");
        assertThat(p).contains("two-tone markings").contains(ImagePromptV3.SOLID_V5);
    }

    @Test
    @DisplayName("그림 문장: 쉬는 자세로 그린다 (걷는 발·사랑스러운 자세 문구는 없다)")
    void drawPromptRests() {
        String p = ImagePromptV3.sketchV6("deep-savers", "A blue rabbit.");

        assertThat(p).contains(ImagePromptV3.REST_V6).contains("RESTING");
        assertThat(p).doesNotContain("stepping").doesNotContain("a sweet pose");
    }

    @Test
    @DisplayName("그림 문장과 디자이너 지시는 체형을 정하거나 막지 않는다 (통통하게·마르지 않게 같은 말이 없다)")
    void noBodyTypeRules() {
        // 맨 앞의 도트 화풍 문장("chunky square pixels")은 몸 이야기가 아니라서 뺀다
        String draw = ImagePromptV3.sketchV6("nature-spirits", "A cat.").substring(ImageGenerationService.PIXEL_STYLE.length());
        String all = (draw + ImagePromptV3.SKETCH_DESIGNER_V6).toLowerCase();

        // 낱말 단위로 본다 ("thing" 안의 "thin"은 해당 없음)
        for (String word : new String[]{"chunky", "chubby", "sturdy", "sturdier", "lanky", "thin", "slim", "chibi", "baby-animal"}) {
            assertThat(all).as(word).doesNotContainPattern("\\b" + word + "\\b");
        }
        assertThat(ImagePromptV3.SKETCH_DESIGNER_V6).contains("There is no single right way to grow up").contains("each grown in a clearly different direction");
    }

    @Test
    @DisplayName("모르는 진영이면 진영 이름 없이 그린다")
    void unknownFaction() {
        assertThat(ImagePromptV3.sketchThemeV6("no-such-faction")).isEmpty();
        assertThat(ImagePromptV3.sketchThemeV6(null)).isEmpty();
        assertThat(ImagePromptV3.sketchV6(null, "A cat.")).contains("Draw ONE tamagotchi-style character: A cat.");
    }

    @Test
    @DisplayName("디자이너 쪽지: 무엇인지·낙서의 별난 점·진영·잉크를 담고, 잉크가 없으면 진영이 주제라고 알린다")
    void designerNote() {
        String withInk = ImagePromptV3.sketchNoteV6(CAT, "nature-spirits", "a wyvern's bat-like wings");
        String noInk = ImagePromptV3.sketchNoteV6(CAT, "nature-spirits", null);

        assertThat(withInk).contains("What it is: a cat.").contains("pointed ears, long whiskers").contains("Its tribe: forest spirits.").contains("Ink: a wyvern's bat-like wings");
        assertThat(noInk).contains("Ink: none (its tribe is the theme)");
        assertThat(ImagePromptV3.sketchNoteV6(null, null, null)).contains("the creature in the doodle").doesNotContain("Its tribe");
    }

    @Test
    @DisplayName("글 모델이 실패했을 때 쓰는 기본 한 줄: 무엇인지와 잉크를 담는다")
    void fallbackLine() {
        assertThat(ImagePromptV3.sketchFallbackLineV6(CAT, null)).startsWith("A cat, one stage older").endsWith(".");
        assertThat(ImagePromptV3.sketchFallbackLineV6(CAT, "fire salamander skin")).contains("fire salamander skin");
        assertThat(ImagePromptV3.sketchFallbackLineV6(null, null)).startsWith("The creature of image 3");
    }

    @Test
    @DisplayName("디자인 답에서 후보를 최대 3개 꺼내고, 그림 한 줄이나 한글 한 줄이 빈 후보는 버린다")
    void parseCandidates() {
        String content = "{\"candidates\":[" +
                "{\"line\":\"A sleek cat with a leaf crown.\",\"lineKo\":\"이끼 왕관 썼어요\"}," +
                "{\"line\":\"\",\"lineKo\":\"빈 그림 문장\"}," +
                "{\"line\":\"A plump cat with a twig.\",\"lineKo\":\"\"}," +
                "{\"line\":\"A lanky cat with a leaf scarf.\",\"lineKo\":\"잎 스카프 둘렀어요\"}," +
                "{\"line\":\"A fluffy cat with a cape.\",\"lineKo\":\"이끼 망토 걸쳤어요\"}," +
                "{\"line\":\"A fourth.\",\"lineKo\":\"넷째\"}]}";

        ArrayNode arr = SketchDesignService.parse(om, content).orElseThrow();

        assertThat(SketchDesignService.toChoices(arr)).extracting(SketchDesignService.Choice::line)
                .containsExactly("이끼 왕관 썼어요", "잎 스카프 둘렀어요", "이끼 망토 걸쳤어요");
        assertThat(SketchDesignService.parse(om, "not json")).isEmpty();
        assertThat(SketchDesignService.parse(om, "{\"candidates\":[]}")).isEmpty();
    }

    @Test
    @DisplayName("번호 없이 그리면 지난번과 다른 후보를 고른다 (다시 그리면 다른 모습)")
    void pickAvoidsLast() {
        for (int last = 0; last < 3; last++) {
            Set<Integer> seen = new HashSet<>();
            for (int r = 0; r < 2; r++) {
                int pick = r;
                seen.add(SketchDesignService.pickIndex(3, last, n -> pick));
            }
            assertThat(seen).hasSize(2).doesNotContain(last);
        }
        assertThat(SketchDesignService.pickIndex(3, -1, n -> 2)).isEqualTo(2);   // 처음이면 셋 중 아무거나
        assertThat(SketchDesignService.pickIndex(1, 0, n -> 0)).isZero();        // 하나뿐이면 그 하나
    }

    @Test
    @DisplayName("저장된 후보는 잉크가 같을 때만 다시 쓴다 (잉크를 바꾸면 새로 만든다)")
    void reuseOnlyWithSameInk() throws Exception {
        ObjectNode saved = (ObjectNode) om.readTree("{\"ink\":\"wyvern wings\",\"last\":0,\"candidates\":[{\"line\":\"x\",\"lineKo\":\"엑스\"}]}");

        assertThat(SketchDesignService.reusable(saved, "wyvern wings", false)).isTrue();
        assertThat(SketchDesignService.reusable(saved, "fire skin", false)).isFalse();
        assertThat(SketchDesignService.reusable(saved, null, false)).isFalse();
        assertThat(SketchDesignService.reusable(saved, "wyvern wings", true)).isFalse();   // 다시 만들라는 요청
        assertThat(SketchDesignService.reusable(null, null, false)).isFalse();

        ObjectNode noInk = (ObjectNode) om.readTree("{\"ink\":\"\",\"last\":-1,\"candidates\":[{\"line\":\"x\",\"lineKo\":\"엑스\"}]}");
        assertThat(SketchDesignService.reusable(noInk, null, false)).isTrue();
    }

    @Test
    @DisplayName("화풍 참고 그림 두 장이 서버에 들어 있다")
    void styleReferencesAreBundled() {
        assertThat(ImageGenerationService.sketchStyle(1)).hasSizeGreaterThan(1000);
        assertThat(ImageGenerationService.sketchStyle(2)).hasSizeGreaterThan(1000);
    }
}
