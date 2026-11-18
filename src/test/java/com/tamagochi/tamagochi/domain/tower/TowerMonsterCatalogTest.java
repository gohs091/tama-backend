package com.tamagochi.tamagochi.domain.tower;

import com.tamagochi.tamagochi.domain.tower.TowerMonsterCatalog.FloorMonsters;
import com.tamagochi.tamagochi.domain.tower.TowerMonsterCatalog.Role;
import com.tamagochi.tamagochi.domain.tower.TowerMonsterCatalog.Stats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TowerMonsterCatalogTest {

    private static final String ROLES = "\"normal\": {\"hp\": 100, \"attack\": 24}, \"swarm\": {\"hp\": 30, \"attack\": 8}, "
            + "\"boss\": {\"hp\": 520, \"attack\": 38}";

    private static String floors(String... entries) {
        return "{\"floors\": [" + String.join(",", entries) + "]}";
    }

    @Test
    @DisplayName("몬스터 데이터 파일이 있고 그대로 읽힌다: 1~2층만 있고, 층마다 일반·무리·보스의 HP·공격이 0보다 크다")
    void shippedDataLoads() throws Exception {
        String json;
        try (InputStream in = TowerMonsterCatalog.class.getResourceAsStream(TowerMonsterCatalog.RESOURCE)) {
            assertThat(in).isNotNull();
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(TowerMonsterCatalog.parse(json)).isEqualTo(TowerMonsterCatalog.monsters()); // 파일을 다시 읽어도 같은 값

        assertThat(TowerMonsterCatalog.monsters().byFloor().keySet()).containsExactlyInAnyOrder(1, 2);
        assertThat(TowerMonsterCatalog.floor(3)).isEmpty(); // 3층 이상은 다음 작업(M-0)
        assertThat(TowerMonsterCatalog.floor(0)).isEmpty();
        for (int floor = 1; floor <= 2; floor++) {
            FloorMonsters m = TowerMonsterCatalog.floor(floor).orElseThrow();
            assertThat(m.floor()).isEqualTo(floor);
            for (Role role : Role.values()) {
                Stats s = m.of(role);
                assertThat(s).as(floor + "층 " + role).isNotNull();
                assertThat(s.hp()).as(floor + "층 " + role + " hp").isPositive();
                assertThat(s.attack()).as(floor + "층 " + role + " attack").isPositive();
            }
        }
    }

    @Test
    @DisplayName("정한 숫자(임시): 1층 100/24 · 30/8 · 520/38, 2층 109/26 · 33/9 · 564/42")
    void shippedNumbers() {
        FloorMonsters f1 = TowerMonsterCatalog.floor(1).orElseThrow();
        assertThat(f1.normal()).isEqualTo(new Stats(100, 24));
        assertThat(f1.swarm()).isEqualTo(new Stats(30, 8));
        assertThat(f1.boss()).isEqualTo(new Stats(520, 38));
        assertThat(f1.of(Role.NORMAL)).isSameAs(f1.normal());
        assertThat(f1.of(Role.SWARM)).isSameAs(f1.swarm());
        assertThat(f1.of(Role.BOSS)).isSameAs(f1.boss());

        FloorMonsters f2 = TowerMonsterCatalog.floor(2).orElseThrow();
        assertThat(f2.normal()).isEqualTo(new Stats(109, 26));
        assertThat(f2.swarm()).isEqualTo(new Stats(33, 9));
        assertThat(f2.boss()).isEqualTo(new Stats(564, 42));
    }

    @Test
    @DisplayName("올바른 데이터는 읽힌다 (설명 필드 note는 있어도 된다)")
    void validData() {
        TowerMonsterCatalog.Monsters m = TowerMonsterCatalog.parse(
                "{\"provisional\": true, \"note\": \"임시\", \"source\": \"prototype\", \"floors\": [{\"floor\": 1, \"note\": \"x\", " + ROLES + "}]}");
        assertThat(m.floor(1).orElseThrow().boss()).isEqualTo(new Stats(520, 38));
        assertThat(m.floor(2)).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON·깨진 값은 조용히 넘기지 않고 실패한다")
    void invalidDataFails() {
        String[] bad = {
                "",                                                                                  // 빈 글
                "{\"floors\": [",                                                                    // 문법 오류
                "{\"floors\": [{\"floor\": 1, " + ROLES + "}]} trailing",                            // 뒤에 남는 글자
                "[]",                                                                                // 객체가 아님
                "{}",                                                                                // floors 없음
                "{\"floors\": []}",                                                                  // 빈 목록
                "{\"floors\": {}}",                                                                  // 목록이 아님
                floors("7"),                                                                         // 항목이 객체가 아님
                floors("{" + ROLES + "}"),                                                           // 층 번호 없음
                floors("{\"floor\": \"1\", " + ROLES + "}"),                                         // 층 번호가 글자
                floors("{\"floor\": 0, " + ROLES + "}"),                                             // 0층
                floors("{\"floor\": 2, " + ROLES + "}"),                                             // 1층부터 시작하지 않음
                floors("{\"floor\": 1, " + ROLES + "}", "{\"floor\": 1, " + ROLES + "}"),            // 같은 층 두 번
                floors("{\"floor\": 1, " + ROLES + "}", "{\"floor\": 3, " + ROLES + "}"),            // 2층을 건너뜀
                floors("{\"floor\": 1, \"normal\": {\"hp\": 100, \"attack\": 24}, \"swarm\": {\"hp\": 30, \"attack\": 8}}"), // 보스 없음
                floors("{\"floor\": 1, \"normal\": {\"hp\": 100, \"attack\": 24}, \"boss\": {\"hp\": 520, \"attack\": 38}}"), // 무리 없음
                floors("{\"floor\": 1, \"swarm\": {\"hp\": 30, \"attack\": 8}, \"boss\": {\"hp\": 520, \"attack\": 38}}"),    // 일반 없음
                floors("{\"floor\": 1, " + ROLES.replace("\"hp\": 100", "\"hp\": 0") + "}"),         // HP 0
                floors("{\"floor\": 1, " + ROLES.replace("\"hp\": 30", "\"hp\": -30") + "}"),        // HP 음수
                floors("{\"floor\": 1, " + ROLES.replace("\"attack\": 38", "\"attack\": 0") + "}"),  // 공격 0
                floors("{\"floor\": 1, " + ROLES.replace("\"attack\": 24", "\"attack\": 24.5") + "}"), // 정수가 아님
                floors("{\"floor\": 1, " + ROLES.replace("\"hp\": 520", "\"hp\": \"520\"") + "}"),   // 숫자가 글자
                floors("{\"floor\": 1, " + ROLES.replace("\"hp\": 520", "\"hp\": null") + "}"),      // null
                floors("{\"floor\": 1, " + ROLES.replace("\"hp\": 520, ", "") + "}"),                // HP 없음
                floors("{\"floor\": 1, " + ROLES.replace("\"attack\": 8", "\"atk\": 8") + "}"),      // 필드 이름 오타
                floors("{\"floor\": 1, " + ROLES.replace("\"hp\": 520", "\"hp\": 520, \"hp\": 1") + "}"), // 같은 이름 두 번
                floors("{\"floor\": 1, \"elite\": {\"hp\": 1, \"attack\": 1}, " + ROLES + "}"),      // 모르는 종류
                floors("{\"floor\": 1, \"recommendedBp\": 100, " + ROLES + "}"),                     // 전투력 숫자는 받지 않는다
                floors("{\"floor\": 1, " + ROLES.replace("\"hp\": 520", "\"hp\": 520, \"battlePower\": 100") + "}"),
                "{\"bp\": 100, \"floors\": [{\"floor\": 1, " + ROLES + "}]}",
                floors("{\"floor\": 1, \"normal\": [100, 24], \"swarm\": {\"hp\": 30, \"attack\": 8}, \"boss\": {\"hp\": 520, \"attack\": 38}}") // 객체가 아님
        };
        for (String json : bad) {
            assertThatThrownBy(() -> TowerMonsterCatalog.parse(json)).as(json).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("공개 층 상한을 넘는 층은 받지 않는다")
    void floorBeyondOpenCapFails() {
        StringBuilder entries = new StringBuilder();
        int cap = TowerFloorCatalog.openFloorCap();
        for (int floor = 1; floor <= cap + 1; floor++) {
            if (floor > 1) entries.append(',');
            entries.append("{\"floor\": ").append(floor).append(", ").append(ROLES).append('}');
        }
        assertThatThrownBy(() -> TowerMonsterCatalog.parse("{\"floors\": [" + entries + "]}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("상한");
    }
}
