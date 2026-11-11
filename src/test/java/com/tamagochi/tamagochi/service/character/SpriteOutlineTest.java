package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpriteOutlineTest {

    private static final int OUTLINE = 0xFF1E0A2E, PURPLE = 0xFF7B3F9E, SHADE = 0xFF5C2A85;

    /** 투명 배경 위 네모: 가장자리 2픽셀은 edge 색, 안쪽은 body 색 */
    private static byte[] sprite(int edge, int body) throws Exception {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 32; y++) for (int x = 8; x < 32; x++) {
            boolean onEdge = x < 10 || y < 10 || x >= 30 || y >= 30;
            img.setRGB(x, y, onEdge ? edge : body);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    @DisplayName("원래 그림엔 어두운 테두리가 있는데 새 그림 가장자리가 몸 색(조금 어두운 음영 포함)이면 테두리를 잃은 것")
    void detectsLostOutline() throws Exception {
        byte[] awake = sprite(OUTLINE, PURPLE);
        assertThat(SpriteOutline.lost(awake, sprite(PURPLE, PURPLE))).isTrue();
        assertThat(SpriteOutline.lost(awake, sprite(SHADE, PURPLE))).isTrue();    // 몸 색의 음영은 테두리가 아니다
    }

    @Test
    @DisplayName("새 그림에도 테두리가 있으면(색이 조금 달라도) 잃지 않은 것")
    void keepsOutlinedResult() throws Exception {
        byte[] awake = sprite(OUTLINE, PURPLE);
        assertThat(SpriteOutline.lost(awake, sprite(OUTLINE, PURPLE))).isFalse();
        assertThat(SpriteOutline.lost(awake, sprite(0xFF2A1438, PURPLE))).isFalse();
    }

    @Test
    @DisplayName("원래부터 테두리가 뚜렷하지 않은 그림(검은 몸 등)이나 읽을 수 없는 값은 판단하지 않는다")
    void skipsWhenReferenceHasNoOutline() throws Exception {
        assertThat(SpriteOutline.lost(sprite(0xFF202020, 0xFF2A2A2A), sprite(PURPLE, PURPLE))).isFalse();
        assertThat(SpriteOutline.lost(new byte[]{1, 2}, sprite(PURPLE, PURPLE))).isFalse();
        assertThat(SpriteOutline.lost(sprite(OUTLINE, PURPLE), new byte[0])).isFalse();
    }
}
