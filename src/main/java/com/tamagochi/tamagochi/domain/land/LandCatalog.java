package com.tamagochi.tamagochi.domain.land;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 끄적랜드의 구역과 잉크(키워드) 정의. 구역 키는 진영 키와 같다.
 * 잉크는 구역마다 정해진 탐험 시간(분)에 발견된다. 발견 시간은 서버만 알고 화면에는 보내지 않는다.
 * 이미지 문구(prompt)는 진화 이미지에 넣을 고정 문구로, 클라이언트는 잉크 ID만 보낸다.
 */
public final class LandCatalog {

    private LandCatalog() {}

    public enum Grade { FAMILY, COMMON, RARE, LEGEND }

    /** 계열: 특정 잉크가 속하는 큰 갈래. 계열 잉크는 진화에서 질감·분위기를 맡는다 */
    public record Family(String key, String name, String prompt) {}

    /**
     * prompt: 이 잉크의 전체 특징(많이 바뀌는 단계). part: 조금 바뀌는 단계에서 쓰는 한 부위(없으면 prompt).
     * body: 크게 바뀌는 단계에서 희귀·전설이 바꾸는 하체 분류(없으면 체형 유지)
     */
    /**
     * colors: 몸을 칠할 대표 색(v2~). paint: 이 잉크가 몸 색을 바꾸는지(v3~, 없으면 계열로 정한다 — PAINT_FAMILIES)
     */
    public record Ink(String id, String emoji, String name, Grade grade, String family, String prompt, String part, Body body,
                      String colors, Boolean paint) {
        public Ink(String id, String emoji, String name, Grade grade, String family, String prompt, String part, Body body) {
            this(id, emoji, name, grade, family, prompt, part, body, null, null);
        }

        public String partOrPrompt() {
            return part != null ? part : prompt;
        }
    }

    /** discoverAt: 이 구역을 누적 몇 분 탐험하면 발견되는지 (인덱스는 inks와 같다) */
    public record Region(String id, String emoji, String name, boolean open, List<Ink> inks, List<Integer> discoverAt) {
        public int totalMinutes() {
            return discoverAt.isEmpty() ? 0 : discoverAt.get(discoverAt.size() - 1);
        }
    }

    // 탐험 깊이별 구간(분): 1구간은 계열·일반 앞쪽, 2구간은 일반 뒤쪽·희귀, 3구간은 전설
    static final int TIER1_END = 240;
    static final int TIER2_END = 720;
    static final int TIER3_END = 1080;
    static final int TIER1_COUNT = 13;

    /** 탐험 깊이(누적 분)에 따른 분당 소모 잉크. 깊이 들어갈수록 더 든다 */
    public static int costPerMinute(int exploredMinutes) {
        if (exploredMinutes < TIER1_END) return 2;
        if (exploredMinutes < TIER2_END) return 3;
        return 4;
    }

    private static final Map<String, Family> FAMILIES = new LinkedHashMap<>();
    private static final Map<String, Ink> INKS = new LinkedHashMap<>();
    private static final Map<String, Region> REGIONS = new LinkedHashMap<>();
    /** 잉크 id → 집 구역 id (그 잉크를 적어 둔 구역 파일). 계열 잉크는 집 구역이 없다 */
    private static final Map<String, String> HOME = new LinkedHashMap<>();
    /** 구역 id → 그 구역이 집인 잉크들 (도감의 구역별 잉크). 닫힌 구역에도 있다 */
    private static final Map<String, List<Ink>> HOME_INKS = new LinkedHashMap<>();

    public static Optional<Region> region(String id) {
        return Optional.ofNullable(id == null ? null : REGIONS.get(id));
    }

    public static List<Region> regions() {
        return List.copyOf(REGIONS.values());
    }

    public static Optional<Ink> ink(String id) {
        return Optional.ofNullable(id == null ? null : INKS.get(id));
    }

    public static Optional<Family> family(String key) {
        return Optional.ofNullable(key == null ? null : FAMILIES.get(key));
    }

    public static int familyCount() {
        return FAMILIES.size();
    }

    /** 잉크의 집 구역. 계열 잉크는 비어 있다 */
    public static Optional<String> homeRegion(String inkId) {
        return Optional.ofNullable(inkId == null ? null : HOME.get(inkId));
    }

