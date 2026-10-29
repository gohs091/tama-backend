package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EarnPointRequest {
    private String tossUserId;
    private int amount;
    private String idempotencyKey;
    /** 앱이 잰 광고 시청 시간(ms). 모르면 null */
    private Long watchMs;
}