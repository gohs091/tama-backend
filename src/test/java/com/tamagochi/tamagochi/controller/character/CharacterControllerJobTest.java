package com.tamagochi.tamagochi.controller.character;

import com.tamagochi.tamagochi.controller.dto.EvolveImageRequest;
import com.tamagochi.tamagochi.controller.dto.GenerateImageRequest;
import com.tamagochi.tamagochi.controller.dto.GenerateImageResponse;
import com.tamagochi.tamagochi.service.character.CharacterService;
import com.tamagochi.tamagochi.service.character.ImageGenerationService;
import com.tamagochi.tamagochi.service.character.ImageJobResult;
import com.tamagochi.tamagochi.service.character.ImageQuotaService;
import com.tamagochi.tamagochi.service.character.LocalImageCompositeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CharacterControllerJobTest {

    private ImageGenerationService imageService;
    private ImageQuotaService quota;
    private CharacterController controller;

    @BeforeEach
    void setUp() {
        imageService = mock(ImageGenerationService.class);
        quota = mock(ImageQuotaService.class);
        when(quota.tryAcquire(anyString())).thenReturn(true);
        controller = new CharacterController(mock(CharacterService.class), imageService,
                mock(LocalImageCompositeService.class), quota);
    }

    private GenerateImageRequest generateRequest() {
        GenerateImageRequest r = new GenerateImageRequest();
        ReflectionTestUtils.setField(r, "tossUserId", "u1");
        ReflectionTestUtils.setField(r, "imageBase64", "AAAA");
        ReflectionTestUtils.setField(r, "faction", "dragons-roar");
        ReflectionTestUtils.setField(r, "variationSeed", 42L);
        return r;
    }

    @Test
    @DisplayName("유체 생성: 비동기 작업을 넘기기 전에 PENDING을 먼저 저장한다 (큐 대기 중 'Job not found' 방지)")
    void generate_storesPendingBeforeAsync() {
        ResponseEntity<GenerateImageResponse> res = controller.generate(generateRequest());

        ArgumentCaptor<String> jobId = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(imageService);
        order.verify(imageService).storeJob(jobId.capture(), any(ImageJobResult.class));
        order.verify(imageService).generateAsync(eq(jobId.getValue()), eq("AAAA"), eq("dragons-roar"), eq(42L));
        assertThat(res.getBody().getJobId()).isEqualTo(jobId.getValue());
    }

    @Test
    @DisplayName("진화 생성: 비동기 작업을 넘기기 전에 PENDING을 먼저 저장한다")
    void generateEvolved_storesPendingBeforeAsync() {
        EvolveImageRequest r = new EvolveImageRequest();
        ReflectionTestUtils.setField(r, "tossUserId", "u1");
        ReflectionTestUtils.setField(r, "imageBase64", "AAAA");
        ReflectionTestUtils.setField(r, "faction", "metal-empire");
        ReflectionTestUtils.setField(r, "stage", "child");

        controller.generateEvolved(r);

        InOrder order = inOrder(imageService);
        order.verify(imageService).storeJob(anyString(), any(ImageJobResult.class));
        order.verify(imageService).generateEvolvedAsync(anyString(), any(), eq("AAAA"), eq("metal-empire"), eq("child"), any());
    }

    @Test
    @DisplayName("한도 초과면 429이고 작업을 만들지 않는다")
    void quotaExceeded_noJobCreated() {
        when(quota.tryAcquire("u1")).thenReturn(false);

        ResponseEntity<GenerateImageResponse> res = controller.generate(generateRequest());

        assertThat(res.getStatusCode().value()).isEqualTo(429);
        verify(imageService, never()).storeJob(anyString(), any());
        verify(imageService, never()).generateAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("유저 식별자가 없으면 400")
    void missingUser_badRequest() {
        GenerateImageRequest r = generateRequest();
        ReflectionTestUtils.setField(r, "tossUserId", " ");
        assertThat(controller.generate(r).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(imageService);
    }
}
