package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tamagochi.tamagochi.domain.land.LandCatalog;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 잉크 진화 시안 v5: 낙서 측면·스케치 아기 체형·완성작 디자인 후보 3개·격자 다시 찍기 */
class InkStyleV5Test {

    private static final DoodleSubject CAT = new DoodleSubject("a cat", "animal", "ears, tail", "고양이", "pointed ears", "four-legged", null);
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 낙서_단계는_측면과_귀여운_얼굴을_틀_문장_앞에_넣는다() {
        String p = ImagePromptV3.babyV5(CAT, "green");
        assertThat(p).contains(ImagePromptV3.SIDE_BABY_V5);
        assertThat(p.indexOf("SIDE VIEW")).isLessThan(p.indexOf("Single character centered"));
    }

    @Test
    void 스케치는_아기_체형_측면_진영은_색만_쓰고_비율_고정_문장은_없다() {
        String ink = LandCatalog.absorbedPrompt("wyvern", List.of(), "child", LandCatalog.INK_STYLE_V5);
        String p = ImagePromptV3.sketchV5(CAT, ink);
        assertThat(p).contains(ImagePromptV3.CHIBI_V5).contains(ImagePromptV3.SIDE_SKETCH_V5).contains(ImagePromptV3.FACTION_PARTS_V5)
                .contains("KEEP the body colors")
                .doesNotContain("Keep the proportions and body plan of image 1 exactly")
                .doesNotContain("pointed animal ears");
    }

    @Test
    void 잉크가_없는_스케치도_아기_체형을_쓴다() {
        assertThat(ImagePromptV3.sketchV5(CAT, null)).contains(ImagePromptV3.CHIBI_V5).doesNotContain("INK THEME");
    }

    @Test
    void 디자인_답에서_후보를_최대_3개_꺼내고_이름이나_그림_재료가_없는_후보는_버린다() {
        String content = "{\"candidates\":[" +
                "{\"nameKo\":\"전기 기사\",\"lineKo\":\"번개 망토의 기사\",\"body\":\"humanoid\",\"newForm\":\"Volt Knight\",\"bodyPlan\":\"upright\"}," +
                "{\"nameKo\":\"\",\"newForm\":\"x\",\"bodyPlan\":\"y\"}," +
                "{\"nameKo\":\"볼트 뱀\",\"lineKo\":\"감고 쉬는 번개 뱀\",\"body\":\"serpent\",\"newForm\":\"Volt Serpent\",\"bodyPlan\":\"coiled\"}," +
                "{\"nameKo\":\"위습\",\"lineKo\":\"떠 있는 전기 정령\",\"body\":\"spirit\",\"newForm\":\"Wisp\",\"bodyPlan\":\"floating\"}," +
                "{\"nameKo\":\"넷째\",\"lineKo\":\"-\",\"body\":\"beast\",\"newForm\":\"Fourth\",\"bodyPlan\":\"four legs\"}]}";
        ArrayNode arr = FinalDesignService.parse(om, content).orElseThrow();
        assertThat(arr).hasSize(3);
        assertThat(FinalDesignService.toChoices(arr)).extracting(FinalDesignService.Choice::name).containsExactly("전기 기사", "볼트 뱀", "위습");
    }

    @Test
    void 완성작_그림_문장은_고른_후보와_쉬는_자세_단순하게를_담는다() throws Exception {
        var c = om.readTree("{\"newForm\":\"Volt Serpent\",\"essence\":\"a calm guardian.\",\"bodyPlan\":\"coiled\",\"silhouette\":\"S\",\"face\":\"kind\"," +
                "\"palette\":\"green, yellow\",\"motifs\":[\"antenna -> crest\"],\"details\":[\"lightning mane\"]}");
        String p = ImagePromptV3.finalFromDesignV5(c);
        assertThat(p).contains("Volt Serpent").contains("antenna -> crest").contains("RESTING").contains("SIMPLE").contains("fully opaque");
    }

    @Test
    void 격자_다시_찍기는_크기를_그대로_두고_칸마다_한_색으로_채운다() throws Exception {
        BufferedImage src = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++)
            src.setRGB(x, y, x < 64 ? 0xFFFF0000 : ((x + y) % 2 == 0 ? 0xFF00FF00 : 0x00000000)); // 왼쪽 빨강, 오른쪽 초록/투명 섞임
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(src, "png", bos);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(PixelSnap.snap(bos.toByteArray(), 8)));
        assertThat(out.getWidth()).isEqualTo(128);
        assertThat(out.getRGB(3, 3)).isEqualTo(0xFFFF0000);
        assertThat(out.getRGB(10, 3)).isEqualTo(out.getRGB(14, 9)); // 같은 칸(16px)은 한 색
    }
}
