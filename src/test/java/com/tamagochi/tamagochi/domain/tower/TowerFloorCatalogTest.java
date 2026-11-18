package com.tamagochi.tamagochi.domain.tower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 층 스키마(M-0): 실제 파일과, 깨진 데이터를 거절하는지 */
class TowerFloorCatalogTest {

    private static final String WHEELS = """
            [{"wheel": 1, "name": "창고", "firstFloor": 1, "lastFloor": 17},
             {"wheel": 2, "name": "공장", "firstFloor": 18, "lastFloor": 34},
             {"wheel": 3, "name": "본사", "firstFloor": 35, "lastFloor": 51}]""";

    private static final String MAPS = """
            [{"map": 1, "normal": 9, "swarm": 0, "boss": 0, "provisional": true},
             {"map": 2, "normal": 12, "swarm": 0, "boss": 0, "provisional": true},
             {"map": 3, "normal": 15, "swarm": 0, "boss": 0, "provisional": true},
             {"map": 4, "normal": 12, "swarm": 0, "boss": 0, "provisional": true},
             {"map": 5, "normal": 8, "swarm": 0, "boss": 1, "provisional": true}]""";

    /** 골격만 있는 한 층 */
    private static String skeleton(int floor) {
        return skeleton(floor, 5);
    }

    private static String skeleton(int floor, int maps) {
        return "{\"floor\": " + floor + ", \"wheel\": " + TowerFloorCatalog.wheelOf(floor)
                + ", \"faction\": \"" + TowerFloorCatalog.factionOf(floor) + "\", \"mapsPerFloor\": " + maps + "}";
    }

    /** 1~51층을 모두 적은 문서. floorEntry가 null을 돌려주면 그 층은 뺀다 */
    private static String doc(String rootFields, IntFunction<String> floorEntry) {
        List<String> entries = new ArrayList<>();
        for (int f = 1; f <= TowerFloorCatalog.SCHEMA_FLOOR_COUNT; f++) {
            String entry = floorEntry.apply(f);
            if (entry != null) entries.add(entry);
        }
        return "{" + rootFields + ", \"wheels\": " + WHEELS + ", \"floors\": [" + String.join(",", entries) + "]}";
    }

    private static final String ROOT = "\"openFloorCap\": 17, \"defaultMapsPerFloor\": 5";

    /** 1층만 바꾼 문서 */
    private static String withFloor1(String floor1) {
        return doc(ROOT, f -> f == 1 ? floor1 : skeleton(f));
    }

    private static void assertRejected(String json) {
        assertThatThrownBy(() -> TowerFloorCatalog.parse(json)).as(json).isInstanceOf(IllegalStateException.class);
    }

    private static List<String> regions() throws Exception {
        try (InputStream in = TowerFloorCatalogTest.class.getResourceAsStream("/inks/regions.json")) {
            List<String> ids = new ArrayList<>();
            for (JsonNode id : new ObjectMapper().readTree(in)) ids.add(id.asText());
            return ids;
        }
    }

    @Test
    @DisplayName("층 데이터 파일이 있고, 공개 층 상한은 17, 기본 맵 수는 5, 1~51층이 모두 있고 맵 수는 3보다 많다 (지금은 5)")
    void shippedData() {
        assertThat(TowerFloorCatalog.class.getResource(TowerFloorCatalog.RESOURCE)).isNotNull();
        TowerFloorCatalog.Floors floors = TowerFloorCatalog.floors();
        assertThat(TowerFloorCatalog.openFloorCap()).isEqualTo(17);
        assertThat(floors.defaultMapsPerFloor()).isEqualTo(5).isGreaterThanOrEqualTo(TowerFloorCatalog.MIN_MAPS_PER_FLOOR);
        assertThat(floors.byFloor()).hasSize(51);
        for (int floor = 1; floor <= 51; floor++) {
            TowerFloorCatalog.Floor f = TowerFloorCatalog.floor(floor).orElseThrow();
            assertThat(f.floor()).isEqualTo(floor);
            assertThat(TowerFloorCatalog.mapsPerFloor(floor)).as("floor " + floor).isGreaterThan(3).isEqualTo(5);
            assertThat(f.mapsPerFloor()).isEqualTo(5);
        }
        assertThat(TowerFloorCatalog.floor(0)).isEmpty();
        assertThat(TowerFloorCatalog.floor(52)).isEmpty();
        assertThat(TowerFloorCatalog.mapsPerFloor(99)).isEqualTo(5); // 스키마 밖의 층을 물어도 터지지 않는다
    }

