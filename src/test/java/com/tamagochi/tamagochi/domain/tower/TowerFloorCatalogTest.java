package com.tamagochi.tamagochi.domain.tower;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TowerFloorCatalogTest {

    private static TowerFloorCatalog.Floors parse(String json) throws Exception {
        return TowerFloorCatalog.parse(new ObjectMapper().readTree(json));
    }

    @Test
    @DisplayName("층 데이터 파일이 있고, 공개 층 상한은 17, 모든 층의 맵 수는 3보다 많다 (지금은 5)")
    void shippedData() {
        assertThat(TowerFloorCatalog.class.getResource(TowerFloorCatalog.RESOURCE)).isNotNull();
        assertThat(TowerFloorCatalog.openFloorCap()).isEqualTo(17);
        assertThat(TowerFloorCatalog.floors().defaultMapsPerFloor()).isEqualTo(5);
        for (int floor = 1; floor <= TowerFloorCatalog.openFloorCap(); floor++) {
            assertThat(TowerFloorCatalog.mapsPerFloor(floor)).as("floor " + floor).isGreaterThan(3).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("층마다 맵 수를 따로 적을 수 있고, 적지 않은 층은 기본값을 쓴다")
    void perFloorOverrideAndDefault() throws Exception {
        TowerFloorCatalog.Floors f = parse("""
                {"openFloorCap": 17, "defaultMapsPerFloor": 5,
                 "floors": [{"floor": 1, "mapsPerFloor": 4}, {"floor": 17, "mapsPerFloor": 9}]}""");
        assertThat(f.mapsPerFloor(1)).isEqualTo(4);
        assertThat(f.mapsPerFloor(2)).isEqualTo(5);
        assertThat(f.mapsPerFloor(17)).isEqualTo(9);
        assertThat(f.mapsPerFloor(99)).isEqualTo(5); // 상한 밖의 층을 물어도 터지지 않는다

        assertThat(parse("{\"openFloorCap\": 3, \"defaultMapsPerFloor\": 4}").mapsPerFloor(2)).isEqualTo(4); // floors는 없어도 된다
    }

    @Test
    @DisplayName("검증: 맵 수는 4 이상, 층 번호는 1~상한, 같은 층을 두 번 적지 않고, 필수 값이 있어야 한다")
    void validation() {
        String[] bad = {
                "{\"openFloorCap\": 17, \"defaultMapsPerFloor\": 3}",                                              // 기본 맵 수 3
                "{\"openFloorCap\": 17, \"defaultMapsPerFloor\": 5, \"floors\": [{\"floor\": 1, \"mapsPerFloor\": 3}]}", // 층 맵 수 3
                "{\"openFloorCap\": 17, \"defaultMapsPerFloor\": 5, \"floors\": [{\"floor\": 0, \"mapsPerFloor\": 5}]}", // 0층
                "{\"openFloorCap\": 17, \"defaultMapsPerFloor\": 5, \"floors\": [{\"floor\": 18, \"mapsPerFloor\": 5}]}", // 상한 밖
                "{\"openFloorCap\": 17, \"defaultMapsPerFloor\": 5, \"floors\": [{\"floor\": 2, \"mapsPerFloor\": 5}, {\"floor\": 2, \"mapsPerFloor\": 6}]}",
                "{\"openFloorCap\": 17, \"defaultMapsPerFloor\": 5, \"floors\": [{\"floor\": 2}]}",                // 맵 수 없음
                "{\"openFloorCap\": 0, \"defaultMapsPerFloor\": 5}",                                               // 상한 0
                "{\"defaultMapsPerFloor\": 5}",                                                                    // 상한 없음
                "{\"openFloorCap\": 17}",                                                                          // 기본 맵 수 없음
                "{\"openFloorCap\": \"17\", \"defaultMapsPerFloor\": 5}",                                         // 숫자가 아님
                "[]"
        };
        for (String json : bad) {
            assertThatThrownBy(() -> parse(json)).as(json).isInstanceOf(IllegalStateException.class);
        }
    }
}
