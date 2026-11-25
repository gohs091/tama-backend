package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 자는 모습을 PixelLab으로: 보내는 첫 장과, 받은 마지막 장을 앱이 쓰는 그림으로 맞추는 부분 (PixelLab 호출 자체는 여기서 부르지 않는다) */
class PixelLabSleepServiceTest {

    private static final int GREEN = 0xFF84A35A, INK = 0xFF301818;
    private static final int GRID = PixelLabSleepService.GRID, CANVAS = PixelLabSleepService.CANVAS;
    private static final int PAD_X = PixelLabSleepService.PAD_X, PAD_TOP = PixelLabSleepService.PAD_TOP;

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private static BufferedImage read(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    /** 캔버스에 칸 단위 사각형 하나 (도트 한 칸 = 1픽셀) */
    private static byte[] frame(int x, int y, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) img.setRGB(xx, yy, GREEN);
        return png(img);
    }

    @Test
    @DisplayName("첫 장: 끄적이 그림을 64칸(도트 한 칸 = 1픽셀)으로 줄여 112칸 캔버스의 정해진 자리에 놓는다")
    void firstFrameIsOneDotPerPixelOnWiderCanvas() throws Exception {
        // 1024px 그림: 한 칸이 16px. 칸 (10,20)은 테두리 색, 칸 (11,20)은 몸 색, 나머지는 투명
        BufferedImage sprite = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int y = 320; y < 336; y++) for (int x = 160; x < 176; x++) sprite.setRGB(x, y, INK);
        for (int y = 320; y < 336; y++) for (int x = 176; x < 192; x++) sprite.setRGB(x, y, GREEN);

        BufferedImage first = read(PixelLabSleepService.toFirstFrame(png(sprite)));