    /** 이 구역이 집인 잉크들 (도감). 탐험 목록(Region.inks)과 달리 닫힌 구역에도 있고, 다른 구역 잉크는 없다 */
    public static List<Ink> homeInks(String regionId) {
        return HOME_INKS.getOrDefault(regionId, List.of());
    }

    /** 잉크 개수 → 흡수 비율(%). 특정(대표) 잉크는 2개, 계열 잉크는 1개로 센다 */
    static final int[] ABSORB_RATIO = {0, 35, 60, 85};
    /** 이 비율부터 몸 전체가 잉크의 모습으로 바뀐다 */
    static final int FULL_RATIO = 85;

    /** 단계별로 잉크가 겉모습을 바꿀 수 있는 최대 비율(%). 낙서가 갑자기 다른 생물이 되지 않게 한다 */
    static int stageCap(String stage) {
        return switch (stage == null ? "" : stage) {
            case "baby" -> 15;
            case "child" -> 50;
            default -> 90;
        };
    }

    /** 실제로 쓰는 비율 = min(잉크 비율, 단계 한도) */
    public static int absorbRatio(int units, String stage) {
        return Math.min(ABSORB_RATIO[Math.max(0, Math.min(units, ABSORB_RATIO.length - 1))], stageCap(stage));
    }

    /**
     * 하체 분류(인간형·4족형·2족 공룡 다리형·탱크형·다리없음·날개형): 체형까지 바꾸는 잉크(희귀·전설)가 쓴다. 자유 문장("네발로", "이족보행으로") 대신 다섯 가지로 고정하고
     * 다리 개수를 숫자로 못박으면 이미지 모델이 훨씬 잘 따른다(실험으로 확인).
     */
    public enum Body {
        HUMAN("a HUMANOID lower body: it stands upright on two ordinary legs and walks like a person",
                "HARD RULE: exactly TWO legs are visible, and the character stands upright on them."),
        QUAD("a FOUR-LEGGED lower body: it walks on four clearly visible legs, all four feet on the ground, with its body held horizontally like an animal (it must NOT stand upright on two legs)",
                "HARD RULE: exactly FOUR legs are visible, all on the ground."),
        DINO("a BIPEDAL DINOSAUR lower body: it walks on two strong dinosaur legs like a velociraptor or a tyrannosaurus, with a hunched forward-leaning torso, a long thick tail held out behind for balance, and three-toed clawed feet",
                "HARD RULE: exactly TWO legs are visible, on a hunched forward-leaning body with a long tail behind it."),
        TANK("a TANK-LIKE lower body: instead of legs it moves on caterpillar treads or big wheels, with a low, wide, armored base",
                "HARD RULE: ZERO legs are visible; the base is treads or wheels."),
        LEGLESS("a LEGLESS single-trunk body: ONE continuous trunk-like body with NO legs, NO feet and NO talons at all; where legs would be, the body continues as a tail (like a serpent, a ghost, a worm or a fish tail, or a jet exhaust), floating or resting smoothly on the ground",
                "HARD RULE: the number of visible legs, feet and talons is ZERO; the lower body is a tail."),
        FLY("a WINGED FLYING build: large prominent wings, flying or hovering in the air in a lifted posture (any legs are small and tucked away)",
                "HARD RULE: the large wings are clearly visible and the pose is airborne.");

        private final String description;
        private final String hardRule;

        Body(String description, String hardRule) {
            this.description = description;
            this.hardRule = hardRule;
        }

        String prompt() {
            return BODY_CHANGE_MARK + " " + description + ". The rest of the body must follow naturally from that. " + hardRule + " ";
        }
    }

    /** 프롬프트에 이 문구가 있으면 체형이 바뀌는 진화이므로, "이전 체형을 유지하라"는 문장을 넣지 않는다 */
    public static final String BODY_CHANGE_MARK = "Change its lower body type to";
    /** 강한 구간 문장에 들어가는 자리표시: 이미지 프롬프트를 만들 때 처음 그린 그림의 이름·특징으로 채운다 */
    public static final String ORIGIN_TOKEN = "{origin}";
    /** 몸 전체가 바뀌는 단계(85% 이상)의 문장 머리말: 이 문구가 있으면 겉모습이 크게 바뀌어도 된다 */
    public static final String FULL_MARK = "FULL TRANSFORMATION: ";
    public static final String TRAITS_TOKEN = "{origin-traits}";

