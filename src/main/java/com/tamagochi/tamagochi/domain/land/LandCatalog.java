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
    public record Ink(String id, String emoji, String name, Grade grade, String family, String prompt, String part, Body body) {
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
                        i.hasNonNull("body") ? Body.valueOf(i.get("body").asText()) : null));
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
