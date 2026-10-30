package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TrainingStartRequest {
    private String tossUserId;
    private String rematchSessionId; // 설욕전: 이 판의 상대와 다시 붙는다 (없으면 새 상대)
}