    @Test
    @DisplayName("실제 파일에 같은 층이 두 번 적혀 있지 않다 (floors 항목 51개, 층 번호 1~51이 한 번씩)")
    void shippedFileHasNoDuplicateFloors() throws Exception {
        try (InputStream in = TowerFloorCatalog.class.getResourceAsStream(TowerFloorCatalog.RESOURCE)) {
            JsonNode floors = new ObjectMapper().readTree(in).get("floors");
            assertThat(floors).hasSize(51);
            List<Integer> numbers = new ArrayList<>();
            floors.forEach(n -> numbers.add(n.get("floor").asInt()));
            assertThat(new HashSet<>(numbers)).hasSize(51);
            assertThat(numbers).allMatch(n -> n >= 1 && n <= 51);
        }
    }

    @Test
    @DisplayName("바퀴 3개(창고 1~17, 공장 18~34, 본사 35~51). 바퀴마다 진영이 한 번씩, regions.json 순서 그대로")
    void wheelsAndFactions() throws Exception {
        List<String> regions = regions();
        assertThat(regions).containsExactly("nature-spirits", "deep-savers", "jungle-troopers", "metal-empire",
                "nightmare-soldiers", "wind-guardians", "virus-busters", "dragons-roar", "dark-area", "trash", "space",
                "desert", "snowfield", "volcano", "candy-land", "ninja", "magic-school");
        assertThat(TowerFloorCatalog.factions()).isEqualTo(regions);

        assertThat(TowerFloorCatalog.floors().wheels()).containsExactly(
                new TowerFloorCatalog.Wheel(1, "창고", 1, 17),
                new TowerFloorCatalog.Wheel(2, "공장", 18, 34),
                new TowerFloorCatalog.Wheel(3, "본사", 35, 51));

        for (int wheel = 1; wheel <= 3; wheel++) {
            List<String> factions = new ArrayList<>();
            for (int floor = (wheel - 1) * 17 + 1; floor <= wheel * 17; floor++) {
                TowerFloorCatalog.Floor f = TowerFloorCatalog.floor(floor).orElseThrow();
                assertThat(f.wheel()).as("floor " + floor).isEqualTo(wheel);
                assertThat(f.faction()).as("floor " + floor).isEqualTo(regions.get((floor - 1) % 17));
                factions.add(f.faction());
            }
            assertThat(factions).as("wheel " + wheel).isEqualTo(regions);
        }
    }

    @Test
    @DisplayName("1~2층만 내용이 있다: 구출 NPC id, 임시 맵 구성(9 / 12 / 15 / 12 / 8+보스1, 무리 0), 짧은 이야기 제목. 3~51층은 골격뿐")
    void floorsOneAndTwo() {
        TowerFloorCatalog.Floor one = TowerFloorCatalog.floor(1).orElseThrow();
        assertThat(one.faction()).isEqualTo("nature-spirits");
        assertThat(one.wheel()).isEqualTo(1);
        assertThat(one.rescueNpcIds()).containsExactly("ns-hamster", "ns-woodpecker");

        TowerFloorCatalog.Floor two = TowerFloorCatalog.floor(2).orElseThrow();
        assertThat(two.faction()).isEqualTo("deep-savers");
        assertThat(two.wheel()).isEqualTo(1);
        assertThat(two.rescueNpcIds()).containsExactly("ds-sea-otter");

        List<TowerFloorCatalog.MapMonsters> expected = List.of(
                new TowerFloorCatalog.MapMonsters(1, 9, 0, 0, true),
                new TowerFloorCatalog.MapMonsters(2, 12, 0, 0, true),
                new TowerFloorCatalog.MapMonsters(3, 15, 0, 0, true),
                new TowerFloorCatalog.MapMonsters(4, 12, 0, 0, true),
                new TowerFloorCatalog.MapMonsters(5, 8, 0, 1, true));
        for (TowerFloorCatalog.Floor f : List.of(one, two)) {
            assertThat(f.maps()).isEqualTo(expected);       // 두 층의 구성은 같고, 전부 임시다
            assertThat(f.maps()).hasSize(f.mapsPerFloor());
            assertThat(f.map(5).orElseThrow().boss()).isEqualTo(1);
            assertThat(f.map(0)).isEmpty();
            assertThat(f.map(6)).isEmpty();
            assertThat(f.story()).isNotNull();
            assertThat(f.story().title()).isNotBlank().hasSizeLessThanOrEqualTo(20);
            assertThat(f.story().provisional()).isTrue();
        }

        for (int floor = 3; floor <= 51; floor++) {
            TowerFloorCatalog.Floor f = TowerFloorCatalog.floor(floor).orElseThrow();
            assertThat(f.rescueNpcIds()).as("floor " + floor).isEmpty();
            assertThat(f.maps()).as("floor " + floor).isEmpty();
            assertThat(f.story()).as("floor " + floor).isNull();
            assertThat(f.map(1)).isEmpty();
        }
    }

