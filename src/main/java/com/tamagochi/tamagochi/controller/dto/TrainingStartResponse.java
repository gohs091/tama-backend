package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrainingStartResponse {
    private String sessionId;
    private long minSeconds; // 이 시간이 지나기 전에는 결과를 낼 수 없다

    public static TrainingStartResponse of(TrainingSession session) {
        return TrainingStartResponse.builder()
                .sessionId(session.getId())
                .minSeconds(TrainingSession.MIN_ELAPSED.toSeconds())
                .build();
    }
}
