package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GenerateImageRequest {
    private String tossUserId;
    private String imageBase64;
    private String faction;
    /** 그림과 팩션이 합쳐지는 방식을 정하는 시드 (그림을 새로 저장할 때 클라이언트가 한 번 만든다) */
    private Long variationSeed;
}