    /**
     * 대표 잉크를 진화 이미지 프롬프트 문장으로 만든다. 문구는 모두 서버가 가진 고정 문구다.
     * 특정 잉크는 몸에 드러나는 특징("무엇이 되는가"), 계열 잉크는 질감·색·분위기만 맡는다.
     * 잉크를 많이 장착할수록(특정 2 + 계열 1씩) 비율이 올라가 그쪽 모습이 강해지고, 단계 한도를 넘지 않는다.
     *  - 15~34%: 장식만   - 35~54%: 부위 하나만 교체   - 55~84%: 그 특징을 크고 뚜렷하게(얼굴·체형은 유지)   - 85%~: 몸 전체가 바뀜
     * 무작위로 방식을 고르지 않는다. 같은 단계면 항상 같은 문장이라 잉크가 많을수록 더 강해진다.
     * 85%~는 등급마다 정해진 한 가지: 흔한 잉크는 얼굴 유지, 희귀는 부위 이식, 전설은 키메라. 체형(하체 분류)은 이 구간의 희귀·전설만 바꾼다.
     * 고른 잉크가 하나도 없으면 null.
     */
    // ---------------- 잉크 진화 시안 버전 (image.ink-style) ----------------
    // 시안마다 버전 이름과 git 태그(ink-evo-vN)를 붙여, 설정값 하나로 언제든 돌아갈 수 있게 한다.
    /** v0: 지금 방식. 비율만큼 장식 → 부위 하나 교체 → 체형 변화. 귀여움을 넣으면 변화가 작고(날개 하나), 변화를 키우면 귀여움이 사라졌다 */
    public static final String INK_STYLE_V0 = "v0";
    /**
     * v1: 속성 재해석(속성 진화형 방식). 부위를 붙이지 않고, 같은 귀여운 생물을 그 잉크의 색·재질·실루엣으로 다시 그린다.
     * 귀여움의 기준(큰 머리·둥근 몸·짧은 팔다리·같은 눈)은 고정하고, 근육·이빨·화난 눈·갑옷은 금지한다.
     */
    public static final String INK_STYLE_V1 = "v1";
    /**
     * v2: v1 + 고친 점 세 가지. ① 잉크마다 대표 색(colors)을 데이터로 정해 문장에 넣는다(없으면 실제 색을 모델이 고르게).
     * ② "머리는 키의 40%" 규칙을 빼고 낙서 단계의 비율을 지킨다(긴 목 공룡이 고양이가 됐다). ③ 진영 색 문장을 뺀다(잉크 색과 섞였다).
     */
    public static final String INK_STYLE_V2 = "v2";
    /**
     * v3: v2 + 잉크마다 "몸 색을 바꾸는지" 나눈다. 불·얼음·젤리·과자처럼 재질이 곧 정체인 잉크만 몸을 칠하고,
     * 와이번·여우처럼 모양이 정체인 잉크는 원래 몸 색을 지키고 모양·재질로만 바뀐다 (갑자기 색이 바뀌면 어색하다).
     */
    public static final String INK_STYLE_V3 = "v3";
    /** v3에서 몸 색을 바꾸는 계열: 재질이 곧 그 잉크인 것들. 잉크 데이터의 paint로 하나씩 바꿀 수 있다 */
    static final java.util.Set<String> PAINT_FAMILIES = java.util.Set.of("fire", "ice", "tentacle", "sweet", "cosmic", "ghost", "shadow", "steel");
    /** v3 문장 머리말 (v1·v2 머리말을 포함하지 않는다) */
    public static final String THEME_MARK_V3 = "INK THEME EVOLUTION (v3): ";
    /**
     * v4: v3 + 완전체는 얼굴까지 새로 디자인한다. v1~v3는 "같은 얼굴·같은 눈"을 지키게 해서 몸만 용이 되고
     * 얼굴은 낙서 단계 고양이 그대로 남아 키메라가 됐다. 누구였는지는 색·표정·대표 특징을 새 몸에 "번역"해서 잇는다.
     * 잉크 문장은 v3와 같고, 다른 점은 ImagePromptV3가 머리말을 보고 완전체 문장을 바꾸는 것이다.
     */
    public static final String INK_STYLE_V4 = "v4";
    /**
     * v5: 단계마다 역할을 나눈다. 스케치 = 잉크를 입은 아기(v3 잉크 문장 + 아기 체형), 완성작 = 정수를 뽑아 다시 태어남(디자인 후보 3개 중 고름).
     * 잉크 문장은 스케치에서만 쓰고(v3와 같다), 완성작은 inkTheme()을 디자이너에게 글로 준다. 그림 문장은 ImagePromptV3의 v5 부분.
     */
    public static final String INK_STYLE_V5 = "v5";

