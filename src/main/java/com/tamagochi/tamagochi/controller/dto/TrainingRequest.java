package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Getter
@NoArgsConstructor
public class TrainingRequest {
    private String tossUserId;
    private String sessionId; // POST /training/start 로 받은 트레이닝 세션
    private int clickCount;
    private String idempotencyKey;
}