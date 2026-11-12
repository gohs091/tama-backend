package com.tamagochi.tamagochi.service.character;

import com.tamagochi.tamagochi.domain.land.LandCatalog;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이미지 프롬프트 v3: 한 덩어리 문장이 아니라 칸(슬롯)으로 나눠 조립한다.
 *   [스타일] [입력 역할] [정체] [방향] [체형] [성장 폭] [흡수한 잉크] [진영 장식] [마무리]
 * - 낙서: 그림을 그대로 살린다. 사람·생물 그림도 합체(탈것·부위·들기)하지 않는다.
 * - 스케치/완성작: 직전 단계 이미지(1번)와 원본 낙서(2번)를 함께 보고, 크기만 키우지 않고 방향을 정해 진화한다.
 *   완성작은 "정규 진화"처럼 더 강하고 멋있어 보이게 한다(체격·표정·디테일·대비).
 * - 흡수한 잉크가 있으면 그 칸에 들어간다(문장은 LandCatalog가 개수·단계 한도·등급으로 만든다). 없으면 성장만 한다.
 * 롤백: image.prompt-version=v2 로 두면 이전 프롬프트(ImageGenerationService.buildOpenAiPrompt)를 쓴다.
 */
final class ImagePromptV3 {

    private ImagePromptV3() {}

    /** 스카프·망토는 여러 진영에 겹쳐 모든 캐릭터가 비슷해 보이게 하므로, 기사 진영에서만 모티프로 쓴다 */
    static final String KNIGHT_FACTION = "virus-busters";

    /** 속이 빈 새까만 눈이 어울리는 진영: 이 진영이나 악마 잉크에서만 눈동자 없는 눈을 허용한다 */
    static final Set<String> BLANK_EYE_FACTIONS = Set.of("dark-area", "nightmare-soldiers");

    /** 진화하면서 눈동자가 사라져 섬뜩해진다는 후기가 있어, 앞 단계의 눈 모양을 유지하고 하이라이트를 꼭 넣게 한다 */
    private static final String EYES = "Eyes: keep the exact eye design of image 1 (the same eye shape, the same whites and pupils if image 1 has them, " +
            "the same dark dots if image 1 has dots), and make sure every eye keeps a small white highlight so it never becomes a blank solid-black oval. " +
            "Do not make the eyes smaller, flatter or emptier than in image 1.";

    /** 잉크가 닿지 않은 부위(꼬리 등)까지 물들고, 점이 자글자글해지고, 칸 크기가 달라지는 걸 막는다 */
    static final String CLEAN = "Every part that the absorbed ink does not change (for example the tail, wings, eyes, belly and main body color) keeps EXACTLY " +
            "its colors and shape from image 1. Use a limited palette (about 12 colors at most) with large clean flat color areas: " +
            "no speckled, mottled, noisy or rainbow pixels. Keep the same pixel size as image 1. ";

    /** 정면 그림은 방향이 없어 앱이 뒤집을 수 없다 (11/2 결정). 진화 프롬프트에 빠져 있어 완성작이 정면으로 나왔다 */
    static final String SIDE_VIEW = "View: a SIDE VIEW (or three-quarter side view) facing the same direction as image 1, the whole body turned to that side. " +
            "Never a front view facing the viewer. ";

    /** 젤리·유령·얼음 같은 재질도 불투명하게: 반투명하게 그리면 알파 정리에서 지워져 몸에 구멍이 난다 */
    static final String SOLID = "Fill the whole body with solid, fully opaque flat colors: even jelly, glass, ice, ghost or water materials are drawn " +
            "as opaque colors, never see-through, translucent or white. ";

    private static final String FRAME = "Single character centered, filling about 70% of the frame, transparent background, " +
            "no shadow, no glow, no ground, no other characters.";

    // ================= 잉크 진화 시안 v5 (image.ink-style=v5) =================
    // 낙서 단계: 측면 + 귀여운 얼굴 (mini가 정면으로 그리면 진화에서 돌릴 수 없다)
    // 스케치: 아기 체형 + 측면 + 진영은 색·무늬만. sunburst low로 그린다
    // 완성작: 정수 뽑기 디자인(글 모델)이 몸 구조가 다른 후보 3개를 내고, 고른 후보를 글로만 그린다 (스케치 그림을 넣으면 그 얼굴을 따라 그려 키메라가 됐다)

    static final String SIDE_BABY_V5 = "View: a SIDE VIEW (or three-quarter side view) facing RIGHT, the whole body turned to that side, " +
            "with its face shown in profile. Never a front view facing the viewer. Face: big round eyes with a white highlight and a small happy smile. ";

    static final String SIDE_SKETCH_V5 = "View: a SIDE VIEW (or three-quarter side view) facing the same direction as image 1, " +
            "the whole body turned to that side. Never a front view. ";

    /** 스케치 귀여움: 숫자 비율 없이 아기 체형으로. 알아보게 하는 길이(긴 목·긴 꼬리)는 지킨다 */
    static final String CHIBI_V5 = "It must stay lovable, the kind of pet people want to raise, drawn in a CHIBI baby-animal style: " +
            "the head is clearly bigger than a real animal's head compared to the body, the legs are short and stubby, the body is compact and chubby, " +
            "big round eyes with a large white highlight, a happy open smile, and thick bold dark outlines. " +
            "Keep the lengths that make it recognizable (a long neck stays long, a long tail stays long) and keep the same body plan as image 1. " +
            "Draw it with chunky pixels: about 48 pixels across the whole canvas, so each pixel is a big visible square. " +
            "Do NOT add muscles, fangs or sharp teeth, angry or fierce eyes, armor plates, weapon-like spikes or big claws.";

