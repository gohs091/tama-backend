package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoodleFacingTest {

    @Test
    void 방향_답을_읽는다() {
        assertThat(DoodleSubjectService.parseFacing("left")).contains("left");
        assertThat(DoodleSubjectService.parseFacing(" Right.")).contains("right");
        assertThat(DoodleSubjectService.parseFacing("FRONT")).contains("front");
    }

    @Test
    void 알_수_없는_답은_비운다() {
        assertThat(DoodleSubjectService.parseFacing("up")).isEmpty();
        assertThat(DoodleSubjectService.parseFacing("")).isEmpty();
        assertThat(DoodleSubjectService.parseFacing(null)).isEmpty();
    }
}
