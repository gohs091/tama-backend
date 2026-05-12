package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EarnPointRequest {
    private String tossUserId;
    private int amount;
    private String idempotencyKey;
}