    /**
     * 몸은 끝까지 불투명하게: 젤리·유리·얼음·유령·물 재질을 반투명하게 그리면 알파 정리(binarizeAlpha)에서 지워져 몸에 구멍이 난다.
     * v5는 solid=true라 앱이 구멍을 흰색으로 메우지 않으므로, 그대로 배경이 비쳐 보인다. 유리 느낌은 밝은 하이라이트 색으로만 낸다 (#52에서 가져옴)
     */
    static final String SOLID_V5 = "Fill the whole body with solid, fully opaque flat colors: even jelly, glass, ice, ghost or water materials are drawn as opaque colors " +
            "that only LOOK glassy through lighter highlight shades, never see-through or translucent. ";

    /** 진영 장식이 귀·꼬리를 붙여 로봇·공룡에 고양이 귀가 생겼다 → 색·작은 무늬만 */
    static final String FACTION_PARTS_V5 = "Faction theme: only as subtle colors or small patterns, never as new body parts " +
            "(no ears, tails, horns or paws added for the faction).";

    /** v5 낙서 단계: 지금 문장 끝(틀 문장 앞)에 방향·얼굴 지시를 더한다 */
    static String babyV5(DoodleSubject o, String colors) {
        String base = baby(o, colors);
        int at = base.lastIndexOf(FRAME);
        return at < 0 ? base + " " + SIDE_BABY_V5 : base.substring(0, at) + SIDE_BABY_V5 + base.substring(at);
    }

    // ================= 스케치 v6 (image.ink-style=v6) =================
    // 아기 그림에 부위를 덧붙이지 않는다. 글 모델이 "무엇으로 자랐는지" 한 줄(후보 3개)을 쓰고, 그 한 줄을
    // 스파링 상대를 그리던 프롬프트 뼈대로 그린다: 잘 다듬어진 샘플 두 장은 화풍 참고, 아기 그림은 같은 아이 참고, 색(투톤·포인트)은 맡긴다.
    // 체형이나 분위기를 정하거나 막는 말은 넣지 않는다 ("통통하게", "마르지 않게" 같은 말은 모든 끄적이에 한 가지 답을 정한다).
    // 어느 쪽으로 자랐는지는 끄적이마다 한 줄 설명이 정하고, 후보 셋은 서로 다른 방향이다.

    /** v6 스케치 디자이너(글 모델의 시스템 지시). 후보에 순서를 매기지 않는다: 늘 첫 후보만 그리면 한쪽으로 쏠린다 */
    static final String SKETCH_DESIGNER_V6 = """
            You write one-line character descriptions for the illustrator of a pixel-art monster-raising game.

            A baby pet is evolving into its MIDDLE form: the same creature, one stage older. Its owner should say "whoa, it grew up!" and "that's my little one" in the same breath.

            You get image 1 (the pet now, as a baby), image 2 (the doodle its owner drew) and a note (what it is, what was special about the doodle, its tribe, and the ink its owner fed it, if any).

            Write what it looks like now in ONE sentence of at most 40 words, the way you would brief an illustrator: what it is, its main color (the baby's own), HOW it has grown up, and ONE thing it got from its theme. The theme is the ink if the owner fed one, otherwise its tribe. That one thing is either a feature it grew or one item it wears (a hat, a scarf, a cape, a collar, a headband): whichever is more charming for this creature.

            There is no single right way to grow up: any build, any mood, as long as it is clearly one stage older and it suits who this character is. Say plainly which way it grew.

            Propose THREE different ones, each grown in a clearly different direction.

            Reply with JSON only: {"candidates":[{"line":"the one-sentence description","lineKo":"what it grew into, in Korean, at most 18 characters, friendly tone"},{...},{...}]}
            """;

    /** 진영 이름 (스파링 상대를 그릴 때 쓰던 이름 그대로). 모르는 진영이면 빈 값 */
    static String sketchThemeV6(String faction) {
        return switch (faction == null ? "" : faction) {
            case "nature-spirits" -> "forest spirits";
            case "deep-savers" -> "deep sea";
            case "jungle-troopers" -> "jungle";
            case "metal-empire" -> "machine kingdom";
            case "nightmare-soldiers" -> "playful haunted house";
            case "wind-guardians" -> "windy valley";
            case "virus-busters" -> "knights castle";
            case "dragons-roar" -> "dragon nest";
            case "dark-area" -> "mischievous dark world";
            case "trash" -> "junkyard";
            case "space" -> "space station";
            case "desert" -> "desert";
            case "snowfield" -> "snowfield";
            case "volcano" -> "volcano";
            case "candy-land" -> "candy land";
            case "ninja" -> "ninja village";
            case "magic-school" -> "magic school";
            default -> "";
        };
    }

    /** 디자이너에게 주는 쪽지: 무엇인지, 낙서의 별난 점, 진영, 넣은 잉크 */
    static String sketchNoteV6(DoodleSubject origin, String faction, String inkTheme) {
        String theme = sketchThemeV6(faction);
        return "What it is: " + (origin == null || blank(origin.subject()) ? "the creature in the doodle" : origin.subject()) + ". " +
                (origin == null || blank(origin.traits()) ? "" : "What was special about the doodle: " + origin.traits() + ". ") +
                (theme.isEmpty() ? "" : "Its tribe: " + theme + ". ") +
                "Ink: " + (blank(inkTheme) ? "none (its tribe is the theme)" : inkTheme);
    }

    /** 글 모델이 실패했을 때 쓰는 한 줄: 그림은 그대로 그린다 */
    static String sketchFallbackLineV6(DoodleSubject origin, String inkTheme) {
        String what = origin == null || blank(origin.subject()) ? "The creature of image 3" : cap(origin.subject());
        return what + ", one stage older than the baby in image 3, in the baby's own main color" +
                (blank(inkTheme) ? "." : ", showing what it got from its ink: " + inkTheme + ".");
    }

