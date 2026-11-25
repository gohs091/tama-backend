package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 자는 모습을 PixelLab(도트 그림을 그대로 움직여 주는 서비스)으로 만든다.
 * 끄적이 그림을 첫 장으로 넣고 "자는 자리를 잡고 잠든다" 동작을 시키면 같은 아이를 움직인 8장이 오고, 마지막 장이 자는 모습이다.
 * 그림 모델에게 자는 모습을 새로 그리게 하면 색이 틀어지고(초록 용이 연두로), 무늬가 빠지고(배의 크림색), 없던 다리가 생겼다.
 * PixelLab은 넣은 그림을 그대로 움직여서 색·무늬·팔다리·테두리가 남는다 (2026-11-23 발 없는 용·회색 토끼·호랑이로 확인).
 * 키가 없거나, 실패하거나, 제때 오지 않으면 null: 부르는 쪽이 예전 방식(그림 모델)으로 그린다.
 */
@Service
@Slf4j
public class PixelLabSleepService {

    /** 끄적이 그림의 칸 수 (PixelSnap.GRID와 같다). 도트 한 칸을 1픽셀로 보내 도트 크기가 달라지지 않게 한다 */
    static final int GRID = PixelSnap.GRID;
    /** 눕는 자리가 필요해서 그림을 더 넓은 캔버스에 놓아 보낸다: 양옆 24칸, 위 40칸, 아래 8칸 */
    static final int CANVAS = 112, PAD_X = 24, PAD_TOP = 40;
    /**
     * "엎드려 잠든다"라고 시키면 엎드릴 수 없는 기차는 몸이 사라지고 바퀴만 남았다. 눕히지 않고 "자는 자리를 잡고 잠든다"로 시키면
     * 기차는 선 채로 눈을 감고, 토끼는 웅크리고, 발 없는 용은 길게 눕고, 사람은 옆으로 눕는다 (2026-11-23, 각 한 번).
     */
    static final String ACTION = "finds its sleeping spot and settles in comfortably where it stands, then closes its eyes and falls fast asleep, " +
            "staying whole and still, breathing gently";
    /** 떨어진 조각이 원래 그림의 어느 색과도 이만큼(RGB 거리) 넘게 다르면 원래 없던 색으로 본다 */
    static final int FOREIGN_COLOR = 48;
    /** 지우는 조각의 크기 한도: 몸 칸 수의 이 비율과 MIN_STRAY_LIMIT 중 큰 쪽 이하 */
    static final double STRAY_SHARE = 0.02;
    static final int MIN_STRAY_LIMIT = 8;
    static final int FRAMES = 8;
    /** 돌려줄 그림의 한 변(픽셀)이 이 값을 넘지 않게 칸을 키운다 */
    static final int OUTPUT_MAX = 1024;
    private static final int VISIBLE = 128;

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${pixellab.api-key:}")
    private String apiKey;

    @Value("${pixellab.base-url:https://api.pixellab.ai/v2}")
    private String baseUrl = "https://api.pixellab.ai/v2";

    /** pixellab | openai (예전 방식으로 되돌릴 때) */
    @Value("${image.sleep-provider:pixellab}")
    private String provider = "pixellab";

    /** 앱은 자는 모습을 180초까지 기다린다. 이 시간 안에 오지 않으면 포기하고 예전 방식으로 그릴 시간을 남긴다 */
    @Value("${pixellab.wait-seconds:140}")
    private int waitSeconds = 140;

