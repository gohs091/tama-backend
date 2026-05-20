package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LocalCompositeRequest {
    /** 유저 손그림 base64 (data: 접두사 포함/불포함 모두 허용) */
    private String imageBase64;
    private String faction;
}