    private static String cap(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /** 쉬는 자세(완성작 POSE_FINAL_V5와 같은 뜻): 끄적이는 홈 화면에 가만히 있어서 걷는 발·든 앞발이 어색하다 */
    static final String REST_V6 = "POSE: a calm RESTING pose for a pet game home screen, in whatever body it has: a walker stands still with feet on the ground, " +
            "a serpent rests calmly coiled or in a gentle S-curve near the ground, a flyer or floater hovers calmly just above the ground. " +
            "Never an attack, a jump or a mid-motion action. ";

    /**
     * v6 스케치 그림 문장. 그림은 세 장을 넣는다: 1·2번은 화풍 참고(잘 다듬어진 샘플), 3번은 지금 모습(아기).
     * line은 디자이너가 쓴 한 줄 (무엇으로 자랐는지).
     */
    static String sketchV6(String faction, String line) {
        String theme = sketchThemeV6(faction);
        return ImageGenerationService.PIXEL_STYLE +
                "You are given three images. Images 1 and 2 are STYLE REFERENCES only: copy their exact pixel size, outline thickness, flat color shading " +
                "and cute tamagotchi proportions, but do NOT copy their characters. " +
                "Image 3 is THIS pet as a baby. Draw the same individual grown one stage: keep what it is, its main color and what makes it special, " +
                "but make a new drawing, do not trace it. " +
                "Draw ONE tamagotchi-style character" + (theme.isEmpty() ? "" : " from the " + theme + " theme") + ": " + line + " " +
                "It is a mid-evolution form: clearly one stage more grown-up than the baby in image 3, grown up in exactly the way the description says. " +
                "It has a few more details. It is not yet its final form. " +
                "Render it as charming and lovely: clear colors with two-tone markings (such as a lighter belly) and one accent color. " +
                "VIEW: a three-quarter side view. Its face and body are clearly turned toward the RIGHT side of the image, looking to the right, " +
                "like the reference sprites. Do NOT draw a front view; the pose must not be left-right symmetrical. " +
                REST_V6 +
                "Keep a cute simple pixel face in the style of the references, never scary. " +
                SOLID_V5 +
                "Single character centered, full body, filling about 70% of the frame, transparent background, no ground, no text, no other characters.";
    }

    /** v5 스케치. absorbed는 잉크 문장(v3 방식: 재질 잉크만 칠한다), 없으면 null */
    static String sketchV5(DoodleSubject origin, String absorbed) {
        boolean hasInk = !blank(absorbed);
        boolean bodyChanges = hasInk && absorbed.contains(LandCatalog.BODY_CHANGE_MARK);
        String identity = origin == null || blank(origin.subject()) ? "" :
                "Origin of this creature: " + origin.subject() + "." +
                (blank(origin.traits()) ? "" : " Its distinctive features: " + origin.traits() + ".") + " ";
        String fidelity = hasInk ? THEME_KEEP : SAME;
        String growth = (hasInk ? CHILD_THEME_GROWTH : CHILD_GROWTH) + " " + CHIBI_V5;
        String bodyPlan = bodyChanges ? "" : "Keep the body plan of image 1 (the same number of legs and the same overall posture family). ";
        String ink = hasInk ? LandCatalog.fillOrigin(absorbed, origin == null ? null : origin.subject(), origin == null ? null : origin.traits()) + " " : "";
        String eyes = blankEyesAllowed(null, absorbed) ? "" : EYES + " ";
        return ImageGenerationService.PIXEL_STYLE +
                "You are given two images. Image 1 is the CURRENT form of a tamagotchi-style pet. Image 2 is the original doodle the player drew: " +
                "the root of this creature's identity (silhouette, proportions, distinctive features). " +
                identity + fidelity + " " + bodyPlan + growth + " " + toolGrowth(origin) + ink + FACTION_PARTS_V5 + " " + eyes + SOLID_V5 +
                "It must be ONE single creature. Never draw blueprints, diagrams or text. " + SIDE_SKETCH_V5 + FRAME;
    }

    /**
     * v5 완성작 디자이너(글 모델의 시스템 지시). 스케치에 덧붙이지 않고 정수를 뽑아 다른 차원의 존재로 다시 태어나게 한다.
     * 한 번에 몸 구조가 다른 후보 3개를 받는다: 한 개만 받으면 같은 문장에서 늘 같은 몸(사람형만, 또는 뱀형만)으로 쏠렸다.
     * 자세는 "그 몸의 쉬는 자세"(홈 화면이 정적이라 뛰기·날개 활짝이 어색했다), 디자인은 48칸에서 읽히게 단순하게.
     */
    static final String FINAL_DESIGNER_V5 = """
            You are the lead creature designer of a monster-raising game. Its FINAL evolutions are famous for being a complete paradigm shift,
            like a red mechanical dragon evolving into a crimson holy knight with a flowing cape: not the old body with more parts added, but a new being of a higher order.

            You get two images: image 1 is the creature's CURRENT form (second stage), image 2 is the original doodle the player drew. You also get the ink the player fed it.

            Do NOT add things to the current body. DIGEST everything and DISTILL its essence:
            1. Essence: in one sentence, what is this creature at its core? (its spirit, role and personality, read from its look, its origin and the ink)
            2. Rebirth: reincarnate that essence as a completely new, more majestic being. Species, body plan, silhouette, head and face may ALL change.
               It can be a knight, a guardian beast, a spirit, a titan, a dragon, a mecha, anything, as long as it is the natural final form of that essence.
            3. Carry over ONLY three things: its main color palette, its personality, and one or two symbolic motifs TRANSFORMED into something more meaningful
               (for example a tail flame becomes a flaming cape or banner, an antenna becomes a crest or halo, long ears become a hood or crown-like horns).
               Nothing is copied literally from the current form.
            Rules: it may be cool, elegant, majestic or awe-inspiring; armor, capes and crowns are fine when they come from the essence. No gore, nothing scary or grotesque.
            Eyes must stay vivid and readable. A tight palette (3 main colors + 1 accent). ONE single being, no rider or companion. Side view (or three-quarter side view) facing right.
            POSE: it lives on the calm home screen of a pet game, so draw it RESTING in whatever body it has: a walker stands still with feet on the ground,
            a serpent or long-bodied being rests calmly coiled or lies in a gentle S-curve on the ground, a flyer or floater hovers calmly just above the ground.
            The pose is calm, never an attack, a jump or a mid-motion action. This rule is about the POSE only: it must not limit what kind of body it becomes.
            SIMPLICITY: it must read clearly as a small 48-pixel sprite. Use a few big, bold shapes and large calm color areas, at most 10 colors in total,
            and at most 2 key details. No fine filigree, thin swirls, tiny patterns, lace, or many small floating pieces.

            Propose THREE different final forms of the same essence, each with a clearly DIFFERENT body type
            (for example one upright humanoid or knight, one beast or serpent, one floating spirit or winged being; choose the three that fit this essence best).
            For each also write, in KOREAN for the player: "nameKo", a short name of 2 to 6 Korean characters (no English letters),
            and "lineKo", one short line of at most 18 Korean characters saying what it becomes. "body" is one of: humanoid, beast, serpent, flyer, spirit.

            Reply with JSON only:
            {"candidates": [ {"nameKo": "...", "lineKo": "...", "body": "...", "essence": "...", "newForm": "...", "bodyPlan": "...", "silhouette": "...",
             "face": "...", "palette": "...", "motifs": ["from X -> to Y"], "details": ["at most 2 key design details, each a big bold shape"]}, {...}, {...} ]}
            """;

    private static final String PIXEL_FINAL_V5 = "Create a 16-bit PIXEL ART sprite, like a retro handheld monster game: clearly visible chunky square pixels " +
            "with hard stair-stepped edges, about 48 pixels across the whole canvas so each pixel is a big visible square, bold dark pixel outlines, " +
            "flat colors with at most 2 shades per color, NO smooth vector lines, NO gradients, NO anti-aliasing. Keep the design SIMPLE: a few big bold shapes, " +
            "large flat color areas, at most 10 colors, no fine filigree, thin swirls or tiny patterns. ";
    private static final String POSE_FINAL_V5 = "POSE: a calm RESTING pose for a pet game home screen, facing RIGHT in side view (or three-quarter side view), " +
            "in whatever body it has: a walker stands still with feet on the ground, a serpent rests calmly coiled or in a gentle S-curve near the ground, " +
            "a flyer or floater hovers calmly just above the ground. Never an attack, a jump or a mid-motion action. ";
    private static final String FRAME_FINAL_V5 = "Single character centered, filling about 70% of the frame, transparent background, no shadow, no glow, " +
            "no ground, no other characters, no text. " + SOLID_V5;

    /** v5 완성작 그림 프롬프트: 고른 디자인 후보 하나를 글로만 그린다 (그림 입력 없음) */
    static String finalFromDesignV5(com.fasterxml.jackson.databind.JsonNode c) {
        StringBuilder motifs = new StringBuilder(), details = new StringBuilder();
        c.path("motifs").forEach(m -> motifs.append(motifs.isEmpty() ? "" : "; ").append(m.asText()));
        c.path("details").forEach(d -> details.append(details.isEmpty() ? "" : "; ").append(d.asText()));
        return PIXEL_FINAL_V5 + "Draw this creature. It is the FINAL evolution: " + c.path("newForm").asText() + ". " +
                "Essence: " + c.path("essence").asText() + " Body plan: " + c.path("bodyPlan").asText() + ". " +
                "Silhouette: " + c.path("silhouette").asText() + ". Head and face: " + c.path("face").asText() + ". " +
                "Palette: " + c.path("palette").asText() + ". Symbolic motifs: " + motifs + ". Key details: " + details + ". " +
                "Eyes vivid and readable. ONE single being. " + POSE_FINAL_V5 + FRAME_FINAL_V5;
    }

    /** 원본이 사람·생물이면 그 모습 그대로, 물체이면 물체 자체에 얼굴을 달아 캐릭터로 만든다 */
    static String baby(DoodleSubject o, String colors) {
        if (o.isTool()) return babyTool(o, colors);
        boolean living = isLiving(o);
        String identity = living
                ? "The doodle depicts " + o.subject() + ". Draw that same character as a cute pet: keep its silhouette, proportions and pose."
                : "The doodle depicts " + o.subject() + ". The " + o.subject() + " itself is the character, NOT an animal: keep its exact " +
                  "silhouette and proportions. Give it a simple cute pixel face (two dot eyes and a small smile) on its main body part, " +
                  "plus tiny stubby arms and legs.";
        String parts = blank(o.parts()) ? "" : " Keep every part in the same position as in the doodle: " + o.parts() + ".";
        String traits = blank(o.traits()) ? "" :
                " Keep these distinctive features of the drawing clearly visible and exaggerated rather than smoothed out: " + o.traits() + ".";
        String plan = blank(o.bodyPlan()) || "other".equalsIgnoreCase(o.bodyPlan()) ? "" : " Body plan: " + o.bodyPlan() + ".";
        return ImageGenerationService.PIXEL_STYLE +
                "The sprite shows ONE cute tamagotchi-style pet based on the player's rough doodle. " + identity + parts + traits + plan + " " +
                "It is ONE single creature: no rider, no mount, no companion, no second head or body. " +
                "Fill the whole body with solid flat colors; nothing may be see-through or white. " +
                "Do not draw blueprints, diagrams or text, and do not invent an unrelated creature. " +
                "Faction color hint (subtle, only as colors and at most one tiny accent): " + colors + ". " +
                "It is a small, cute baby form with big cute eyes. Do not add accessories, scarves, armor or props. " + FRAME;
    }

    /**
     * 칼·방패 같은 도구: 도구에 얼굴을 붙이지 않고, 도구가 생물의 몸 한 부분(귀·껍질·손·날개)이 된 귀여운 생물로 만든다.
     * 도구 자체에 얼굴을 붙이면 "머리 위에 칼을 얹은 로봇"처럼 매력이 떨어진다 (기차·자동차 같은 완성된 물체는 이 경로를 쓰지 않는다).
     */
    static String babyTool(DoodleSubject o, String colors) {
        String s = o.subject();
        String detail = !blank(o.traits()) ? o.traits() : (!blank(o.parts()) ? o.parts() : "its own shape and details");
        return ImageGenerationService.PIXEL_STYLE +
                "The sprite shows ONE cute original tamagotchi-style creature inspired by the player's rough doodle of " + s + ". " +
                "Do NOT draw the " + s + " itself with a face stuck on it. Instead, design a small chubby, round, soft creature with a big head, " +
                "big cute eyes and a simple friendly face, in which the " + s + " appears as one of its own body parts: " + toolPart(o.attach(), s) + ". " +
                "That part must clearly keep the " + s + "'s own shape, material and details (" + detail + ") so a person immediately recognizes where it came from, " +
                "while the rest of the body is soft, round and cute and uses the " + s + "'s main colors. " +
                "It is ONE single creature: no rider, no second character. Fill the whole body with solid flat colors, no see-through parts, no text. " +
                "Faction color hint (subtle, only as colors and at most one tiny accent): " + colors + ". " +
                "Eyes: round dark eyes with a clear white highlight. " + FRAME;
    }

    /**
     * 자는 모습: 지금 모습 그대로 엎드려 자는 그림. 정체·부위·특징·몸 구조·진영 색을 키워드로 함께 알려 같은 캐릭터로 남게 한다.
     * 새 부위(날개·뿔·장식)를 만들지 못하게 하고, 눈이 있으면 감는다.
     */
    /**
     * 자는 모습: 지금 모습의 색을 그대로 써야 한다. 진영 색·진영 장식은 넣지 않는다
     * (넣으면 "깊은 바다 = 파랑"처럼 진영 색으로 다시 칠해 주황 고양이가 파란 고양이가 됐다).
     * 입력 그림은 옅은 단색 배경을 깔아 보낸다 (SpriteBackdrop): 투명 배경 그대로 보내면 모델이 어두운 테두리를 배경으로 여겨
     * 테두리·감은 눈·입 선을 투명하게 비웠다. 그래서 배경은 캐릭터가 아니라고 알리고, 테두리를 불투명하게 지키라고 따로 적는다.
     */
    static String sleep(DoodleSubject o) {
        return sleep(o, false);
    }

    /** 엎드려 자는 자세 (낙서·스케치) */
    private static final String SLEEP_LYING = "Now draw THIS SAME character fast asleep, lying down on its belly with its body low and flat on the ground " +
            "and its head resting down on its front limbs, face lowered, eyes closed (if it has a face). ";

    /**
     * 앉아서 조는 자세 (v5 완성작). 완성작은 몸이 크고 장식이 많아, 납작하게 엎드리면 얼굴이 작아지고 로브 입은 사람형은 형태가 뭉개진다.
     * 앉은 채 고개만 숙이면 얼굴이 크게 보이고 관·갈기·볏 같은 장식도 그대로 남는다 (2026-11-12 로봇·사자·새 완성작으로 비교, 사용자 선택).
     */
    private static final String SLEEP_DOZING = "Now draw THIS SAME character dozing off in a cozy, compact pose: it has settled down where it stood, " +
            "with its legs folded or tucked under its body (a four-legged creature rests like a loaf, an upright two-legged character sits or kneels), " +
            "its body still rounded and upright rather than flattened on the ground, and its head nodding forward a little. " +
            "Its head and face stay as large as in the image and clearly visible, with closed eyes drawn as gentle curved lines " +
            "and a small peaceful smile (if it has a face). ";

    /** @param dozing true면 앉아서 조는 자세(v5 완성작), false면 엎드려 자는 자세 */
    static String sleep(DoodleSubject o, boolean dozing) {
        String based = o == null || blank(o.subject()) ? "" : "It was generated from these keywords: it is based on the player's doodle of " + o.subject() + ". ";
        String parts = o == null || blank(o.parts()) ? "" : "Its parts: " + o.parts() + ". ";
        String traits = o == null || blank(o.traits()) ? "" : "Distinctive features that define it: " + o.traits() + ". ";
        String plan = o == null || blank(o.bodyPlan()) || "other".equalsIgnoreCase(o.bodyPlan()) ? "" : "Body plan: " + o.bodyPlan() + ". ";
        return "This image shows the current pixel art form of a tamagotchi-style pet on a plain flat backdrop (the backdrop is not part of the character). " +
                based + parts + traits + plan +
                "Use EXACTLY the colors that are in the image (same body color, same markings, same light belly or face patches) — do not recolor anything. " +
                (dozing ? SLEEP_DOZING : SLEEP_LYING) +
                "Keep every listed part, feature, marking and color clearly visible in the new pose, in the same colors as the image. " +
                "Do NOT add new parts such as wings, horns or accessories, and do not change the species, colors or color pattern. " +
                "Keep the exact same 16-bit pixel art style, pixel size and flat color palette as the image, and keep its bold dark outline: " +
                "the same outline color and thickness around the whole silhouette and between its parts, drawn as solid opaque pixels (never transparent, never left out). " +
                "It faces the same direction as in the image. " +
                "Single character centered, filling about 70% of the frame, transparent background, no shadow, no glow, no ground, no other characters, no text.";
    }

    /** 도구가 되는 몸 부위. 알 수 없으면 귀로 한다 (길고 얇은 도구가 가장 많아 가장 안전하다) */
    static String toolPart(String attach, String s) {
        String a = attach == null ? "" : attach.trim().toLowerCase();
        return switch (a) {
            case "shell" -> "a big round SHELL on its back made from the " + s + ", like a turtle or a hermit crab carries its shell";
            case "hands" -> "two big round HANDS shaped like the " + s + " (one on each side, like big mittens or gloves), and a small " + s + "-shaped crest on its forehead";
            case "wings" -> "two flat wing-like fins shaped like the " + s + " on its back";
            default -> "two long, narrow, pointed EARS standing up on its head, shaped like two small copies of the " + s;
        };
    }

    /** 진화할 때도 도구는 손에 든 무기가 아니라 몸의 일부로 남는다 */
    private static String toolGrowth(DoodleSubject o) {
        if (o == null || !o.isTool()) return "";
        String s = o.subject();
        return "The " + s + " is NOT a weapon or object the creature holds: it is part of its body (" + toolPart(o.attach(), s) + "). " +
                "Keep that part clearly recognizable and let it grow sharper, bigger or more ornate as the creature matures into a stronger armored creature around it, " +
                "with armor plates in its own colors. The creature must NOT hold the " + s + " in its hand and must NOT turn into the " + s + " itself. ";
    }

    /**
     * @param origin   처음 그린 그림의 분석(없으면 null)
     * @param faction  진영 키 (스카프·망토는 기사 진영에서만 쓴다)
     * @param absorbed 흡수한 잉크 문장(없으면 null). {origin} 자리표시는 여기서 채운다
     */
    static String evolve(String stage, DoodleSubject origin, String faction, String colors, String motifs, String absorbed) {
        boolean child = "child".equals(stage);
        boolean hasInk = !blank(absorbed);
        boolean strong = hasInk && absorbed.contains(LandCatalog.FULL_MARK);                 // 85% 이상(몸 전체가 바뀜): 겉모습이 크게 바뀔 수 있다
        boolean bodyChanges = hasInk && absorbed.contains(LandCatalog.BODY_CHANGE_MARK);  // 하체 분류가 바뀐다
        boolean themeV4 = hasInk && absorbed.contains(LandCatalog.THEME_MARK_V4);         // 잉크 시안 v4
        boolean themeV2 = themeV4 || hasInk && (absorbed.contains(LandCatalog.THEME_MARK_V2)
                || absorbed.contains(LandCatalog.THEME_MARK_V3));                         // 잉크 시안 v2·v3·v4
        boolean theme = themeV2 || hasInk && absorbed.contains(LandCatalog.THEME_MARK);   // 잉크 시안 v1~v4: 속성 재해석
        boolean redesign = themeV4 && !child;                                              // v4 완전체: 얼굴까지 새로 디자인
        String fidelity = redesign ? THEME_REDESIGN_V4 : theme ? THEME_KEEP : child ? (hasInk ? CLOSE : SAME) : (strong ? LOOSE : CLOSE);
        String growth = redesign ? ADULT_THEME_GROWTH + " " + CUTE_ANCHOR_V4
                : theme ? (child ? CHILD_THEME_GROWTH : ADULT_THEME_GROWTH) + " " + (themeV2 ? CUTE_ANCHOR_V2 : CUTE_ANCHOR)
                : child ? CHILD_GROWTH : ADULT_GROWTH;
        String identity = origin == null || blank(origin.subject()) ? "" :
                "Origin of this creature: " + origin.subject() + "." +
                (blank(origin.traits()) ? "" : redesign
                        ? " Its iconic traits, to be translated into the new design (not copied): " + origin.traits() + "."
                        : " Its distinctive features: " + origin.traits() + ".") + " ";
        String bodyPlan = bodyChanges ? ""
                : "Keep the body plan of image 1 (the same number of legs and the same overall posture family). ";
        String ink = hasInk
                ? LandCatalog.fillOrigin(absorbed, origin == null ? null : origin.subject(), origin == null ? null : origin.traits()) + " "
                : "";
        String decor = "Faction theme (small accents only, about " + (theme ? 10 : child ? 20 : 30) + "% intensity): " +
                (KNIGHT_FACTION.equals(faction) ? motifs : withoutScarves(motifs)) + ". " +
                "Use at most two of these motifs, expressed on the creature's own parts; they must never replace or hide the creature's own identity." +
                (themeV2 ? " Do not use faction colors; the ink theme colors decide the palette." : " Use " + colors + " as accent colors.");
        String eyes = blankEyesAllowed(faction, absorbed) ? "" : (redesign ? EYES_V4 : EYES) + " ";
        return ImageGenerationService.PIXEL_STYLE +
                "You are given two images. Image 1 is the CURRENT form of a tamagotchi-style pet. Image 2 is the original doodle the player drew: " +
                (redesign ? "the root of this creature's identity; carry over its spirit and its iconic traits, not its exact drawing. "
                        : "the root of this creature's identity (silhouette, proportions, distinctive features). ") +
                identity + fidelity + " " + bodyPlan + growth + " " + toolGrowth(origin) + ink + decor + " " + eyes + CLEAN + SIDE_VIEW + SOLID +
                "It must be ONE single creature. Never draw blueprints, diagrams or text. " + FRAME;
    }

    /**
     * 완성작 디자인 단계(텍스트 모델)의 지시문. 재료를 콘셉트 하나로 합쳐 콜라주를 막는다.
     * 정체성은 머리·얼굴, 주 몸 색, 처음 그림의 대표 특징이 들고 가고, 몸 구조는 자유롭게 바꿀 수 있다 (네발 → 직립 기사 등).
     */
    static String finalDesigner(DoodleSubject origin) {
        String signature = origin == null || blank(origin.traits()) ? "its most distinctive features in the image" : origin.traits();
        return "You are the lead character designer of a monster-raising game whose evolutions feel like Digimon or Pokemon evolutions.\n" +
                "You get the CURRENT form of a player's creature (image) and the ingredients of its next evolution: its origin, its stage, the ink data the player absorbed, and a faction theme.\n" +
                "Merge them into ONE coherent evolved character design, NOT a collage of stickers. The classic failure you must avoid: a skull from the faction, wings from one ink " +
                "and armor plates from another, each glued on separately in unrelated colors. Instead, find a single concept that explains every element, so each part looks like it was designed together.\n" +
                "Freedom is allowed: armor, helmets, masks, fierce looks, big bodies, cool or cute are all fine, as long as the design is unified.\n" +
                "Rules:\n" +
                "- Respect the transformation strength written in the ink data (decoration only / one part / prominent / full transformation).\n" +
                "- IDENTITY is carried by: the head and face shape (snout, horns, ears, eye style), the MAIN BODY COLOR read from the image " +
                "(it must be the first color of the palette; faction colors are only accents), and these signature features: " + signature +
                ". They must all survive, clearly visible, in the new concept.\n" +
                "- The BODY PLAN is NOT part of identity. For this FINAL form you may freely change it to fit the concept: a four-legged creature may stand up " +
                "into an upright two-legged warrior or knight, gain wings, etc. Choose what makes the most impressive evolution.\n" +
                "- Faction theme is optional seasoning: use at most one faction element, and only if it fits the concept; otherwise just use its colors or drop it.\n" +
                "- One clear focal point: the eyes stay vivid and readable (they may be partly covered by a helmet, but at least one eye clearly shines).\n" +
                "- A tight palette: 3 main colors + 1 accent, each repeated on several parts so the whole body reads as one design.\n" +
                "- It is ONE single creature: any companion, pet or rider in the ink data becomes part of its own body.\n" +
                "- Side view, facing the same direction as the image.\n" +
                "- Describe it in neutral design words (no gore, no violence).\n" +
                "Reply with JSON only: {\"concept\": one short phrase, \"palette\": \"...\", \"bodyPlan\": \"the new body plan and pose\", " +
                "\"silhouette\": \"how the outline changes and why\", \"focal\": \"...\", " +
                "\"parts\": [\"2-4 parts, each saying which ingredient it comes from and how it fits the concept\"], " +
                "\"dropped\": \"ingredients reduced to color or left out to keep it clean\"}";
    }

    /** 디자인 설계서대로 완성작을 그리는 그림 프롬프트 */
    static String finalFromBrief(com.fasterxml.jackson.databind.JsonNode b) {
        StringBuilder parts = new StringBuilder();
        b.path("parts").forEach(p -> parts.append(p.asText()).append(' '));
        return ImageGenerationService.PIXEL_STYLE +
                "You are given two images. Image 1 is the CURRENT form of a pet creature, image 2 is the original drawing it came from. " +
                "Draw its evolved form following this design brief exactly.\n" +
                "Concept: " + b.path("concept").asText() + ". Palette: " + b.path("palette").asText() + ". " +
                "Body plan and pose: " + b.path("bodyPlan").asText() + ". Silhouette: " + b.path("silhouette").asText() + ". " +
                "Focal point: " + b.path("focal").asText() + ". Parts: " + parts.toString().trim() + "\n" +
                "Everything on the body belongs to this one concept and palette; do not add any other decorations. Keep it recognizable as the creature of image 1.\n" +
                "View: a side view (or three-quarter side view) facing the same direction as image 1, never a front view. " +
                "Fill the whole body with solid, fully opaque flat colors, nothing see-through. It must be ONE single creature, no text. " + FRAME;
    }

    /** 친구와 배틀을 잘한 끄적이는 진화한 모습에 그 기세가 보인다 (같은 생물 그대로, 무기·글자 없이) */
    static String champion(int tier) {
        if (tier <= 0) return "";
        String base = "This creature has won most of its battles against friends: give it a proud, confident champion bearing " +
                "(a determined gaze, a strong stance) and slightly bolder, sharper details. ";
        if (tier == 1) return base;
        return base + "It is a grand champion: also let ONE small trophy-like emblem (a star, a crest or a medal shape) grow out of its own body " +
                "in its own colors. No crowns, no held objects, no text. ";
    }

    private static final String SAME = "Keep it recognizably the SAME character: the same face, colors, markings and body plan. It only matures.";
    private static final String CLOSE = "Keep its face, hair or markings, color palette and the most distinctive features of image 1 and image 2 " +
            "(do not simplify or drop details that image 1 already has). Its proportions, posture and details may change noticeably.";
    private static final String LOOSE = "Keep its color palette and its essence, but its outer design may change a lot: new proportions, a new silhouette, new parts.";

    private static final String CHILD_GROWTH = "This is the second stage: a more mature, more developed form, NOT merely a bigger copy of image 1. " +
            "Make ONE clear change to its SILHOUETTE that fits its nature (for example a taller crest, longer legs, a bigger tail or a more upright, " +
            "confident posture) so the outline itself looks more grown-up; a new color or pattern alone does not count. Keep everything else recognizable.";

    /** 스케치 → 완성작: 정규 진화처럼 눈에 띄게 강하고 멋있어지되, 같은 생물이어야 한다 */
    private static final String ADULT_GROWTH =
            "This is the FINAL, fully evolved form: a true evolution, like the grown-up upgrade of a digital monster. " +
            "It must look visibly stronger, cooler and more mature than image 1, and image 1 should look like its younger form. " +
            "Make the proportions more mature and imposing (a taller or more athletic build, longer limbs, a broader chest or a more refined body line, " +
            "a sharper face and a more confident or fierce expression), add refined armor-like plates, bands, spikes, markings or a signature accessory " +
            "that grow out of ITS OWN features, strengthen the color contrast, and choose a dynamic, confident side-view pose that fits its body plan. " +
            "The distinctive features listed in the origin above must still be clearly visible, evolved and exaggerated rather than dropped. " +
            "It is still the same creature (same face identity and palette), not a generic cute mascot and not a generic standing hero.";

    // ---------------- 잉크 진화 시안 v1 (LandCatalog.INK_STYLE_V1) ----------------
    /** v1: 색은 잉크가 바꾸므로 "색 유지"를 빼고, 얼굴·눈·몸 구조만 지킨다 */
    private static final String THEME_KEEP = "Keep it recognizably the SAME character as image 1 and image 2: the same face, the same eye design, " +
            "the same body plan and pose family, and its most distinctive features. Its colors, surface material and part shapes WILL change to the ink theme below.";

    private static final String CHILD_THEME_GROWTH = "This is the second stage: a slightly more developed form of image 1 " +
            "(a little more detail and a little more confident), and above all the ink-themed version of it.";

    /** v1 완성작: 강하고 무섭게가 아니라, 귀여운 스타터의 마지막 진화처럼 화려하고 완성된 느낌 */
    private static final String ADULT_THEME_GROWTH = "This is the FINAL form: like the last evolution of a cute starter monster that stays charming. " +
            "Make it feel complete and special rather than stronger: richer theme details, a more elegant and graceful line, a proud happy pose, " +
            "and one signature theme feature that clearly stands out. Image 1 should look like its younger form.";

    // ---------------- 잉크 진화 시안 v4: 완전체는 얼굴까지 새로 디자인 ----------------
    /**
     * v4 완전체: 옛 얼굴을 새 몸에 스티커처럼 붙이지 않는다(키메라). 머리·얼굴·몸을 한 그림체로 다시 그리고,
     * 누구였는지는 색·표정·대표 특징 한두 개를 새 몸의 구조로 바꿔서 잇는다 (고양이 귀 → 귀 모양 지느러미·뿔).
     */
    private static final String THEME_REDESIGN_V4 = "This is a real evolution, like a monster evolving into its final form: " +
            "REDESIGN the whole creature, head and face included, as ONE new species. Do NOT keep the old face of image 1 as a flat sticker on a new body; " +
            "the head, face, body, limbs and tail are drawn together in one style and one level of detail, and the body's texture continues over the head. " +
            "It is still recognizably the same individual through three things: its body color palette, its gentle personality and expression, " +
            "and its one or two most iconic traits TRANSLATED into the new anatomy instead of pasted on " +
            "(for example a cat's pointed ears become ear-shaped frills or horns on the new head, whiskers become thin feelers or fins, a long neck stays long). " +
            "Do not stack new parts on top of old ones (no horns piled on top of the old ears).";

    /** v4 완전체의 귀여움: 비율은 풀어 주고(새 몸을 디자인하게), 표정·둥근 선·눈으로만 지킨다 */
    static final String CUTE_ANCHOR_V4 = "It must stay lovable, the kind of pet people want to raise: soft rounded shapes, " +
            "a friendly face with big round eyes, and a gentle happy or proud expression. " +
            "Do NOT add muscles, fangs or sharp teeth, angry or fierce eyes, armor plates or weapon-like spikes.";

    /** v4 완전체의 눈: 모양은 새 얼굴에 맞게 바뀌어도 되고, 하이라이트만 꼭 지킨다 */
    private static final String EYES_V4 = "Eyes: big, round and friendly, drawn as part of the new face, " +
            "each with a clear small white highlight so it never becomes a blank solid-black oval.";

    /** v1에서 반드시 지키는 귀여움의 기준. 다마고치라 키우고 싶은 느낌이 먼저다 */
    static final String CUTE_ANCHOR = "It must stay lovable, the kind of pet people want to raise: a big head (about 40% of its height), " +
            "a round, soft silhouette, short stubby limbs, and a gentle happy or curious expression. " +
            "Do NOT add muscles, fangs or sharp teeth, angry or fierce eyes, armor plates, weapon-like spikes or big claws, " +
            "and do not make it taller, bulkier or more heroic than image 1.";

    /** v2: 머리 비율을 숫자로 정하지 않는다. 낙서 단계의 비율·몸 구조를 지키고 표정·둥근 선으로 귀여움을 지킨다 */
    static final String CUTE_ANCHOR_V2 = "It must stay lovable, the kind of pet people want to raise. Keep the proportions and body plan of image 1 " +
            "exactly (a long neck stays long, a long tail stays long, the same species shape as image 2), and keep the cuteness through soft rounded shapes, " +
            "a gentle happy or curious expression and the same eyes. " +
            "Do NOT add muscles, fangs or sharp teeth, angry or fierce eyes, armor plates, weapon-like spikes or big claws, " +
            "and do not make it taller, bulkier or more heroic than image 1.";

    /** 스카프·망토는 여러 진영에 겹쳐 모든 캐릭터가 비슷해 보이게 하므로 진영 모티프에서 뺀다 */
    static String withoutScarves(String motifs) {
        if (motifs == null) return "";
        return Arrays.stream(motifs.split(", *"))
                .filter(m -> !m.toLowerCase().contains("scarf") && !m.toLowerCase().contains("cape"))
                .collect(Collectors.joining(", "));
    }

    /** 어둠·악마 계열 진영이거나 악마 잉크를 흡수했으면 눈동자 없는 눈을 허용한다 */
    static boolean blankEyesAllowed(String faction, String absorbed) {
        if (faction != null && BLANK_EYE_FACTIONS.contains(faction)) return true;
        return absorbed != null && absorbed.toLowerCase().contains("demon");
    }

    private static boolean isLiving(DoodleSubject o) {
        String kind = o.kind() == null ? "" : o.kind().toLowerCase();
        return kind.equals("animal") || kind.equals("creature") || kind.equals("person");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