    @Test
    @DisplayName("공개 층 상한보다 위 층(18~51)도 받는다. 층마다 맵 수를 따로 적을 수 있고, maps는 그 맵 수와 길이가 같으면 된다")
    void floorsAboveCapAndPerFloorMaps() {
        TowerFloorCatalog.Floors f = TowerFloorCatalog.parse(doc(ROOT, floor -> switch (floor) {
            case 1 -> skeleton(1, 4);
            case 17 -> skeleton(17, 9);
            case 40 -> skeleton(40, 6);
            case 2 -> skeleton(2).replace("}", ", \"rescueNpcIds\": [\"ds-sea-otter\"], \"story\": {\"title\": \"소품\"}, \"maps\": " + MAPS + "}");
            default -> skeleton(floor);
        }));
        assertThat(f.openFloorCap()).isEqualTo(17);
        assertThat(f.byFloor()).hasSize(51);
        assertThat(f.mapsPerFloor(1)).isEqualTo(4);
        assertThat(f.mapsPerFloor(2)).isEqualTo(5);
        assertThat(f.mapsPerFloor(17)).isEqualTo(9);
        assertThat(f.mapsPerFloor(40)).isEqualTo(6);
        assertThat(f.mapsPerFloor(99)).isEqualTo(5);
        assertThat(f.floor(18).orElseThrow().wheel()).isEqualTo(2);
        assertThat(f.floor(18).orElseThrow().faction()).isEqualTo("nature-spirits");
        assertThat(f.floor(51).orElseThrow().faction()).isEqualTo("magic-school");
        assertThat(f.floor(2).orElseThrow().maps()).hasSize(5);
        assertThat(f.floor(2).orElseThrow().story().provisional()).isFalse(); // 적지 않으면 임시가 아니다

        // 상한을 낮춰도 그 위 층은 데이터에 그대로 있다
        assertThat(TowerFloorCatalog.parse(doc("\"openFloorCap\": 3, \"defaultMapsPerFloor\": 4",
                TowerFloorCatalogTest::skeleton)).floor(51)).isPresent();
    }