    public static boolean isV5(String style) {
        return INK_STYLE_V5.equalsIgnoreCase(style);
    }

    /** 잉크 한 칸(특정 잉크 또는 계열 잉크 하나)의 진화 문장. 없으면 null */
    public static String singleInkPrompt(String inkId, String stage, String style) {
        Ink ink = ink(inkId).orElse(null);
        if (ink == null) return null;
        return ink.grade() == Grade.FAMILY
                ? absorbedPrompt(null, List.of(ink.id()), stage, style)
                : absorbedPrompt(ink.id(), List.of(), stage, style);
    }

    /** 잉크 한 칸의 디자이너용 설명. 희귀·전설은 완성작을 더 크게 바꾸라고 덧붙인다. 없으면 null */
    public static String singleInkTheme(String inkId) {
        Ink ink = ink(inkId).orElse(null);
        if (ink == null) return null;
        String theme = ink.grade() == Grade.FAMILY ? inkTheme(null, List.of(ink.id())) : inkTheme(ink.id(), List.of());
        return theme + switch (ink.grade()) {
            case RARE -> " (a RARE ink: let it shape the final form strongly)";
            case LEGEND -> " (a LEGENDARY ink: let it shape the final form boldly and dominantly)";
            default -> "";
        };
    }

    /** v5 완성작 디자이너에게 줄 잉크 설명(대표 잉크 + 계열). 고른 잉크가 없으면 null */
    public static String inkTheme(String mainId, List<String> familyIds) {
        Ink main = ink(mainId).filter(i -> i.grade() != Grade.FAMILY).orElse(null);
        List<String> families = familyIds == null ? List.of() : familyIds.stream()
                .map(LandCatalog::ink).flatMap(Optional::stream)
                .filter(i -> i.grade() == Grade.FAMILY).map(Ink::prompt).distinct().toList();
        if (main == null && families.isEmpty()) return null;
        String theme = main != null ? main.name() + " (" + main.prompt() + ")" : String.join(" and ", families);
        return main != null && !families.isEmpty() ? theme + ", mixed with " + String.join(" and ", families) : theme;
    }
    public static final String THEME_MARK_V4 = "INK THEME EVOLUTION (v4): ";

    /** v3: 이 잉크가 몸 색을 바꾸는지. 데이터의 paint가 먼저, 없으면 계열로 정한다 */
    public static boolean paints(Ink ink) {
        return ink.paint() != null ? ink.paint() : PAINT_FAMILIES.contains(ink.family());
    }

    /** v1 문장 머리말: 이 문구가 있으면 ImagePromptV3가 "강하고 멋있게" 대신 귀여움을 지키는 성장 문장을 쓴다 */
    public static final String THEME_MARK = "INK THEME EVOLUTION: ";
    /** v2 문장 머리말 (v1 머리말을 포함하지 않는다) */
    public static final String THEME_MARK_V2 = "INK THEME EVOLUTION (v2): ";

    /** 시안 버전에 맞는 잉크 문장. v1은 스케치·완성작에만 쓰고, 낙서(유체)는 장식만이라 v0와 같다 */
    public static String absorbedPrompt(String mainId, List<String> familyIds, String stage, String style) {
        if ("baby".equals(stage)) return absorbedPrompt(mainId, familyIds, stage);
        if (INK_STYLE_V1.equalsIgnoreCase(style)) return themePrompt(mainId, familyIds, stage, 1);
        if (INK_STYLE_V2.equalsIgnoreCase(style)) return themePrompt(mainId, familyIds, stage, 2);
        if (INK_STYLE_V3.equalsIgnoreCase(style)) return themePrompt(mainId, familyIds, stage, 3);
        if (INK_STYLE_V4.equalsIgnoreCase(style)) return themePrompt(mainId, familyIds, stage, 4);
        if (isV5(style)) return themePrompt(mainId, familyIds, stage, 3); // 스케치용. 완성작은 inkTheme()으로 디자이너가 쓴다
        return absorbedPrompt(mainId, familyIds, stage);
    }