        assertThat(first.getWidth()).isEqualTo(CANVAS);
        assertThat(first.getHeight()).isEqualTo(CANVAS);
        assertThat(first.getRGB(10 + PAD_X, 20 + PAD_TOP)).isEqualTo(INK);
        assertThat(first.getRGB(11 + PAD_X, 20 + PAD_TOP)).isEqualTo(GREEN);
        assertThat(first.getRGB(12 + PAD_X, 20 + PAD_TOP) >>> 24).isZero();
        assertThat(first.getRGB(0, 0) >>> 24).isZero();
    }

    @Test
    @DisplayName("첫 장: 빈 그림이나 이미지가 아닌 값은 보내지 않는다")
    void firstFrameNeedsSomethingToDraw() throws Exception {
        assertThat(PixelLabSleepService.toFirstFrame(png(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)))).isNull();
        assertThat(PixelLabSleepService.toFirstFrame(new byte[]{1, 2, 3})).isNull();
    }

    @Test
    @DisplayName("받은 그림: 자는 모습이 원래 칸 안에 있으면 그 칸을 그대로 잘라 도트 크기와 발 높이가 깨어 있을 때와 같다")
    void fitKeepsTheOriginalWindowWhenTheBodyStaysInside() throws Exception {
        // 창(24..88, 40..104) 안: 칸 (30..60, 90..100)
        BufferedImage out = read(PixelLabSleepService.fit(frame(PAD_X + 6, PAD_TOP + 50, 30, 10)));

        int scale = PixelLabSleepService.OUTPUT_MAX / GRID;
        assertThat(out.getWidth()).isEqualTo(GRID * scale);
        assertThat(out.getHeight()).isEqualTo(GRID * scale);
        // 창 안에서의 자리가 그대로다: 칸 (6,50)이 몸의 왼쪽 위
        assertThat(out.getRGB(6 * scale, 50 * scale)).isEqualTo(GREEN);
        assertThat(out.getRGB(5 * scale, 50 * scale) >>> 24).isZero();
        assertThat(out.getRGB(6 * scale, 49 * scale) >>> 24).isZero();
        assertThat(out.getRGB(35 * scale + scale - 1, 59 * scale + scale - 1)).isEqualTo(GREEN);
    }

    @Test
    @DisplayName("받은 그림: 길게 누워 칸 밖으로 나가면 몸이 다 들어가게 넓히고, 바닥선은 그대로 둔다")
    void fitWidensForALongBody() throws Exception {
        // 가로 100칸짜리 몸 (칸 6..105), 바닥은 창 바닥 바로 위 (칸 96..103)
        BufferedImage out = read(PixelLabSleepService.fit(frame(6, 96, 100, 8)));

        int side = 102, scale = PixelLabSleepService.OUTPUT_MAX / side;
        assertThat(out.getWidth()).isEqualTo(side * scale);
        // 왼쪽·오른쪽 끝이 모두 들어 있다
        assertThat(out.getRGB(1 * scale, (side - 8) * scale)).isEqualTo(GREEN);
        assertThat(out.getRGB(100 * scale, (side - 1) * scale)).isEqualTo(GREEN);
        assertThat(out.getRGB(0, (side - 1) * scale) >>> 24).isZero();
        // 바닥선: 몸의 맨 아랫줄이 그림의 맨 아랫줄
        assertThat(out.getRGB(50 * scale, out.getHeight() - 1)).isEqualTo(GREEN);
    }

    @Test
    @DisplayName("받은 그림: 빈 그림이면 쓰지 않는다")
    void fitRejectsEmptyFrame() throws Exception {
        assertThat(PixelLabSleepService.fit(png(new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB)))).isNull();
    }

    @Test
    @DisplayName("동작 문장: 엎드리라고 하지 않고 자는 자리를 잡고 잠들게 한다 (엎드리라고 하면 기차는 몸이 사라졌다)")
    void actionSettlesInsteadOfLyingDown() {
        assertThat(PixelLabSleepService.ACTION).contains("finds its sleeping spot and settles in").contains("staying whole");
        assertThat(PixelLabSleepService.ACTION).doesNotContain("lies down");
    }

    /** 몸(초록 사각형)과 떨어진 조각들을 놓은 캔버스 */
    private static BufferedImage withBody() {
        BufferedImage img = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        for (int y = 60; y < 90; y++) for (int x = 30; x < 80; x++) img.setRGB(x, y, GREEN);   // 몸 1500칸
        return img;
    }

    @Test
    @DisplayName("받은 그림: 원래 그림에 없던 색의 작은 조각은 지운다 (용 꼬리 옆의 흰 점)")
    void dropsSmallPiecesInForeignColors() throws Exception {
        BufferedImage frame = withBody();
        for (int y = 40; y < 42; y++) for (int x = 95; x < 98; x++) frame.setRGB(x, y, 0xFFFDFFEA);   // 흰 점 6칸

        BufferedImage out = read(PixelLabSleepService.dropStrayPieces(png(frame), png(withBody())));

        assertThat(out.getRGB(96, 40) >>> 24).isZero();
        assertThat(out.getRGB(50, 70)).isEqualTo(GREEN);
    }

    @Test
    @DisplayName("받은 그림: 원래 그림에 있던 색의 조각(움직임 선·장식)과 큰 조각, 몸에 붙은 것은 남긴다")
    void keepsPiecesTheDoodleHadAndBigOnes() throws Exception {
        BufferedImage original = withBody();
        original.setRGB(20, 80, INK);                                                               // 원래 그림의 움직임 선 색
        BufferedImage frame = withBody();
        for (int x = 18; x < 21; x++) frame.setRGB(x, 85, INK);                                      // 같은 색 조각 3칸: 남긴다
        for (int y = 20; y < 30; y++) for (int x = 90; x < 100; x++) frame.setRGB(x, y, 0xFFFFFFFF); // 흰 덩어리 100칸: 커서 남긴다
        for (int y = 55; y < 60; y++) for (int x = 40; x < 44; x++) frame.setRGB(x, y, 0xFFFFFFFF);  // 몸에 붙은 흰 증기: 몸의 일부

        BufferedImage out = read(PixelLabSleepService.dropStrayPieces(png(frame), png(original)));

        assertThat(out.getRGB(19, 85)).isEqualTo(INK);
        assertThat(out.getRGB(95, 25)).isEqualTo(0xFFFFFFFF);
        assertThat(out.getRGB(41, 57)).isEqualTo(0xFFFFFFFF);
    }

    @Test
    @DisplayName("키가 없거나 예전 방식으로 돌려 두면 PixelLab을 쓰지 않는다")
    void disabledWithoutKeyOrWhenSwitchedBack() {
        PixelLabSleepService service = new PixelLabSleepService(new ObjectMapper());
        assertThat(service.enabled()).isFalse();
        assertThat(service.sleep(new byte[]{1})).isNull();

        ReflectionTestUtils.setField(service, "apiKey", "key");
        assertThat(service.enabled()).isTrue();
        ReflectionTestUtils.setField(service, "provider", "openai");
        assertThat(service.enabled()).isFalse();
    }
}
