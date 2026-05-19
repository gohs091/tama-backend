package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GenerateImageRequest {
    private String tossUserId;
    private String imageBase64;
    private String faction;
}