    /**
     * v1: 색(몸 대부분) + 재질(몸 전체 표면) + 실루엣(자기 부위 1~2곳의 모양)을 함께 바꿔 작은 썸네일에서도 확 달라 보이게 한다.
     * 완성작은 더 짙게(색 대부분·부위 2~3곳). 희귀·전설이 85% 이상이면 체형(하체 분류)도 v0처럼 바뀐다.
     */
    static String themePrompt(String mainId, List<String> familyIds, String stage, int version) {
        boolean v2 = version >= 2;
        Ink main = ink(mainId).filter(i -> i.grade() != Grade.FAMILY).orElse(null);
        List<Ink> familyInks = familyIds == null ? List.of() : familyIds.stream()
                .map(LandCatalog::ink).flatMap(Optional::stream)
                .filter(i -> i.grade() == Grade.FAMILY).distinct().toList();
        List<String> families = familyInks.stream().map(Ink::prompt).distinct().toList();
        // v3: 대표 잉크가 있으면 그 잉크로, 계열만 있으면 하나라도 칠하는 계열이면 칠한다. v1·v2는 늘 칠한다
        boolean paint = version < 3 || (main != null ? paints(main) : familyInks.stream().anyMatch(LandCatalog::paints));
        if (main == null && families.isEmpty()) return null;
        boolean adult = !"child".equals(stage);
        String theme = main != null ? main.prompt() : String.join(" and ", families);
        String mix = main != null && !families.isEmpty() ? " mixed with " + String.join(" and ", families) : "";
        StringBuilder sb = new StringBuilder(version >= 4 ? THEME_MARK_V4 : version == 3 ? THEME_MARK_V3 : v2 ? THEME_MARK_V2 : THEME_MARK);
        sb.append("The player chose this ink theme: ").append(theme).append(mix).append(". ");
        if (v2 && paint) {
            sb.append(main != null && main.colors() != null
                    ? "The theme colors are " + main.colors() + ". "
                    : "The theme colors are the real-life colors of that theme; pick them clearly and use them boldly. ");
        }
        sb
          .append("Like the elemental evolutions of one cute monster family, this creature evolves into the ink-themed version of ITSELF: ")
          .append("the same lovable creature, reborn in that theme. Do NOT glue a foreign part onto it; re-imagine its own body in the theme. ")
          .append("The change must be obvious even as a tiny thumbnail, through all three of these together: ")
          .append(paint
                ? "(1) COLOR: repaint " + (adult ? "almost all (about 80%)" : "most (about 60%)") + " of the body in "
                  + (v2 ? "the theme colors" : "the colors of that theme") + "; its old colors stay only as small accents such as the belly, cheeks or markings. "
                : "(1) COLOR: KEEP the body colors of image 1 exactly (the same main body color, belly and markings): this theme is about shape, not color. "
                  + "New theme parts use deeper or lighter shades of its OWN colors (for example wing membranes in a deeper shade of its body color), "
                  + "with at most one small accent in another color. ")
          .append("(2) MATERIAL: its own surface (fur, skin, shell or metal) becomes the material of that theme all over the body, not in one small spot. ")
          .append("(3) SILHOUETTE: reshape ").append(adult ? "two or three" : "one or two")
          .append(" of its OWN parts (ears, tail, crest, top of the head, cheeks or back) into shapes of that theme so the outline itself changes; ")
          .append("they grow out of its body as soft, rounded shapes. ");
        if (version >= 3 && main == null && !paint) {
            // 계열 잉크 하나(새·곤충·짐승 등 모양 계열): 몸 색을 지키면 작은 부위만 생겨 변화가 조용했다 → 무늬·포인트 색까지
            sb.append("Because this is a broad family ink, show it boldly: besides the new parts, add clear theme patterns and one accent color of the theme ")
              .append("over about a third of the body (for example feather patterns and a bright crest color for bird traits), while the main body color stays. ");
        }
        if (version >= 3 && main != null && main.grade() == Grade.RARE) sb.append("This is a RARE ink: make the theme bold and prominent. ");
        if (version >= 3 && main != null && main.grade() == Grade.LEGEND) sb.append("This is a LEGENDARY ink: make the theme dominant and spectacular. ");
        int ratio = main == null ? 0 : absorbRatio(2 + families.size(), stage);
        Body body = main == null || main.grade() == Grade.COMMON || ratio < FULL_RATIO ? null : main.body();
        if (body != null) sb.append(body.prompt());
        return sb.toString().trim();
    }

