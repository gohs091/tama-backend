package com.tamagochi.tamagochi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImageStatusResponse {
    private final String status;
    private final String imageDataUrl;
    private final String error;
    private final String species; // 손그림의 정체(한글), 유체 생성 결과에만 있다
}
