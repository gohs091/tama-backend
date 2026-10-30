package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import com.tamagochi.tamagochi.domain.land.LandCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ImageGenerationService {

    private final WebClient webClient;
    private final WebClient openAiClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ImageJobResult> jobStore = new ConcurrentHashMap<>();

    @Value("${stability.api-key}")
    private String stabilityApiKey;

    // image.provider: stability(기본, 롤백용) | openai
    @Value("${image.provider:stability}")
    private String provider;

    // 손그림이 무엇인지 분석 (없거나 실패하면 일반 프롬프트로 진행)
    @Autowired(required = false)
    private DoodleSubjectService doodleSubjectService;

    // 처음 그린 그림의 분석 결과 저장소 (없으면 진화 때마다 분석)
    @Autowired(required = false)
    private DoodleOriginService doodleOriginService;

    // image.prompt-version: v3(기본, 칸으로 나눈 프롬프트) | v2(이전 프롬프트, 롤백용)
    @Value("${image.prompt-version:v3}")
    private String promptVersion = "v3";

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.image-model:gpt-image-1-mini}")
    private String openAiImageModel;

    @Value("${openai.image-quality:medium}")
    private String openAiImageQuality;

    private static final String OPENAI_URL = "https://api.openai.com";
    private static final String OPENAI_EDIT_ENDPOINT = "/v1/images/edits";
    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;

    private static final String STABILITY_URL = "https://api.stability.ai";
    private static final String SKETCH_ENDPOINT = "/v2beta/stable-image/control/sketch";
    private static final String REMOVE_BG_ENDPOINT = "/v2beta/stable-image/edit/remove-background";

    // ── Prompt v2: 픽셀 아트 / 다마고치 스타일 ────────────────────────────────
    // 롤백: ImageGenerationService_prompt_v1_backup.md 참고

    private static final String NEGATIVE_PROMPT =
            "multiple characters, two characters, three characters, group, friends, companions, " +
            "surrounding characters, background characters, extra characters, other monsters, side characters, " +
            "pattern, tile, repeat, wallpaper, texture background, " +
            "scenery, landscape, environment, backdrop, sky, grass, ocean, forest, city, nature, " +
            "shadow, drop shadow, cast shadow, inner shadow, ground shadow, circular shadow, " +
            "vignette, halo, glow, spotlight, radial gradient, ambient occlusion, background glow, " +
            "ground, floor, detailed background, gradient background, " +
            "photorealistic, hyperrealistic, 3d render, realistic, painting, watercolor, oil paint, " +
            "smooth gradients, anti-aliased, blurry, soft edges, " +
            "human anatomy, ugly, scary, high tech, complex shading";

    public ImageGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(STABILITY_URL)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
        this.openAiClient = WebClient.builder()
                .baseUrl(OPENAI_URL)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }

    public void storeJob(String jobId, ImageJobResult result) {
        jobStore.put(jobId, result);
    }

    public ImageJobResult getJob(String jobId) {
        return jobStore.getOrDefault(jobId, ImageJobResult.failed("Job not found"));
    }

    // ----------------------------------------------------------------
    // 낙서(baby) 생성: 손그림 base64 → Stability AI sketch control
    // ----------------------------------------------------------------
    public void generateAsync(String jobId, String imageBase64, String faction) {
        generateAsync(jobId, imageBase64, faction, null);
    }

    // variationSeed: 그림과 팩션이 합쳐지는 방식(FusionMode)을 정한다. 같은 시드는 같은 방식
    @Async("imageExecutor")
    public void generateAsync(String jobId, String imageBase64, String faction, Long variationSeed) {
        generateAsync(jobId, imageBase64, faction, variationSeed, null);
    }

    // tossUserId: 그림 분석 결과를 저장해 두었다가 스케치·완성작 진화에서 다시 쓴다 (없으면 저장하지 않는다)
    @Async("imageExecutor")
    public void generateAsync(String jobId, String imageBase64, String faction, Long variationSeed, String tossUserId) {
        jobStore.put(jobId, ImageJobResult.pending());
        try {
            String mimeType = extractMimeType(imageBase64);
            byte[] imageBytes = decodeBase64Image(imageBase64);
            Generated generated = generateImage(imageBytes, mimeType, null, null, faction, "baby", "0.70", variationSeed, null, tossUserId);
            byte[] responseBytes = generated.bytes();

            if (responseBytes == null || responseBytes.length == 0) {
                jobStore.put(jobId, ImageJobResult.failed("Empty image response"));
                return;
            }
            jobStore.put(jobId, ImageJobResult.done(toDataUrl(responseBytes), generated.species(), detectFacing(responseBytes)));
            log.info("Baby image generation done for job: {}", jobId);

        } catch (Exception e) {
            log.error("Image generation failed for job: {}", jobId, e);
            jobStore.put(jobId, ImageJobResult.failed(e.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    // 스케치/완성작 생성: S3 URL 또는 base64 → Stability AI sketch control
    // stage: baby | child(스케치) | adult(완성작)
    // ----------------------------------------------------------------
    public void generateEvolvedAsync(String jobId, String imageUrl, String imageBase64,
                                     String faction, String stage) {
        generateEvolvedAsync(jobId, imageUrl, imageBase64, faction, stage, null);
    }

    public void generateEvolvedAsync(String jobId, String imageUrl, String imageBase64,
                                     String faction, String stage, String referenceBase64) {
        generateEvolvedAsync(jobId, imageUrl, imageBase64, faction, stage, referenceBase64, null);
    }

    // referenceBase64: 성장 전 단계의 캐릭터 이미지 (있으면 같은 캐릭터로 진화, OpenAI 경로에서만 사용)
    // absorbedInk: 끄적랜드에서 고른 대표 잉크 문장 (서버 고정 문구, 없으면 null)
    @Async("imageExecutor")
    public void generateEvolvedAsync(String jobId, String imageUrl, String imageBase64,
                                     String faction, String stage, String referenceBase64, String absorbedInk) {
        generateEvolvedAsync(jobId, imageUrl, imageBase64, faction, stage, referenceBase64, absorbedInk, null);
    }

    // tossUserId: 처음 그린 그림의 분석 결과를 불러온다 (없으면 그때 한 번 분석해 저장)
    @Async("imageExecutor")
    public void generateEvolvedAsync(String jobId, String imageUrl, String imageBase64,
                                     String faction, String stage, String referenceBase64, String absorbedInk,
                                     String tossUserId) {
        jobStore.put(jobId, ImageJobResult.pending());
        try {
            byte[] imageBytes;
            if (imageUrl != null && !imageUrl.isBlank()) {
                imageBytes = downloadFromUrl(imageUrl);
            } else if (imageBase64 != null && !imageBase64.isBlank()) {
                imageBytes = decodeBase64Image(imageBase64);
            } else {
                jobStore.put(jobId, ImageJobResult.failed("imageUrl 또는 imageBase64 중 하나는 필수입니다."));
                return;
            }

            // 진화 단계별 control_strength: 픽셀 아트는 스케치 실루엣을 높게 따를수록 연속성 유지
            String controlStrength = switch (stage) {
                case "child" -> "0.75";
                case "adult" -> "0.80";
                default      -> "0.70";
            };

            String mimeType = (imageUrl != null && !imageUrl.isBlank()) ? "image/jpeg" : extractMimeType(imageBase64);
            boolean hasReference = referenceBase64 != null && !referenceBase64.isBlank();
            byte[] referenceBytes = hasReference ? decodeBase64Image(referenceBase64) : null;
            String referenceMime = hasReference ? extractMimeType(referenceBase64) : null;
            byte[] responseBytes = generateImage(imageBytes, mimeType, referenceBytes, referenceMime,
                    faction, stage, controlStrength, null, absorbedInk, tossUserId).bytes();

            if (responseBytes == null || responseBytes.length == 0) {
                jobStore.put(jobId, ImageJobResult.failed("Empty image response"));
                return;
            }
            jobStore.put(jobId, ImageJobResult.done(toDataUrl(responseBytes), null, detectFacing(responseBytes)));
            log.info("Evolved ({}) image generation done for job: {}", stage, jobId);

        } catch (Exception e) {
            log.error("Evolved image generation failed for job: {}", jobId, e);
            jobStore.put(jobId, ImageJobResult.failed(e.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    // 프로바이더 분기: 최종 투명 PNG 바이트 반환
    // openai    : 편집형 모델이 낙서를 캐릭터로 재해석, 투명 배경 직접 출력 (배경 제거 호출 없음)
    // stability : sketch control + remove-background (롤백용)
    // ----------------------------------------------------------------
    /** 생성된 이미지와, 그 과정에서 분석한 손그림의 정체(한글, 없으면 null) */
    private record Generated(byte[] bytes, String species) {}

    private Generated generateImage(byte[] imageBytes, String mimeType, byte[] referenceBytes, String referenceMime,
                                    String faction, String stage, String controlStrength, Long variationSeed,
                                    String absorbedInk, String tossUserId) {
        String ink = absorbedInk == null || absorbedInk.isBlank() ? "" : " " + LandCatalog.fillOrigin(absorbedInk, null, null);
        if ("openai".equalsIgnoreCase(provider)) {
            if (!"v2".equalsIgnoreCase(promptVersion)) {
                return generateV3(imageBytes, mimeType, referenceBytes, referenceMime, faction, stage, absorbedInk, tossUserId);
            }
            // v2(롤백용): 이전 프롬프트
            DoodleSubject subject = referenceBytes == null ? describeDoodle(imageBytes, mimeType) : null;
            FusionMode mode = FusionMode.SELF;
            if (subject != null && !subject.isAbstract()) {
                mode = FusionMode.pick(variationSeed, isAnimalLike(subject));
                log.info("합체 방식 - mode: {}, subject: {}, faction: {}", mode, subject.subject(), faction);
            }
            String prompt = buildOpenAiPrompt(faction, stage, referenceBytes != null, subject, mode) + ink;
            byte[] image = binarizeAlpha(callOpenAiEdit(imageBytes, mimeType, referenceBytes, referenceMime, prompt));
            return new Generated(image, subject == null ? null : subject.species());
        }
        byte[] bytes = callStabilitySketch(imageBytes, mimeType, buildFactionPrompt(faction, stage) + ink, controlStrength);
        if (bytes == null || bytes.length == 0) return new Generated(bytes, null);
        return new Generated(removeBackground(bytes), null);
    }

    // v3: 낙서는 그림 그대로 살리고(합체 없음), 진화는 처음 그림의 정체를 이어서 쓴다
    private Generated generateV3(byte[] imageBytes, String mimeType, byte[] referenceBytes, String referenceMime,
                                 String faction, String stage, String absorbedInk, String tossUserId) {
        boolean evolving = referenceBytes != null;
        String colors = getFactionPixelColors(faction);
        DoodleSubject subject;
        String prompt;
        if (evolving) {
            subject = originFor(tossUserId, imageBytes, mimeType);
            prompt = ImagePromptV3.evolve(stage, subject, faction, colors, getFactionThemeMotifs(faction), absorbedInk);
        } else {
            subject = describeDoodle(imageBytes, mimeType);
            if (subject != null && !subject.isAbstract()) {
                saveOrigin(tossUserId, subject);
                log.info("v3 낙서 - subject: {}, kind: {}, bodyPlan: {}, faction: {}", subject.subject(), subject.kind(), subject.bodyPlan(), faction);
                prompt = ImagePromptV3.baby(subject, colors);
            } else {
                // 무엇인지 모르는 그림은 이전과 같은 일반 프롬프트로 만든다
                prompt = buildOpenAiPrompt(faction, stage, false, subject, FusionMode.SELF);
            }
        }
        byte[] image = binarizeAlpha(callOpenAiEdit(imageBytes, mimeType, referenceBytes, referenceMime, prompt));
        return new Generated(image, evolving || subject == null ? null : subject.species());
    }

    /** 저장된 그림 분석을 읽고, 없으면(저장 기능 이전에 만든 유저) 지금 한 번 분석해 저장한다 */
    private DoodleSubject originFor(String tossUserId, byte[] imageBytes, String mimeType) {
        if (doodleOriginService != null) {
            DoodleSubject saved = doodleOriginService.find(tossUserId).orElse(null);
            if (saved != null) return saved;
        }
        DoodleSubject described = describeDoodle(imageBytes, mimeType);
        if (described != null && !described.isAbstract()) saveOrigin(tossUserId, described);
        return described;
    }

    private void saveOrigin(String tossUserId, DoodleSubject subject) {
        if (doodleOriginService != null) doodleOriginService.save(tossUserId, subject);
    }

    // ----------------------------------------------------------------
    // OpenAI images/edits 호출 (gpt-image 계열)
    // 이미지 순서: 참조(성장 전 캐릭터)가 있으면 1번, 원본 낙서는 그 다음
    // ----------------------------------------------------------------
    private byte[] callOpenAiEdit(byte[] sketchBytes, String sketchMime, byte[] referenceBytes, String referenceMime,
                                  String prompt) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        String field = referenceBytes != null ? "image[]" : "image";
        if (referenceBytes != null) {
            addImagePart(builder, field, referenceBytes, referenceMime, "current.png");
        }
        addImagePart(builder, field, sketchBytes, sketchMime, sketchMime.contains("png") ? "sketch.png" : "sketch.jpg");
        builder.part("model", openAiImageModel);
        builder.part("prompt", prompt);
        builder.part("size", "1024x1024");
        builder.part("quality", openAiImageQuality);
        builder.part("background", "transparent");
        builder.part("output_format", "png");

        String json = openAiClient.post()
                .uri(OPENAI_EDIT_ENDPOINT)
                .header("Authorization", "Bearer " + openAiApiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(180))
                .block();

        try {
            String b64 = objectMapper.readTree(json).path("data").path(0).path("b64_json").asText("");
            return b64.isEmpty() ? null : Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 픽셀 아트용 알파 정리: 알파 128 미만은 투명, 이상은 불투명으로 나누고, 몸 안쪽의 투명 구멍은 채운다.
     * 모델이 지시를 어기고 그리는 흐린 그림자/번짐과 반투명 가장자리를 없애고, 배경이 비쳐 색이 달라 보이는 것을 막는다.
     * 실패하면 원본을 그대로 반환.
     */
    byte[] binarizeAlpha(byte[] png) {
        if (png == null || png.length == 0) return png;
        byte[] cleaned = SpriteAlpha.clean(png);
        if (cleaned == null) {
            log.warn("알파 정리 실패, 원본 사용");
            return png;
        }
        return cleaned;
    }

    private void addImagePart(MultipartBodyBuilder builder, String field, byte[] bytes, String mime, String filename) {
        builder.part(field, bytes)
                .header("Content-Disposition", "form-data; name=\"" + field + "\"; filename=\"" + filename + "\"")
                .header("Content-Type", mime);
    }

    /** 만들어진 그림이 바라보는 쪽(left | right | front). 분석이 없거나 실패하면 null — 앱은 방향 없이(뒤집지 않고) 보여준다 */
    private String detectFacing(byte[] png) {
        return doodleSubjectService == null ? null : doodleSubjectService.facing(png).orElse(null);
    }

    private DoodleSubject describeDoodle(byte[] imageBytes, String mimeType) {
        if (doodleSubjectService == null) return null;
        DoodleSubject subject = doodleSubjectService.describe(imageBytes, mimeType).orElse(null);
        log.info("손그림 분석 - subject: {}, kind: {}", subject == null ? "(분석 실패)" : subject.subject(),
                subject == null ? "-" : subject.kind());
        return subject;
    }

    // ----------------------------------------------------------------
    // OpenAI용 프롬프트
    // - 스타일 지시를 맨 앞에: 뒤에 두면 대상 설명에 밀려 매끈한 벡터 그림이 나온다
    // - 낙서가 무엇인지(subject)를 알면 그 대상 자체를 캐릭터로 만들게 한다 (칼 → 칼 캐릭터)
    // - 참조 이미지(성장 전 모습)가 있으면 같은 캐릭터가 성장한 모습으로 그린다
    // ----------------------------------------------------------------
    static final String PIXEL_STYLE =
            "Create a 16-bit PIXEL ART sprite, like a retro handheld tamagotchi / Game Boy Advance game: " +
            "the image must be made of clearly visible chunky square pixels with hard stair-stepped edges, " +
            "a very low-resolution look (about 48x48 pixels upscaled), bold dark pixel outlines, flat colors " +
            "with at most 2 shades per color, NO smooth vector lines, NO smooth curves, NO gradients, NO anti-aliasing. ";

    String buildOpenAiPrompt(String faction, String stage, boolean hasReference) {
        return buildOpenAiPrompt(faction, stage, hasReference, null);
    }

    String buildOpenAiPrompt(String faction, String stage, boolean hasReference, DoodleSubject subject) {
        return buildOpenAiPrompt(faction, stage, hasReference, subject, FusionMode.SELF);
    }

    static boolean isAnimalLike(DoodleSubject subject) {
        String kind = subject.kind() == null ? "" : subject.kind().toLowerCase();
        return kind.equals("animal") || kind.equals("creature") || kind.equals("person");
    }

    String buildOpenAiPrompt(String faction, String stage, boolean hasReference, DoodleSubject subject, FusionMode mode) {
        boolean fused = !hasReference && mode != FusionMode.SELF && subject != null && !subject.isAbstract();
        String stageText = switch (stage) {
            case "child" -> "It is a clearly EVOLVED form of the baby: noticeably bigger and sturdier body, " +
                            "one signature feature clearly added, and more detail than the baby form.";
            case "adult" -> "It is the fully evolved, powerful final form: large, imposing, richly detailed, with prominent " +
                            "iconic features and a strong, heroic pose.";
            default      -> fused
                    ? "It is a small, cute baby form of the faction creature; do not add accessories other than the drawn object."
                    : "It is a small, cute baby form: a simple, rounded chibi version of the doodle with big cute eyes " +
                      "and tiny limbs. Preserve the pose and outline of the doodle; " +
                      "do not add new accessories, armor or props.";
        };
        String tail = "Use this color scheme as the dominant colors of the whole sprite: " + getFactionPixelColors(faction) +
                " (you may add one or two small accent colors so that different parts are easy to tell apart). " +
                buildThemeText(faction, stage, fused) + " " + stageText + " " +
                "Single character centered, filling about 70% of the frame, transparent background, " +
                "no shadow, no glow, no ground, no other characters.";

        if (hasReference) {
            return PIXEL_STYLE +
                    "You are given two images. Image 1 is the CURRENT form of a tamagotchi-style pet: keep its face, " +
                    "colors, markings and recognizable identity. Image 2 is the original doodle the player drew; " +
                    "use it only as a hint for identifying features. Draw the NEXT evolved form of the SAME creature. " +
                    "Keep the same composition as image 1: if the creature holds, rides or merges with the drawn object, " +
                    "it keeps doing so, and the drawn object stays clearly recognizable and grows together with the creature. " +
                    "Never draw blueprints, diagrams or text. " + tail;
        }

        if (fused) {
            String name = subject.subject();
            String keep = " The drawn " + name + " keeps its exact shape from the doodle, stays clearly recognizable " +
                    "and takes up at least half of the sprite's size.";
            String composition = switch (mode) {
                case HELD -> "The player drew a rough doodle of " + name + ". The character is a cute baby creature of the " +
                        "faction theme below (NOT the doodle itself) that is holding, wielding or carrying the drawn " + name + "." + keep;
                case FEATURE -> "The player drew a rough doodle of " + name + ". The character is a cute baby creature of the " +
                        "faction theme below. The drawn " + name + " is physically part of the creature's own body: it IS one of " +
                        "its body parts, such as its horn, tail, wing, crest or shell (choose the part that fits the shape best), " +
                        "growing out of the body. It is NOT held in a hand and NOT a separate object." + keep;
                default -> "The player drew a rough doodle of " + name + ". The character is a cute baby creature of the " +
                        "faction theme below riding on the drawn " + name + ", which has become its vehicle or mount " +
                        "(flying, rolling or floating)." + keep;
            };
            String parts = subject.parts() == null || subject.parts().isBlank() ? "" :
                    " Keep the drawn object's parts as in the doodle: " + subject.parts() + ".";
            return PIXEL_STYLE + "The sprite shows ONE cute tamagotchi-style pet. " + composition + parts +
                    " Fill the whole sprite with solid flat colors; nothing may be see-through or white. " +
                    "Do not draw blueprints, diagrams or text. " + tail;
        }

        if (subject != null && !subject.isAbstract()) {
            String identity = switch (subject.kind().toLowerCase()) {
                case "animal", "creature", "person" ->
                        "The doodle depicts " + subject.subject() + ". Draw that same " + "character as a cute pet: keep its silhouette, " +
                        "proportions and pose.";
                default ->
                        "The doodle depicts " + subject.subject() + ". The " + subject.subject() + " itself is the character, " +
                        "NOT an animal: keep its exact silhouette and proportions. Give it a simple cute pixel face " +
                        "(two dot eyes and a small smile) on its main body part, plus tiny stubby arms and legs.";
            };
            String parts = subject.parts() == null || subject.parts().isBlank() ? "" :
                    " Keep every part in the same position as in the doodle: " + subject.parts() + ".";
            return PIXEL_STYLE + "The sprite shows ONE cute tamagotchi-style pet based on the player's rough doodle. " +
                    identity + parts +
                    " Fill the whole body with solid flat colors; nothing may be see-through or white. " +
                    "Do not draw blueprints, diagrams or text, and do not invent an unrelated creature. " + tail;
        }

        return PIXEL_STYLE +
                "The input image is a rough doodle drawn by a player (black outlines, empty unfilled interior). " +
                "Draw it as ONE cute tamagotchi-style pet that is clearly recognizable as that same drawing. " +
                "If it depicts an animal or creature: keep its silhouette, ears, tail and pose. " +
                "If it depicts an object: do NOT replace it with an unrelated animal; turn the object itself into a living " +
                "character that keeps the object's silhouette and key parts, with a cute face and tiny arms and legs. " +
                "Keep every identifying feature of the doodle (shape, proportions, ears, tail, whiskers, eyes, mouth). " +
                "Fill the whole body with solid flat colors; no part of the body may be see-through or white. " +
                "Do not draw blueprints, diagrams or text, and do not invent an unrelated creature. " + tail;
    }

    // ----------------------------------------------------------------
    // Stability AI sketch endpoint 공통 호출
    // ----------------------------------------------------------------
    private byte[] callStabilitySketch(byte[] imageBytes, String mimeType, String prompt, String controlStrength) {
        String ext = mimeType.contains("png") ? "sketch.png" : "sketch.jpg";
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", imageBytes)
                .header("Content-Disposition", "form-data; name=\"image\"; filename=\"" + ext + "\"")
                .header("Content-Type", mimeType);
        builder.part("prompt", prompt);
        builder.part("negative_prompt", NEGATIVE_PROMPT);
        builder.part("control_strength", controlStrength);
        builder.part("output_format", "jpeg");

        return webClient.post()
                .uri(SKETCH_ENDPOINT)
                .header("Authorization", "Bearer " + stabilityApiKey)
                .header("Accept", "image/*")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    // ----------------------------------------------------------------
    // 팩션 × 단계 프롬프트 (v2 — 픽셀 아트 / 다마고치 스타일)
    // ----------------------------------------------------------------
    private String buildFactionPrompt(String faction, String stage) {
        String factionColors = getFactionPixelColors(faction);
        String stageModifier = switch (stage) {
            case "child" -> ", slightly larger sprite, evolved form with more details, " +
                            "additional small accessories or features";
            case "adult"  -> ", large detailed sprite, fully evolved powerful form, " +
                            "prominent faction-colored armor or features, iconic silhouette";
            default       -> ", small cute baby form, simple round shape";
        };
        return "pixel art, 16-bit retro game sprite, tamagotchi creature, " +
                "cute chibi character, " + factionColors + ", " +
                "bold black pixel outlines, flat solid colors, limited color palette, " +
                "sharp pixel edges, no gradients, no anti-aliasing, " +
                "ONLY ONE character, single sprite centered, " +
                "filling 70% of frame, pure solid white background, no shadow, no vignette, no glow, isolated sprite" +
                stageModifier;
    }

    // ----------------------------------------------------------------
    // 팩션 테마 (디지몬 소속 필드 컨셉): 단계가 오를수록 테마 반영이 커진다 (낙서 20% → 스케치 50% → 완성작 80%)
    // 테마는 사용자의 그림을 대체하지 않고 장식·질감·소품으로 입힌다
    // ----------------------------------------------------------------
    String buildThemeText(String faction, String stage) {
        return buildThemeText(faction, stage, false);
    }

    // fused: 그림과 팩션 생물이 따로 존재하는 합체 방식(HELD/FEATURE/MOUNT)의 낙서 — 처음부터 팩션 생물이 등장한다
    String buildThemeText(String faction, String stage, boolean fused) {
        String motifs = getFactionThemeMotifs(faction);
        if (fused && !"child".equals(stage) && !"adult".equals(stage)) {
            return "Faction theme: " + motifs + ". The character itself is a baby creature of this faction " +
                    "(theme about 60%), expressed on the creature's own body. The player's drawn object must remain " +
                    "clearly recognizable and large.";
        }
        String intensity = switch (stage) {
            case "child" -> "about 50%: two or three of these motifs are clearly visible";
            case "adult" -> "about 80%: these motifs are prominent and make up the character's iconic look";
            default      -> "about 20%: only a subtle hint, such as the color scheme and at most one tiny motif";
        };
        return "Faction theme: " + motifs + ". Apply the theme at " + intensity + ". " +
                "The theme must never replace the player's drawing: keep the doodle's silhouette and identity, " +
                "and express the theme through decoration, texture and small parts.";
    }

    String getFactionThemeMotifs(String faction) {
        return switch (faction) {
            case "nature-spirits"     -> "wild beast (canine or feline) traits: fur tufts, pointed animal ears, a bushy tail, paws and claws";
            case "deep-savers"        -> "aquatic creature traits: fins, scales, water droplets, bubbles, shell or coral parts";
            case "nightmare-soldiers" -> "haunted house traits: friendly spooky ghost wisps, a tattered sheet-like cloak, cobwebs, candle-like glow, glowing eyes, little stitched patches";
            case "wind-guardians"     -> "canyon and valley traits: layered rock-ridge textures, small waterfalls and mist, wind-swept feathers, a bird-of-prey crest and wings";
            case "metal-empire"       -> "machine and cyborg traits: metal plating, bolts and gears, mechanical joints, antenna, a visor, robotic limbs";
            case "virus-busters"      -> "heroic justice fighter: a hero emblem, a cape or scarf, protective gear, a shield, a brave cheerful look";
            case "dragons-roar"       -> "dragon and dinosaur traits: scales, horns, spikes along the back, claws, a thick tail, small flames";
            case "jungle-troopers"    -> "plant and insect traits: leaves, vines, flower buds, antennae, insect wings or shell plates";
            case "dark-area"          -> "demon lord and beast-demon traits from the underworld where the data of the dead and deleted gathers: " +
                                         "curved demon horns, dark bat-like wings, bone or skull ornaments, chains, " +
                                         "dark gothic armor, plus pale bone-white and bright violet accents so the sprite stays readable " +
                                         "on dark backgrounds. The sprite itself must stay solid, clean and fully intact";
            case "trash"              -> "chaotic patchwork of recycled junk from a landfill: bottle caps, can lids, cardboard and mismatched colorful scraps";
            case "space"              -> "outer space traits: star speckles, a tiny planet ring or crescent moon, a glass helmet-like dome, comet-tail trails, glowing constellation dots";
            case "desert"             -> "desert traits: cactus spines and flowers, sand-dune curves, a wrapped scarf, a scarab or scorpion tail, sun-baked cracked patterns";
            case "snowfield"          -> "snowfield traits: icicles, snowflake crystals, fluffy snow fur, a knitted scarf, a frosty breath puff";
            case "volcano"            -> "volcano traits: glowing lava cracks, small flames, obsidian rock plates, ember sparks, a steaming vent";
            case "candy-land"         -> "candy land traits: lollipops, frosting drips, sprinkles, cookie and macaron parts, a whipped-cream swirl";
            case "ninja"              -> "ninja traits: a headband with a flowing tail, a face wrap, shuriken, a scroll, swift shadow-like wisps";
            case "magic-school"       -> "magic school traits: a pointed wizard hat or robe, a star-tipped wand, floating spell books, sparkling rune circles, small stars";
            default                   -> "a cute, neutral look";
        };
    }

    String getFactionPixelColors(String faction) {
        return switch (faction) {
            case "nature-spirits"     -> "green and brown color scheme";
            case "deep-savers"        -> "blue and cyan color scheme";
            case "nightmare-soldiers" -> "dark purple and black color scheme";
            case "wind-guardians"     -> "light blue and white color scheme";
            case "metal-empire"       -> "silver and gold color scheme";
            case "virus-busters"      -> "pink and white color scheme";
            case "dragons-roar"       -> "orange and red color scheme";
            case "jungle-troopers"    -> "olive green and tan color scheme";
            case "dark-area"          -> "dark indigo and deep purple color scheme";
            case "trash"              -> "rainbow multicolor scheme";
            case "space"              -> "deep navy and bright violet with star yellow color scheme";
            case "desert"             -> "sand yellow and terracotta orange color scheme";
            case "snowfield"          -> "ice blue and snow white color scheme";
            case "volcano"            -> "lava red and charcoal black with ember orange color scheme";
            case "candy-land"         -> "pastel pink, mint and cream color scheme";
            case "ninja"              -> "charcoal black and crimson red color scheme";
            case "magic-school"       -> "royal purple and gold color scheme";
            default                   -> "warm cream and light color scheme";
        };
    }

    // ----------------------------------------------------------------
    // 유틸
    // ----------------------------------------------------------------
    private byte[] decodeBase64Image(String base64) {
        String clean = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        return Base64.getDecoder().decode(clean);
    }

    private String extractMimeType(String base64) {
        if (base64.startsWith("data:")) {
            int semi = base64.indexOf(';');
            if (semi > 5) return base64.substring(5, semi); // "data:" = 5 chars
        }
        return "image/jpeg";
    }

    private byte[] downloadFromUrl(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("S3 이미지 다운로드 실패: HTTP " + response.statusCode());
        }
        return response.body();
    }

    // ----------------------------------------------------------------
    // Stability AI remove-background 호출
    // 색상 기반 휴리스틱 없이 AI 세그멘테이션으로 배경 제거 → 투명 PNG 반환
    // ----------------------------------------------------------------
    private byte[] removeBackground(byte[] imageBytes) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", imageBytes)
                    .header("Content-Disposition", "form-data; name=\"image\"; filename=\"image.jpg\"")
                    .header("Content-Type", "image/jpeg");
            builder.part("output_format", "png");

            byte[] result = webClient.post()
                    .uri(REMOVE_BG_ENDPOINT)
                    .header("Authorization", "Bearer " + stabilityApiKey)
                    .header("Accept", "image/*")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (result == null || result.length == 0) {
                log.warn("removeBackground: empty response, using original");
                return imageBytes;
            }
            return result;
        } catch (Exception e) {
            log.warn("removeBackground failed, using original: {}", e.getMessage());
            return imageBytes;
        }
    }

    private String toDataUrl(byte[] imageBytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StabilityApiResponse {
        private String image;
        private String finish_reason;
    }
}
