package com.tamagochi.tamagochi.service.character;

import com.tamagochi.tamagochi.domain.character.DoodleOrigin;
import com.tamagochi.tamagochi.repository.character.DoodleOriginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DoodleOriginServiceTest {

    private DoodleOriginRepository repository;
    private DoodleOriginService service;

    @BeforeEach
    void setUp() {
        repository = mock(DoodleOriginRepository.class);
        service = new DoodleOriginService(repository);
    }

    @Test
    @DisplayName("처음 저장: 분석 결과(특징·몸 구조 포함)를 그대로 저장한다")
    void save_new() {
        when(repository.findByTossUserId("u1")).thenReturn(Optional.empty());

        service.save("u1", new DoodleSubject("a crow", "animal", "beak, wing", "새", "sharp beak", "winged"));

        ArgumentCaptor<DoodleOrigin> cap = ArgumentCaptor.forClass(DoodleOrigin.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getTossUserId()).isEqualTo("u1");
        assertThat(cap.getValue().getSubject()).isEqualTo("a crow");
        assertThat(cap.getValue().getTraits()).isEqualTo("sharp beak");
        assertThat(cap.getValue().getBodyPlan()).isEqualTo("winged");
    }

    @Test
    @DisplayName("다시 그리면 같은 유저의 기록을 새 분석으로 덮어쓴다")
    void save_overwrites() {
        DoodleOrigin existing = new DoodleOrigin("u1");
        existing.update("a cat", "animal", "고양이", "ears", "big ears", "quadruped");
        when(repository.findByTossUserId("u1")).thenReturn(Optional.of(existing));

        service.save("u1", new DoodleSubject("a crow", "animal", "beak", "새", "sharp beak", "winged"));

        verify(repository).save(existing);
        assertThat(existing.getSubject()).isEqualTo("a crow");
    }

    @Test
    @DisplayName("조회: 저장된 값을 분석 결과로 돌려준다 / 없으면 비어 있다")
    void find() {
        DoodleOrigin existing = new DoodleOrigin("u1");
        existing.update("a crow", "animal", "새", "beak", "sharp beak", "winged");
        when(repository.findByTossUserId("u1")).thenReturn(Optional.of(existing));
        when(repository.findByTossUserId("u2")).thenReturn(Optional.empty());

        assertThat(service.find("u1")).hasValueSatisfying(s -> {
            assertThat(s.subject()).isEqualTo("a crow");
            assertThat(s.traits()).isEqualTo("sharp beak");
            assertThat(s.species()).isEqualTo("새");
        });
        assertThat(service.find("u2")).isEmpty();
        assertThat(service.find(" ")).isEmpty();
    }

    @Test
    @DisplayName("저장소 오류가 나도 예외를 밖으로 던지지 않는다 (이미지 생성을 막지 않는다)")
    void errors_swallowed() {
        when(repository.findByTossUserId(any())).thenThrow(new RuntimeException("db down"));

        assertThat(service.find("u1")).isEmpty();
        service.save("u1", new DoodleSubject("a crow", "animal", null));
    }

    @Test
    @DisplayName("값이 너무 길면 잘라서 저장한다")
    void longValuesAreCut() {
        DoodleOrigin o = new DoodleOrigin("u1");
        o.update("x".repeat(500), "animal", null, "p".repeat(900), "t".repeat(900), "biped");

        assertThat(o.getSubject()).hasSize(200);
        assertThat(o.getParts()).hasSize(600);
        assertThat(o.getTraits()).hasSize(600);
    }
}
