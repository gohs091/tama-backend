package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FeedRequest {
    private String tossUserId;
    private String idempotencyKey;
}