    public static String absorbedPrompt(String mainId, List<String> familyIds, String stage) {
        Ink main = ink(mainId).filter(i -> i.grade() != Grade.FAMILY).orElse(null);
        List<String> families = familyIds == null ? List.of() : familyIds.stream()
                .map(LandCatalog::ink).flatMap(Optional::stream)
                .filter(i -> i.grade() == Grade.FAMILY).map(Ink::prompt).distinct().toList();
        if (main == null && families.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("Absorbed ink data chosen by the player: ");
        int ratio = main == null ? 0 : absorbRatio(2 + families.size(), stage);
        if (main != null) {
            sb.append(mainText(main.prompt(), main.partOrPrompt(), ratio, main.grade()));
            Body body = main.grade() == Grade.COMMON || ratio < FULL_RATIO ? null : main.body();
            if (body != null) sb.append(body.prompt());
        }
        if (!families.isEmpty()) {
            sb.append("Its surface texture, colors and mood also carry ").append(String.join(" and ", families))
              .append("; use these only as texture, pattern and mood, not as extra body parts. ");
        }
        if (ratio < FULL_RATIO) sb.append("Keep the doodle's identity and the faction theme.");
        return sb.toString().trim();
    }

    /** 비율 구간별 합치는 방식. 단계마다 문장이 정해져 있다(무작위 없음). */
    static String mainText(String full, String part, int ratio, Grade grade) {
        if (ratio < 15) return "";
        if (ratio < 35) {
            return "Decoration only: add " + part + " as small accents (colors, textures, tiny parts). "
                    + "The body, posture, number of legs and silhouette stay unchanged. ";
        }
        if (ratio < 55) {
            return "Replace exactly ONE body part (for example the tail, the ears or a crest) with a version that truly has " + part
                    + ". Everything else stays unchanged and recognizable. The creature keeps its posture, its number of legs and its overall body shape. ";
        }
        String o = ORIGIN_TOKEN;
        if (ratio < FULL_RATIO) {
            return "Make " + part + " large, prominent and clearly visible, and add one or two smaller matching details (colors, textures, markings) "
                    + "that spread from it onto the nearby body. The face and head of " + o + " (the creature of image 2)" + TRAITS_TOKEN
                    + ", its posture, its number of legs and its overall body shape stay clearly recognizable. ";
        }
        return FULL_MARK + switch (grade) {
            case COMMON -> "The creature keeps the face and head of " + o + " (the creature of image 2) clearly recognizable, with its own distinctive facial shape"
                    + TRAITS_TOKEN + ", but its whole body becomes the body of a creature that truly has " + full
                    + ": these traits must be clearly visible on the body, limbs and silhouette, not just as small details. ";
            case RARE -> "The body truly takes on " + full + ", but one major body part (its wings or arms) keeps the version of " + o
                    + " with its own texture and shape (for a bird: real feathered wings with separate feather tips, NOT membrane or scaled wings), "
                    + "and the rest of the body clearly shows " + full + ". ";
            default -> "An even blend of " + o + " (from image 2) and a creature that truly has " + full + ": a chimera that clearly shows both. ";
        };
    }

    /** 자리표시를 처음 그린 그림의 이름·특징으로 채운다. 모르면 일반 표현을 쓴다 */
    public static String fillOrigin(String text, String originSubject, String originTraits) {
        if (text == null) return null;
        String subject = originSubject == null || originSubject.isBlank() ? "the ORIGIN creature" : originSubject;
        String traits = originTraits == null || originTraits.isBlank() ? "" : " (" + originTraits + ")";
        return text.replace(ORIGIN_TOKEN, subject).replace(TRAITS_TOKEN, traits);
    }

    /**
     * 발견 시간표. 표의 순서대로 발견된다.
     * 1구간(앞 13개): 처음엔 촘촘하고(1분에 첫 발견) 점점 넓어진다. 2구간: 고르게. 3구간(전설): 고르게.
     */
    static List<Integer> schedule(List<Ink> inks) {
        int legends = (int) inks.stream().filter(i -> i.grade() == Grade.LEGEND).count();
        int tier1 = Math.min(TIER1_COUNT, inks.size() - legends);
        int tier2 = inks.size() - legends - tier1;
        List<Integer> times = new ArrayList<>();
        int prev = 0;
        for (int i = 0; i < tier1; i++) {
            int t = i == 0 ? 1 : (int) Math.round(TIER1_END * Math.pow((i + 1) / (double) tier1, 1.7));
            prev = Math.max(prev + 1, t);
            times.add(prev);
        }
        for (int i = 0; i < tier2; i++) {
            prev = Math.max(prev + 1, TIER1_END + (TIER2_END - TIER1_END) * (i + 1) / tier2);
            times.add(prev);
        }
        for (int i = 0; i < legends; i++) {
            prev = Math.max(prev + 1, TIER2_END + (TIER3_END - TIER2_END) * (i + 1) / legends);
            times.add(prev);
        }
        return List.copyOf(times);
    }

    // ── 잉크 데이터 읽기 (resources/inks/) ──
    // families.json: 계열(이름·이모지·질감 문구). 계열 잉크 "family.<key>"는 여기서 만든다.
    // regions.json: 구역 순서. <구역 id>.json: 구역 정보, 탐험 순서(explore), 이 구역이 집인 잉크(한 줄에 하나).
    // 잉크는 집 구역 파일에 한 번만 쓰고, 다른 구역의 탐험 목록에서는 id로 가리킨다. 스파링 상대가 떨어뜨리는 잉크도 여기 있다.
    static final String DIR = "/inks/";

    static {
        JsonNode families = readJson("families.json");
        for (JsonNode f : families) {
            String key = text(f, "key");
            if (FAMILIES.put(key, new Family(key, text(f, "name"), text(f, "prompt"))) != null) throw new IllegalStateException("중복 계열: " + key);
            put(new Ink("family." + key, text(f, "emoji"), text(f, "name") + "의 잉크", Grade.FAMILY, key, text(f, "prompt"), null, null));
        }
        List<JsonNode> regionFiles = new ArrayList<>();
        for (JsonNode id : readJson("regions.json")) regionFiles.add(readJson(id.asText() + ".json"));
        // 잉크를 먼저 모두 등록하고(다른 구역의 잉크를 탐험 목록에서 가리킬 수 있게), 구역은 그다음에 만든다
        for (JsonNode r : regionFiles) {
            for (JsonNode i : r.get("inks")) {
                String family = text(i, "family");
                if (!FAMILIES.containsKey(family)) throw new IllegalStateException("없는 계열: " + family + " (" + text(i, "id") + ")");
                Grade grade = Grade.valueOf(text(i, "grade"));
                if (grade == Grade.FAMILY) throw new IllegalStateException("계열 잉크는 families.json 에: " + text(i, "id"));
                put(new Ink(text(i, "id"), text(i, "emoji"), text(i, "name"), grade, family, text(i, "prompt"),
                        i.hasNonNull("part") ? i.get("part").asText() : null,
                        i.hasNonNull("body") ? Body.valueOf(i.get("body").asText()) : null,
                        i.hasNonNull("colors") ? i.get("colors").asText() : null,
                        i.hasNonNull("paint") ? i.get("paint").asBoolean() : null));
                HOME.put(text(i, "id"), text(r, "id"));
                HOME_INKS.computeIfAbsent(text(r, "id"), k -> new ArrayList<>()).add(INKS.get(text(i, "id")));
            }
        }
        for (JsonNode r : regionFiles) {
            List<Ink> explore = new ArrayList<>();
            for (JsonNode id : r.get("explore")) {
                Ink ink = INKS.get(id.asText());
                if (ink == null) throw new IllegalStateException("탐험 목록에 없는 잉크: " + id.asText() + " (" + text(r, "id") + ")");
                explore.add(ink);
            }
            boolean open = r.get("open").asBoolean();
            if (open == explore.isEmpty()) throw new IllegalStateException("열린 구역에만 탐험 목록이 있어야 해요: " + text(r, "id"));
            REGIONS.put(text(r, "id"), new Region(text(r, "id"), text(r, "emoji"), text(r, "name"), open, List.copyOf(explore), schedule(explore)));
        }
    }

    private static void put(Ink ink) {
        if (INKS.put(ink.id(), ink) != null) throw new IllegalStateException("중복 잉크: " + ink.id());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) throw new IllegalStateException("빈 칸 " + field + ": " + node);
        return v.asText();
    }

    private static JsonNode readJson(String name) {
        try (InputStream in = LandCatalog.class.getResourceAsStream(DIR + name)) {
            if (in == null) throw new IllegalStateException(DIR + name + " 이 없어요");
            return new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(DIR + name + " 을 읽지 못했어요", e);
        }
    }
}