    @Test
    @DisplayName("검증: 깨진 JSON, 중복 층, 빠진 층, 진영 순서·바퀴 오류, 맵 수와 maps 길이 불일치, 맵 수 4 미만, 필수 값 없음은 실패한다")
    void validation() {
        String valid = doc(ROOT, TowerFloorCatalogTest::skeleton);
        TowerFloorCatalog.parse(valid); // 기준 문서는 통과한다

        String[] bad = {
                valid.substring(0, valid.length() - 2),                                           // 문법 오류 (끝이 잘림)
                valid + " trailing",                                                              // 뒤에 남는 글자
                "[]",
                "{}",
                valid.replace("\"openFloorCap\": 17", "\"openFloorCap\": 17, \"openFloorCap\": 16"), // 같은 이름 두 번
                doc(ROOT, f -> f == 2 ? skeleton(2) + "," + skeleton(2) : skeleton(f)),           // 중복 층
                doc(ROOT, f -> f == 30 ? skeleton(30) + "," + skeleton(30, 6) : skeleton(f)),     // 중복 층 (상한 위)
                doc(ROOT, f -> f == 3 ? null : skeleton(f)),                                      // 3층이 빠짐
                doc(ROOT, f -> f == 51 ? null : skeleton(f)),                                     // 51층이 빠짐
                doc(ROOT, f -> f == 51 ? skeleton(51) + "," + skeleton(51).replace("51", "52") : skeleton(f)), // 52층
                doc(ROOT, f -> f == 1 ? skeleton(1).replace("\"floor\": 1", "\"floor\": 0") : skeleton(f)),    // 0층
                // 진영 순서 오류: 1층과 2층의 진영이 뒤바뀜
                doc(ROOT, f -> f == 1 ? skeleton(1).replace("nature-spirits", "deep-savers")
                        : f == 2 ? skeleton(2).replace("deep-savers", "nature-spirits") : skeleton(f)),
                withFloor1(skeleton(1).replace("nature-spirits", "forest")),                      // 모르는 진영
                doc(ROOT, f -> f == 18 ? skeleton(18).replace("nature-spirits", "deep-savers") : skeleton(f)), // 2바퀴 첫 진영이 틀림
                doc(ROOT, f -> f == 18 ? skeleton(18).replace("\"wheel\": 2", "\"wheel\": 1") : skeleton(f)),  // 바퀴가 틀림
                withFloor1(skeleton(1).replace(", \"wheel\": 1", "")),                            // 바퀴 없음
                withFloor1(skeleton(1).replace(", \"faction\": \"nature-spirits\"", "")),         // 진영 없음
                withFloor1(skeleton(1).replace(", \"mapsPerFloor\": 5", "")),                     // 맵 수 없음
                withFloor1(skeleton(1, 3)),                                                       // 층 맵 수 3
                withFloor1(skeleton(1, 4).replace("}", ", \"maps\": " + MAPS + "}")),             // 맵 수 4인데 maps 5장
                withFloor1(skeleton(1, 6).replace("}", ", \"maps\": " + MAPS + "}")),             // 맵 수 6인데 maps 5장
                withFloor1(skeleton(1).replace("}", ", \"maps\": " + MAPS.replace("\"map\": 4", "\"map\": 5") + "}")), // 맵 번호가 차례가 아님
                withFloor1(skeleton(1).replace("}", ", \"maps\": " + MAPS.replace("\"normal\": 9", "\"normal\": -1") + "}")), // 음수
                withFloor1(skeleton(1).replace("}", ", \"maps\": " + MAPS.replace("\"normal\": 9", "\"normal\": 0") + "}")),  // 몬스터 없는 맵
                withFloor1(skeleton(1).replace("}", ", \"maps\": " + MAPS.replace("\"swarm\": 0, \"boss\": 1", "\"boss\": 1") + "}")), // 무리 수 없음
                withFloor1(skeleton(1).replace("}", ", \"maps\": " + MAPS.replace("\"boss\": 1", "\"boss\": 1, \"recommendedBp\": 100") + "}")), // 전투력 숫자
                withFloor1(skeleton(1).replace("}", ", \"maps\": {}}")),                          // maps가 목록이 아님
                withFloor1(skeleton(1).replace("}", ", \"rescueNpcIds\": \"ns-hamster\"}")),      // 목록이 아님
                withFloor1(skeleton(1).replace("}", ", \"rescueNpcIds\": [\"ns-hamster\", \"ns-hamster\"]}")), // 같은 id 두 번
                withFloor1(skeleton(1).replace("}", ", \"rescueNpcIds\": [\"\"]}")),              // 빈 id
                withFloor1(skeleton(1).replace("}", ", \"rescueNpcIds\": [7]}")),                 // 글자가 아님
                withFloor1(skeleton(1).replace("}", ", \"story\": {}}")),                         // 제목 없는 소품
                withFloor1(skeleton(1).replace("}", ", \"battlePower\": 100}")),                  // 모르는 필드
                withFloor1("7"),                                                                  // 항목이 객체가 아님
                doc("\"openFloorCap\": 17, \"defaultMapsPerFloor\": 3", TowerFloorCatalogTest::skeleton),  // 기본 맵 수 3
                doc("\"openFloorCap\": 0, \"defaultMapsPerFloor\": 5", TowerFloorCatalogTest::skeleton),   // 상한 0
                doc("\"openFloorCap\": 52, \"defaultMapsPerFloor\": 5", TowerFloorCatalogTest::skeleton),  // 상한이 스키마 밖
                doc("\"defaultMapsPerFloor\": 5", TowerFloorCatalogTest::skeleton),                        // 상한 없음
                doc("\"openFloorCap\": 17", TowerFloorCatalogTest::skeleton),                              // 기본 맵 수 없음
                doc("\"openFloorCap\": \"17\", \"defaultMapsPerFloor\": 5", TowerFloorCatalogTest::skeleton), // 숫자가 아님
                valid.replace("\"lastFloor\": 34", "\"lastFloor\": 33"),                          // 바퀴 범위가 틀림
                valid.replace("{\"wheel\": 2", "{\"wheel\": 3"),                                  // 바퀴 번호가 차례가 아님
                valid.replace("\"name\": \"본사\", ", ""),                                         // 바퀴 이름 없음
                "{" + ROOT + ", \"wheels\": " + WHEELS + "}",                                     // floors 없음
                "{" + ROOT + ", \"floors\": []}"                                                  // wheels 없음
        };
        for (String json : bad) assertRejected(json);
    }
}
