package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class LocalImageCompositeServiceTest {

    private final LocalImageCompositeService service = new LocalImageCompositeService();

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    // ----------------------------------------------------------------
    // 테스트용 스케치 이미지 생성 헬퍼 (흰 배경 + 검은 원)
    // ----------------------------------------------------------------
    private static String makeSketchBase64(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.setColor(new Color(30, 30, 30));
        g.setStroke(new BasicStroke(3f));
        g.drawOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // ----------------------------------------------------------------
    // 기본 파이프라인 검증: 결과물이 유효한 PNG base64인지 확인
    // ----------------------------------------------------------------
    @Test
    @DisplayName("기본 파이프라인 - 유효한 PNG base64 반환")
    void generate_returnsValidPngBase64() throws Exception {
        String sketchBase64 = makeSketchBase64(256, 256);
        String result = service.generate(sketchBase64, "nature-spirits");

        assertThat(result).startsWith("data:image/png;base64,");
        String raw = result.substring("data:image/png;base64,".length());
        byte[] bytes = Base64.getDecoder().decode(raw);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    // ----------------------------------------------------------------
    // 7대 팩션 모두 에러 없이 처리되는지 확인
    // ----------------------------------------------------------------
    @ParameterizedTest
    @ValueSource(strings = {
        "nature-spirits", "deep-savers", "nightmare-soldiers",
        "wind-guardians", "metal-empire", "virus-busters", "dragons-roar",
        "jungle-troopers", "dark-area", "trash"
    })
    @DisplayName("팩션별 배경 생성 - 예외 없음")
    void generate_allFactions_noException(String faction) throws Exception {
        String sketchBase64 = makeSketchBase64(200, 200);
        assertThatCode(() -> service.generate(sketchBase64, faction))
                .doesNotThrowAnyException();
    }

    // ----------------------------------------------------------------
    // data: 접두사가 붙은 base64도 올바르게 처리하는지 확인
    // ----------------------------------------------------------------
    @Test
    @DisplayName("data:image/png;base64, 접두사 포함 입력 처리")
    void generate_withDataUrlPrefix() throws Exception {
        String raw = makeSketchBase64(128, 128);
        String withPrefix = "data:image/png;base64," + raw;
        String result = service.generate(withPrefix, "virus-busters");
        assertThat(result).startsWith("data:image/png;base64,");
    }

    // ----------------------------------------------------------------
    // 정사각형이 아닌 스케치도 올바르게 512×512로 출력되는지 확인
    // ----------------------------------------------------------------
    @Test
    @DisplayName("비정방형 스케치 - 출력은 512x512")
    void generate_nonSquareSketch_outputIs512() throws Exception {
        String sketchBase64 = makeSketchBase64(300, 150); // 가로 직사각형
        String result = service.generate(sketchBase64, "dragons-roar");
        String raw = result.substring("data:image/png;base64,".length());
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(raw)));
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    // ----------------------------------------------------------------
    // 잘못된 base64 입력 시 예외 발생 확인
    // ----------------------------------------------------------------
    @Test
    @DisplayName("유효하지 않은 이미지 데이터 - IllegalArgumentException")
    void generate_invalidImage_throwsException() {
        String garbage = Base64.getEncoder().encodeToString("not-an-image".getBytes());
        assertThatThrownBy(() -> service.generate(garbage, "nature-spirits"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 이미지");
    }
}
