package com.tamagochi.tamagochi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImageStatusResponse {
    private final String status;
    private final String imageDataUrl;
    private final String error;
}
