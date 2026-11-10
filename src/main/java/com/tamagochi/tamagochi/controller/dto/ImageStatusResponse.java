package com.tamagochi.tamagochi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImageStatusResponse {
    private final String status;
    private final String imageDataUrl;
    private final String error;
    private final String species; // 손그림의 정체(한글), 낙서 생성 결과에만 있다
    private final String facing;  // 그림이 바라보는 쪽: left | right | front (알 수 없으면 null)
    private final boolean solid;  // true면 몸 안 투명 틈은 진짜 배경 (앱이 흰색으로 메우지 않는다)
}
