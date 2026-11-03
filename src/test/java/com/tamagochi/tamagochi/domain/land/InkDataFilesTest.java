package com.tamagochi.tamagochi.domain.land;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** resources/inks/ 잉크 파일 검사: 새 잉크를 추가할 때 빠뜨린 칸을 여기서 잡는다 */
class InkDataFilesTest {

    private static List<LandCatalog.Ink> allInks() {
        // 계열 28 + 잉크 394 (계열 잉크 id는 family.<key>)
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("beast", "bird", "insect", "spirit", "fish", "crustacean", "tentacle", "marine",
                                        "plant", "flower", "reptile", "primate", "clockwork", "electric", "steel", "engine",
                                        "ghost", "wind", "heraldry", "scale", "shadow", "junk", "cosmic", "ice", "fire", "sweet", "mask", "stardust")
                                .map(k -> LandCatalog.ink("family." + k).orElseThrow()),
                        com.tamagochi.tamagochi.domain.spar.SparRoster.all().stream()
                                .flatMap(e -> e.drops().stream()).distinct().map(id -> LandCatalog.ink(id).orElseThrow()))
                .distinct().toList();
    }

    @Test
    @DisplayName("계열 28개, 구역 17곳이 읽히고, 스파링 상대가 떨어뜨리는 잉크는 모두 목록에 있다")
    void loads() {
        assertThat(LandCatalog.regions()).hasSize(17);
        assertThat(allInks()).hasSizeGreaterThanOrEqualTo(28);
        assertThat(LandCatalog.family("stardust")).isPresent();
    }

    @Test
    @DisplayName("희귀·전설 잉크는 모두 조금 바뀌는 단계용 부위 문구가 있다")
    void rareAndLegendHavePart() {
        List<String> missing = allInks().stream()
                .filter(i -> i.grade() == LandCatalog.Grade.RARE || i.grade() == LandCatalog.Grade.LEGEND)
                .filter(i -> i.part() == null).map(LandCatalog.Ink::id).toList();
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("체형은 희귀·전설에만 있고, 일반·계열 잉크는 체형을 바꾸지 않는다")
    void bodyOnlyOnRareOrLegend() {
        assertThat(allInks()).filteredOn(i -> i.body() != null)
                .allMatch(i -> i.grade() == LandCatalog.Grade.RARE || i.grade() == LandCatalog.Grade.LEGEND);
        assertThat(LandCatalog.ink("megalodon").orElseThrow().body()).isEqualTo(LandCatalog.Body.LEGLESS);
        assertThat(LandCatalog.ink("bonghwang").orElseThrow().body()).isEqualTo(LandCatalog.Body.FLY);
    }

    @Test
    @DisplayName("부위 문구는 조금 바뀌는 단계에, 체형은 크게 바뀌는 단계에만 들어간다")
    void partAndBodyUsedByStage() {
        String small = LandCatalog.absorbedPrompt("swordfish", List.of("family.fish"), "child");
        assertThat(small).contains("a long sharp swordfish bill").doesNotContain(LandCatalog.BODY_CHANGE_MARK);
        String full = LandCatalog.absorbedPrompt("swordfish", List.of("family.fish", "family.marine"), "adult");
        assertThat(full).contains(LandCatalog.BODY_CHANGE_MARK).contains("LEGLESS");
    }
}
