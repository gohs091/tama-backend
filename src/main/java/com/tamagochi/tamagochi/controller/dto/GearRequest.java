package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장비 요청. id = 무기 id (끼기에서 비우거나 null이면 벗어서 맨손) */
@Getter
@NoArgsConstructor
public class GearRequest {
    private String tossUserId;
    private String id;
}
