package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoodleSubjectSpeciesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private DoodleSubject parse(String json) throws Exception {
        return mapper.readValue(json, DoodleSubject.class);
    }

    @Test
    @DisplayName("분석 응답의 한글 이름이 종족이 된다")
    void speciesFromKoreanName() throws Exception {
        DoodleSubject s = parse("{\"subject\":\"a bow\",\"kind\":\"object\",\"parts\":\"\",\"subjectKo\":\" 활 \"}");
        assertThat(s.species()).isEqualTo("활");
    }

    @Test
    @DisplayName("한글 이름이 없는 응답(옛 형식)도 읽히고 종족은 없다")
    void oldFormatHasNoSpecies() throws Exception {
        DoodleSubject s = parse("{\"subject\":\"a sword\",\"kind\":\"object\",\"parts\":\"a blade\"}");
        assertThat(s.species()).isNull();
    }

    @Test
    @DisplayName("무엇인지 알 수 없는 그림은 종족이 없다")
    void abstractHasNoSpecies() throws Exception {
        DoodleSubject s = parse("{\"subject\":\"an abstract shape\",\"kind\":\"abstract\",\"parts\":\"\",\"subjectKo\":\"도형\"}");
        assertThat(s.species()).isNull();
    }

    @Test
    @DisplayName("진영 17개 모두 고유한 테마와 색이 있다")
    void everyFactionHasTheme() {
        String[] factions = {"nature-spirits", "deep-savers", "nightmare-soldiers", "wind-guardians", "metal-empire",
                "virus-busters", "dragons-roar", "jungle-troopers", "dark-area", "trash",
                "space", "desert", "snowfield", "volcano", "candy-land", "ninja", "magic-school"};
        ImageGenerationService service = new ImageGenerationService(mapper);
        java.util.Set<String> themes = new java.util.HashSet<>();
        for (String f : factions) {
            String theme = service.buildThemeText(f, "adult");
            assertThat(theme).doesNotContain("a cute, neutral look");
            themes.add(theme);
        }
        assertThat(themes).hasSize(factions.length);
    }
}
