package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrainingStartResponse {
    private String sessionId;
    private long minSeconds; // 이 시간이 지나기 전에는 결과를 낼 수 없다
    private Enemy enemy;     // 스파링 상대 (탭 수는 화면에서 상대가 치는 모습을 보여주는 데 쓴다)

    public record Enemy(String key, int level, int might, int taps, int condition) {
    }

    public static TrainingStartResponse of(TrainingSession session) {
        return TrainingStartResponse.builder()
                .sessionId(session.getId())
                .minSeconds(TrainingSession.MIN_ELAPSED.toSeconds())
                .enemy(new Enemy(session.getEnemyKey(), session.getEnemyLevel(), session.getEnemyMight(),
                        session.getEnemyTaps(), session.getEnemyCondition()))
                .build();
    }
}
