package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EvolveImageRequest {
    /** S3에 저장된 유체 이미지 URL (imageBase64가 없을 때 사용) */
    private String imageUrl;
    /** 직접 base64로 전달할 때 사용 (data: 접두사 포함/불포함 모두 허용) */
    private String imageBase64;
    private String faction;
    /** baby | child | adult */
    private String stage;
}
