package com.tamagochi.tamagochi.service.character;

/**
 * 이미지 프롬프트 v3: 한 덩어리 문장이 아니라 칸(슬롯)으로 나눠 조립한다.
 *   [스타일] [입력 역할] [정체] [방향] [성장 폭] [흡수한 잉크] [진영 장식] [마무리]
 * - 유체: 그림을 그대로 살린다. 사람·생물 그림도 합체(탈것·부위)하지 않는다.
 * - 성체/완전체: 직전 단계 이미지(1번)와 원본 낙서(2번)를 함께 보고, 크기만 키우지 않고 방향을 정해 진화한다.
 * - 흡수한 잉크가 있으면 그 칸에 들어간다(문장은 LandCatalog가 개수·단계 한도로 만든다). 없으면 성장만 한다.
 * 롤백: image.prompt-version=v2 로 두면 이전 프롬프트(ImageGenerationService.buildOpenAiPrompt)를 쓴다.
 */
final class ImagePromptV3 {

    private ImagePromptV3() {}

    private static final String FRAME = "Single character centered, filling about 70% of the frame, transparent background, " +
            "no shadow, no glow, no ground, no other characters.";

    /** 원본이 사람·생물이면 그 모습 그대로, 물체이면 물체 자체에 얼굴을 달아 캐릭터로 만든다 */
    static String baby(DoodleSubject o, String colors) {
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
     * @param origin   처음 그린 그림의 분석(없으면 null)
     * @param absorbed 흡수한 잉크 문장(없으면 null)
     */
    static String evolve(String stage, DoodleSubject origin, String colors, String motifs, String absorbed) {
        boolean child = "child".equals(stage);
        boolean hasInk = !blank(absorbed);
        // 방향: 잉크가 없으면 성숙(성체)·다듬기(완전체). 잉크가 강하게 들어오는 완전체는 겉모습이 크게 달라질 수 있다
        String fidelity = child ? (hasInk ? CLOSE : SAME) : (hasInk ? LOOSE : CLOSE);
        String growth = child
                ? "This is the second stage: a more mature, more developed form, NOT merely a bigger copy of image 1. " +
                  "Add exactly ONE new signature feature that fits its nature (for example a crest, longer limbs, a distinctive marking or a tail), " +
                  "and keep everything else recognizable."
                : "This is the final form: its most refined and distinctive design. Clearly more evolved than image 1: add one or two more " +
                  "distinctive features and refine the silhouette. Do NOT default to a generic standing hero pose; choose the pose and " +
                  "silhouette that best fit its body plan.";
        String identity = origin == null || blank(origin.subject()) ? "" :
                "Origin of this creature: " + origin.subject() + "." +
                (blank(origin.traits()) ? "" : " Its distinctive features: " + origin.traits() + ".") + " ";
        String bodyPlan = "Keep the body plan of image 1 (the same number of legs and the same overall posture family)" +
                (hasInk && !child ? ", unless the absorbed ink below says otherwise" : "") + ".";
        String decor = "Faction theme (small accents only, about " + (child ? 20 : 30) + "% intensity): " + withoutScarves(motifs) + ". " +
                "Use at most two of these motifs, expressed on the creature's own parts; they must never replace or hide the creature's own identity. " +
                "Use " + colors + " as accent colors.";
        return ImageGenerationService.PIXEL_STYLE +
                "You are given two images. Image 1 is the CURRENT form of a tamagotchi-style pet. Image 2 is the original doodle the player drew: " +
                "the root of this creature's identity (silhouette, proportions, distinctive features). " +
                identity + fidelity + " " + bodyPlan + " " + growth + " " +
                (hasInk ? absorbed + " " : "") + decor + " " +
                "It must be ONE single creature. Never draw blueprints, diagrams or text. " + FRAME;
    }

    private static final String SAME = "Keep it recognizably the SAME character: the same face, colors, markings and body plan. It only matures.";
    private static final String CLOSE = "Keep its face, hair or markings, color palette and the most distinctive features of image 1 and image 2 " +
            "(do not simplify or drop details that image 1 already has). Its proportions, posture and details may change noticeably.";
    private static final String LOOSE = "Keep its color palette and its essence, but its outer design may change a lot: new proportions, a new silhouette, new parts.";

    /** 스카프·망토는 여러 진영에 겹쳐 모든 캐릭터가 비슷해 보이게 하므로 진영 모티프에서 뺀다 */
    static String withoutScarves(String motifs) {
        if (motifs == null) return "";
        return java.util.Arrays.stream(motifs.split(", *"))
                .filter(m -> !m.toLowerCase().contains("scarf") && !m.toLowerCase().contains("cape"))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static boolean isLiving(DoodleSubject o) {
        String kind = o.kind() == null ? "" : o.kind().toLowerCase();
        return kind.equals("animal") || kind.equals("creature") || kind.equals("person");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