    public PixelLabSleepService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return "pixellab".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
    }

    /** 자는 모습 PNG. 만들지 못했으면 null */
    public byte[] sleep(byte[] sprite) {
        if (!enabled()) return null;
        try {
            byte[] first = toFirstFrame(sprite);
            if (first == null) return null;
            JsonNode started = post("/animate-with-text-v3", Map.of(
                    "first_frame", Map.of("type", "base64", "base64", Base64.getEncoder().encodeToString(first), "format", "png"),
                    "action", ACTION, "frame_count", FRAMES, "no_background", true));
            String jobId = started.path("background_job_id").asText("");
            if (jobId.isEmpty()) {
                log.warn("PixelLab 자는 모습: 작업 번호를 받지 못함");
                return null;
            }
            long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(4000);
                JsonNode job = get("/background-jobs/" + jobId);
                String status = job.path("status").asText("");
                if ("failed".equals(status)) {
                    log.warn("PixelLab 자는 모습 실패 - job: {}", jobId);
                    return null;
                }
                if (!"completed".equals(status)) continue;
                JsonNode images = job.path("last_response").path("images");
                if (!images.isArray() || images.isEmpty()) return null;
                byte[] last = Base64.getDecoder().decode(images.get(images.size() - 1).path("base64").asText(""));
                byte[] fitted = fit(dropStrayPieces(last, first));
                if (fitted != null) log.info("PixelLab 자는 모습 완료 - job: {}, {}장", jobId, images.size());
                return fitted;
            }
            log.warn("PixelLab 자는 모습이 {}초 안에 오지 않음 - job: {}", waitSeconds, jobId);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("PixelLab 자는 모습 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 보낼 첫 장: 끄적이 그림을 GRID×GRID 칸(칸 가운데 색, 도트 한 칸 = 1픽셀)으로 줄여 CANVAS 캔버스에 놓는다.
     * 그림이 비었거나 읽을 수 없으면 null
     */
    static byte[] toFirstFrame(byte[] sprite) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(sprite));
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            boolean alpha = src.getColorModel().hasAlpha();
            BufferedImage out = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
            boolean any = false;
            for (int gy = 0; gy < GRID; gy++) {
                for (int gx = 0; gx < GRID; gx++) {
                    int p = src.getRGB(Math.min(w - 1, (int) ((gx + 0.5) * w / GRID)), Math.min(h - 1, (int) ((gy + 0.5) * h / GRID)));
                    if (alpha && (p >>> 24) < VISIBLE) continue;
                    out.setRGB(gx + PAD_X, gy + PAD_TOP, 0xFF000000 | p);
                    any = true;
                }
            }
            return any ? png(out) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 받은 그림에서 원래 그림에 없던 색의 작은 조각을 지운다 (발 없는 용의 꼬리 옆에 흰 점 몇 칸이 생겼다).
     * 몸(가장 큰 덩어리)에서 떨어져 있고, 작고, 색이 원래 그림의 어느 색과도 먼 조각만 지운다.
     * 원래 그림에 있던 색의 조각(꼬리 옆 움직임 선, 떠 있는 장식)과 큰 조각은 남긴다. 읽을 수 없으면 받은 그림 그대로
     */
    static byte[] dropStrayPieces(byte[] frame, byte[] original) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(frame));
            BufferedImage ref = ImageIO.read(new ByteArrayInputStream(original));
            if (img == null || ref == null) return frame;
            java.util.Set<Integer> colors = new java.util.HashSet<>();
            for (int y = 0; y < ref.getHeight(); y++) {
                for (int x = 0; x < ref.getWidth(); x++) {
                    int p = ref.getRGB(x, y);
                    if ((p >>> 24) >= VISIBLE) colors.add(p & 0xFFFFFF);
                }
            }
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            int[] label = new int[w * h];
            java.util.List<int[]> pieces = new java.util.ArrayList<>();   // [칸 수, r합, g합, b합]
            int[] stack = new int[w * h];
            for (int start = 0; start < px.length; start++) {
                if (label[start] != 0 || (px[start] >>> 24) < VISIBLE) continue;
                int id = pieces.size() + 1, top = 0;
                int[] sum = new int[4];
                stack[top++] = start;
                label[start] = id;
                while (top > 0) {
                    int i = stack[--top], x = i % w, y = i / w;
                    sum[0]++; sum[1] += (px[i] >> 16) & 255; sum[2] += (px[i] >> 8) & 255; sum[3] += px[i] & 255;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                            int n = ny * w + nx;
                            if (label[n] != 0 || (px[n] >>> 24) < VISIBLE) continue;
                            label[n] = id;
                            stack[top++] = n;
                        }
                    }
                }
                pieces.add(sum);
            }
            if (pieces.size() < 2) return frame;
            int body = 0;
            for (int[] p : pieces) body = Math.max(body, p[0]);
            int limit = Math.max(MIN_STRAY_LIMIT, (int) (body * STRAY_SHARE));
            boolean[] drop = new boolean[pieces.size() + 1];
            boolean any = false;
            for (int i = 0; i < pieces.size(); i++) {
                int[] p = pieces.get(i);
                if (p[0] == body || p[0] > limit) continue;
                int r = p[1] / p[0], g = p[2] / p[0], b = p[3] / p[0];
                boolean foreign = true;
                for (int c : colors) {
                    int dr = r - ((c >> 16) & 255), dg = g - ((c >> 8) & 255), db = b - (c & 255);
                    if (dr * dr + dg * dg + db * db <= FOREIGN_COLOR * FOREIGN_COLOR) { foreign = false; break; }
                }
                if (foreign) { drop[i + 1] = true; any = true; }
            }
            if (!any) return frame;
            for (int i = 0; i < px.length; i++) if (drop[label[i]]) px[i] = 0;
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, w, h, px, 0, w);
            return png(out);
        } catch (Exception e) {
            return frame;
        }
    }

    /**
     * 받은 마지막 장을 앱이 쓰는 정사각형 그림으로 맞춘다.
     * 자는 모습이 원래 그림 칸(GRID×GRID 창) 안에 들어가면 그 창을 그대로 잘라, 깨어 있을 때와 도트 크기·발 높이가 같다.
     * 길게 누워 창 밖으로 나가면(뱀·용) 몸이 다 들어가는 가장 작은 정사각형으로 넓힌다: 바닥선은 그대로, 가로는 몸 가운데에 맞춘다.
     * 빈 그림이면 null
     */
    static byte[] fit(byte[] frame) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(frame));
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            int left = w, right = -1, top = h, bottom = -1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((src.getRGB(x, y) >>> 24) < VISIBLE) continue;
                    left = Math.min(left, x); right = Math.max(right, x);
                    top = Math.min(top, y); bottom = Math.max(bottom, y);
                }
            }
            if (right < 0) return null;
            // 창의 위치: 보낸 캔버스와 크기가 같으면 보낸 자리, 다르면 비율로
            double k = (double) w / CANVAS;
            int winX = (int) Math.round(PAD_X * k), winY = (int) Math.round(PAD_TOP * k), win = (int) Math.round(GRID * k);
            int floor = Math.max(winY + win, bottom + 1);                      // 바닥선 (몸이 더 내려갔으면 거기까지)
            int side = Math.max(win, Math.max(right - left + 1 + 2, floor - top + 1));
            boolean inside = left >= winX && right < winX + win && top >= winY && bottom < winY + win;
            int x0 = inside ? winX : (left + right + 1) / 2 - side / 2;
            int y0 = floor - side;
            int scale = Math.max(1, OUTPUT_MAX / side);
            BufferedImage out = new BufferedImage(side * scale, side * scale, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    int sx = x0 + x, sy = y0 + y;
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                    int p = src.getRGB(sx, sy);
                    if ((p >>> 24) < VISIBLE) continue;
                    int c = 0xFF000000 | p;
                    for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++) out.setRGB(x * scale + dx, y * scale + dy, c);
                }
            }
            return png(out);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] png(BufferedImage img) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private JsonNode post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return read(path, http.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        return read(path, http.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private JsonNode read(String path, HttpResponse<String> response) throws Exception {
        if (response.statusCode() / 100 != 2) {
            String body = response.body() == null ? "" : response.body();
            throw new IllegalStateException(path + " " + response.statusCode() + " " + body.substring(0, Math.min(200, body.length())));
        }
        return objectMapper.readTree(response.body());
    }
}
