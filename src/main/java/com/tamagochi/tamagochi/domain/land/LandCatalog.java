package com.tamagochi.tamagochi.domain.land;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public record Ink(String id, String emoji, String name, Grade grade, String family, String prompt) {}

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

    /** 단계별로 잉크가 겉모습을 바꿀 수 있는 최대 비율(%). 유체가 갑자기 다른 생물이 되지 않게 한다 */
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
     * 대표 잉크를 진화 이미지 프롬프트 문장으로 만든다. 문구는 모두 서버가 가진 고정 문구다.
     * 특정 잉크는 몸에 드러나는 특징("무엇이 되는가"), 계열 잉크는 질감·색·분위기만 맡는다.
     * 잉크를 많이 장착할수록(특정 2 + 계열 1씩) 비율이 올라가 그쪽 모습이 강해지고, 단계 한도를 넘지 않는다.
     *  - 15~34%: 장식만   - 35~54%: 부위 하나만 교체   - 55~84%: 얼굴 유지 / 부위 이식 / 동반 중 하나   - 85%~: 키메라도 가능
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
        if (main != null) sb.append(mainText(main.prompt(), ratio, Math.abs(Objects.hash(main.id(), stage))));
        if (!families.isEmpty()) {
            sb.append("Its surface texture, colors and mood also carry ").append(String.join(" and ", families))
              .append("; use these only as texture, pattern and mood, not as extra body parts. ");
        }
        if (ratio < 55) sb.append("Keep the doodle's identity and the faction theme.");
        return sb.toString().trim();
    }

    /** 비율 구간별 합치는 방식. 55~84%는 시드로 한 가지를 고른다(같은 잉크·단계면 같은 결과) */
    static String mainText(String g, int ratio, int seed) {
        if (ratio < 15) return "";
        if (ratio < 35) {
            return "Decoration only: add " + g + " as small accents (colors, textures, tiny parts). The body and silhouette stay unchanged. ";
        }
        if (ratio < 55) {
            return "Replace exactly ONE body part (for example the tail, the ears or a crest) with a version that truly has " + g
                    + ". Everything else stays unchanged and recognizable. ";
        }
        String[] modes = {
                "The creature keeps the ORIGIN's face and head (the creature of image 2) clearly recognizable, but its whole body becomes the body of a creature that truly has "
                        + g + ": these traits must be clearly visible on the body, limbs and silhouette, not just as small details. ",
                "The body truly takes on " + g + ", but one major body part (its wings or arms) keeps the ORIGIN's own version with the origin's own texture and shape "
                        + "(for a bird: real feathered wings with separate feather tips, NOT membrane or scaled wings), and the rest of the body clearly shows " + g + ". ",
                "A warrior-like character that truly takes on " + g + ", holding or accompanied by the ORIGIN creature (from image 2) as a companion, "
                        + "both clearly visible, still ONE sprite. ",
                "An even blend of the ORIGIN creature (from image 2) and a creature that truly has " + g + ": a chimera that clearly shows both. "
        };
        int pool = ratio >= 85 ? modes.length : 3;
        return modes[seed % pool];
    }

    private static void family(String key, String name, String prompt) {
        FAMILIES.put(key, new Family(key, name, prompt));
    }

    /** 계열 잉크: 이름은 "<계열>의 잉크", 문구는 계열 문구를 쓴다 */
    private static Ink familyInk(String key, String emoji) {
        Family f = FAMILIES.get(key);
        return ink("family." + key, emoji, f.name() + "의 잉크", Grade.FAMILY, key, f.prompt());
    }

    private static Ink ink(String id, String emoji, String name, Grade grade, String family, String prompt) {
        if (!FAMILIES.containsKey(family)) throw new IllegalStateException("unknown family: " + family);
        Ink existing = INKS.get(id);
        if (existing != null) return existing; // 같은 잉크가 여러 구역에 나올 수 있다
        Ink created = new Ink(id, emoji, name, grade, family, prompt);
        INKS.put(id, created);
        return created;
    }

    private static Ink c(String id, String emoji, String name, String family, String prompt) {
        return ink(id, emoji, name, Grade.COMMON, family, prompt);
    }

    private static Ink r(String id, String emoji, String name, String family, String prompt) {
        return ink(id, emoji, name, Grade.RARE, family, prompt);
    }

    private static Ink l(String id, String emoji, String name, String family, String prompt) {
        return ink(id, emoji, name, Grade.LEGEND, family, prompt);
    }

    private static void region(String id, String emoji, String name, Ink... inks) {
        List<Ink> list = List.of(inks);
        REGIONS.put(id, new Region(id, emoji, name, !list.isEmpty(), list, schedule(list)));
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

    static {
        family("beast", "짐승", "beast traits: fur tufts, pointed ears, a bushy tail, paws");
        family("bird", "새", "bird traits: feathers, a small beak, feathered wings, a crest");
        family("insect", "곤충", "insect traits: antennae, glossy shell plates, translucent wing textures");
        family("spirit", "정령", "spirit traits: soft glowing wisps, leaf-sprite markings, a faint magical aura");
        family("fish", "물고기", "fish traits: fins, smooth scales, a forked tail fin");
        family("crustacean", "갑각류", "crustacean traits: a hard segmented shell, small pincers, jointed legs");
        family("tentacle", "촉수", "tentacle traits: soft wavy tentacles, a jelly-like glossy skin, suction-cup dots");
        family("marine", "바다짐승", "marine mammal traits: sleek smooth skin, flippers, a round snout");
        family("plant", "식물", "plant traits: leaves, curling vines, sprouts, bark textures");
        family("flower", "꽃", "flower traits: petals, flower buds, pollen sparkles, a floral collar");
        family("reptile", "파충류", "reptile traits: small scales, a long tail, sturdy claws, a frill or spikes");
        family("primate", "영장류", "primate traits: long arms, nimble hands, a curling tail, an expressive face");
        family("clockwork", "톱니", "clockwork traits: gears, wind-up keys, rivets, mechanical joints");
        family("electric", "전기", "electric traits: glowing circuit lines, an antenna, small sparks, indicator lights");
        family("steel", "강철", "steel traits: polished metal plating, bolts, a visor, armored limbs");
        family("engine", "엔진", "engine traits: wheels, exhaust pipes, headlights, racing stripes");

        region("nature-spirits", "🌿", "정령의 숲",
                familyInk("beast", "🐾"), familyInk("bird", "🐦"), familyInk("insect", "🐛"), familyInk("spirit", "🍃"),
                c("rabbit", "🐰", "토끼의 잉크", "beast", "a rabbit's long floppy ears"),
                c("squirrel", "🐿️", "다람쥐의 잉크", "beast", "a squirrel's big curled fluffy tail"),
                c("otter", "🦦", "수달의 잉크", "beast", "an otter's sleek fur and little webbed paws"),
                c("raccoon", "🦝", "너구리의 잉크", "beast", "a raccoon's mask-like eye markings and ringed tail"),
                c("hedgehog", "🦔", "고슴도치의 잉크", "beast", "a hedgehog's soft spiky back"),
                c("ladybug", "🐞", "무당벌레의 잉크", "insect", "a ladybug's red spotted shell"),
                c("woodpecker", "🐦", "딱따구리의 잉크", "bird", "a woodpecker's red crest and pointed beak"),
                c("fox", "🦊", "여우의 잉크", "beast", "a fox's big bushy tail with a white tip"),
                c("deer", "🦌", "사슴의 잉크", "beast", "a deer's small branching antlers"),
                c("boar", "🐗", "멧돼지의 잉크", "beast", "a wild boar's small tusks and bristly mane"),
                c("owl", "🦉", "부엉이의 잉크", "bird", "an owl's ear tufts and big round eyes"),
                c("rhino-beetle", "🪲", "장수풍뎅이의 잉크", "insect", "a rhinoceros beetle's big curved horn"),
                c("stag-beetle", "🪲", "사슴벌레의 잉크", "insect", "a stag beetle's pincer-like jaws"),
                c("will-o-wisp", "🔥", "도깨비불의 잉크", "spirit", "small floating will-o'-the-wisp flames"),
                c("cat", "🐱", "고양이의 잉크", "beast", "a cat's pointed ears, whiskers and a slender swaying tail"),
                c("puppy", "🐶", "강아지의 잉크", "beast", "a puppy's floppy ears, round snout and wagging tail"),
                c("hamster", "🐹", "햄스터의 잉크", "beast", "a hamster's chubby cheek pouches and tiny round ears"),
                r("wolf", "🐺", "늑대의 잉크", "beast", "a wolf's thick neck mane and sharp ears"),
                r("bear", "🐻", "곰의 잉크", "beast", "a bear's round ears and big sturdy paws"),
                r("tiger", "🐯", "호랑이의 잉크", "beast", "a tiger's bold stripes"),
                r("tree-spirit", "🌳", "나무 정령의 잉크", "spirit", "bark-like skin patches with sprouting twigs"),
                r("firefly", "✨", "반딧불이의 잉크", "insect", "a firefly's soft glowing light from the body"),
                l("white-tiger", "🤍", "백호의 잉크", "beast", "a white tiger's snow-white fur with silver stripes"),
                l("nine-tailed-fox", "🦊", "구미호의 잉크", "beast", "nine flowing fox tails"),
                l("forest-guardian", "🌲", "숲의 수호령의 잉크", "spirit", "a crown of living branches and a glowing forest aura"));

        region("deep-savers", "🌊", "깊은 바다",
                familyInk("fish", "🐟"), familyInk("crustacean", "🦀"), familyInk("tentacle", "🐙"), familyInk("marine", "🐬"),
                c("clownfish", "🐠", "흰동가리의 잉크", "fish", "a clownfish's bright orange and white stripes"),
                c("pufferfish", "🐡", "복어의 잉크", "fish", "a pufferfish's round puffed body with soft spikes"),
                c("crab", "🦀", "꽃게의 잉크", "crustacean", "a crab's two big claws"),
                c("hermit-crab", "🐚", "소라게의 잉크", "crustacean", "a spiral seashell carried on the back"),
                c("seahorse", "🌊", "해마의 잉크", "fish", "a seahorse's curled tail and snout"),
                c("jellyfish", "🪼", "해파리의 잉크", "tentacle", "a jellyfish's translucent bell and trailing ribbons"),
                c("squid", "🦑", "오징어의 잉크", "tentacle", "a squid's pointed fin head and long arms"),
                c("sea-turtle", "🐢", "바다거북의 잉크", "reptile", "a sea turtle's patterned shell and flippers"),
                c("seal", "🦭", "물개의 잉크", "marine", "a seal's whiskers and smooth flippers"),
                c("sea-otter", "🦦", "해달의 잉크", "marine", "a sea otter's fluffy face and tiny paws holding a shell"),
                c("penguin", "🐧", "펭귄의 잉크", "bird", "a penguin's tuxedo-like black and white body"),
                c("dolphin", "🐬", "돌고래의 잉크", "marine", "a dolphin's curved dorsal fin and friendly snout"),
                c("lobster", "🦞", "바닷가재의 잉크", "crustacean", "a lobster's big pincer claws and segmented tail"),
                c("manta-ray", "🫓", "가오리의 잉크", "fish", "a manta ray's wide wing-like fins"),
                r("shark", "🦈", "상어의 잉크", "fish", "a shark's dorsal fin and a toothy grin"),
                r("octopus", "🐙", "문어의 잉크", "tentacle", "an octopus's eight curling tentacles"),
                r("whale", "🐋", "고래의 잉크", "marine", "a whale's huge tail fluke and a water spout"),
                r("orca", "🐳", "범고래의 잉크", "marine", "an orca's bold black and white markings"),
                r("anglerfish", "🏮", "아귀의 잉크", "fish", "an anglerfish's glowing lure dangling above the head"),
                l("megalodon", "🦈", "메갈로돈의 잉크", "fish", "a giant prehistoric shark's massive jaws and fin"),
                l("kraken", "🌀", "크라켄의 잉크", "tentacle", "colossal kraken tentacles swirling around the body"),
                l("leviathan", "🐉", "리바이어던의 잉크", "marine", "a sea serpent's long finned body and glowing scales"));

        region("jungle-troopers", "🦍", "정글 기지",
                familyInk("plant", "🌿"), familyInk("flower", "🌺"), familyInk("reptile", "🦎"), familyInk("primate", "🐒"),
                c("monkey", "🐒", "원숭이의 잉크", "primate", "a monkey's long curling tail and big ears"),
                c("sloth", "🦥", "나무늘보의 잉크", "beast", "a sloth's long hooked claws and sleepy eye patches"),
                c("parrot", "🦜", "앵무새의 잉크", "bird", "a parrot's rainbow feathers and hooked beak"),
                c("toucan", "🐦", "큰부리새의 잉크", "bird", "a toucan's huge colorful beak"),
                c("dart-frog", "🐸", "독개구리의 잉크", "reptile", "a poison dart frog's bright spotted skin"),
                c("chameleon", "🦎", "카멜레온의 잉크", "reptile", "a chameleon's curled tail and color-shifting patches"),
                c("snake", "🐍", "뱀의 잉크", "reptile", "a snake's long patterned scaly tail"),
                c("flytrap", "🪴", "식충식물의 잉크", "plant", "a venus flytrap's toothed leaf jaws"),
                c("rafflesia", "🌸", "라플레시아의 잉크", "flower", "a giant rafflesia's thick spotted petals"),
                c("mantis", "🦗", "사마귀의 잉크", "insect", "a praying mantis's scythe-like forearms"),
                c("hippo", "🦛", "하마의 잉크", "beast", "a hippo's wide snout and round sturdy body"),
                c("rhino", "🦏", "코뿔소의 잉크", "beast", "a rhino's big nose horn and armored hide"),
                c("elephant", "🐘", "코끼리의 잉크", "beast", "an elephant's long trunk and big flapping ears"),
                c("leopard", "🐆", "표범의 잉크", "beast", "a leopard's spotted rosette fur"),
                r("gorilla", "🦍", "고릴라의 잉크", "primate", "a gorilla's broad strong shoulders and silver back"),
                r("crocodile", "🐊", "악어의 잉크", "reptile", "a crocodile's toothy snout and ridged back"),
                r("black-panther", "🐈‍⬛", "흑표범의 잉크", "beast", "a black panther's sleek glossy black fur"),
                r("morpho", "🦋", "모르포나비의 잉크", "insect", "a morpho butterfly's shimmering blue wings"),
                r("triceratops", "🦕", "트리케라톱스의 잉크", "reptile", "a triceratops's three horns and bony frill"),
                l("lion-king", "🦁", "사자왕의 잉크", "beast", "a lion king's grand golden mane"),
                l("t-rex", "🦖", "티라노사우루스의 잉크", "reptile", "a tyrannosaurus's mighty jaws and tiny arms"),
                l("jungle-giant-tree", "🌳", "정글 거목의 잉크", "plant", "an ancient giant tree's roots and canopy growing from the body"));

        region("metal-empire", "🤖", "기계 왕국",
                familyInk("clockwork", "⚙️"), familyInk("electric", "⚡"), familyInk("steel", "🔩"), familyInk("engine", "🔥"),
                c("car", "🚗", "자동차의 잉크", "engine", "a car's round wheels and headlight eyes"),
                c("bus", "🚌", "버스의 잉크", "engine", "a bus's boxy body with rows of windows"),
                c("motorcycle", "🏍️", "오토바이의 잉크", "engine", "a motorcycle's handlebars and chrome exhaust"),
                c("tractor", "🚜", "트랙터의 잉크", "steel", "a tractor's big rugged rear wheels"),
                c("excavator", "🏗️", "굴착기의 잉크", "steel", "an excavator's digging bucket arm"),
                c("dump-truck", "🚚", "덤프트럭의 잉크", "steel", "a dump truck's tilting cargo bed"),
                c("fire-truck", "🚒", "소방차의 잉크", "engine", "a fire truck's red body, ladder and siren"),
                c("police-car", "🚓", "경찰차의 잉크", "engine", "a police car's flashing light bar"),
                c("steam-train", "🚂", "증기기관차의 잉크", "clockwork", "a steam locomotive's chimney puffing smoke"),
                c("helicopter", "🚁", "헬리콥터의 잉크", "engine", "a helicopter's spinning rotor on the head"),
                c("airplane", "✈️", "비행기의 잉크", "engine", "an airplane's wide wings and tail fin"),
                c("drone", "🛸", "드론의 잉크", "electric", "small drone propellers hovering around the body"),
                c("windup-robot", "🤖", "태엽 로봇의 잉크", "clockwork", "a wind-up key turning on the back"),
                c("submarine", "🌊", "잠수함의 잉크", "steel", "a submarine's periscope and round portholes"),
                r("supercar", "🏎️", "슈퍼카의 잉크", "engine", "a supercar's sleek low body and spoiler"),
                r("tank", "🪖", "탱크의 잉크", "steel", "a tank's caterpillar tracks and turret cannon"),
                r("fighter-jet", "🛩️", "전투기의 잉크", "engine", "a fighter jet's swept-back wings and afterburner glow"),
                r("bullet-train", "🚄", "고속열차의 잉크", "electric", "a bullet train's streamlined nose"),
                r("rocket", "🚀", "로켓의 잉크", "engine", "rocket boosters with blazing flames"),
                l("combining-robot", "🦾", "합체 로봇의 잉크", "steel", "a giant combining robot's heroic armor and chest emblem"),
                l("space-shuttle", "🛰️", "우주왕복선의 잉크", "engine", "a space shuttle's white heat-shield wings and launch flames"),
                l("machine-king", "👑", "기계왕의 잉크", "steel", "golden royal armor plates engraved with gears"));

        // 아직 열지 않은 구역: 지도에 "준비 중"으로 보인다
        region("nightmare-soldiers", "🏚️", "유령의집");
        region("wind-guardians", "🏞️", "계곡");
        region("virus-busters", "🛡️", "기사들의 성");
        region("dragons-roar", "🐉", "용 둥지");
        region("dark-area", "😈", "어둠의 세계");
        region("trash", "🗑️", "매립지");
        region("space", "🚀", "우주 정거장");
        region("desert", "🏜️", "사막");
        region("snowfield", "❄️", "설원");
        region("volcano", "🌋", "화산");
        region("candy-land", "🍭", "과자나라");
        region("ninja", "🥷", "이가닌자의 마을");
        region("magic-school", "🪄", "마법학교");
    }
}
