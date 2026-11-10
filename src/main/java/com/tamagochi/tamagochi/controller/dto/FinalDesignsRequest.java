package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** v5 완성작 디자인 후보 요청 */
@Getter
@NoArgsConstructor
public class FinalDesignsRequest {
    private String tossUserId;
    /** 처음 그린 낙서 (data: 접두사 포함/불포함 모두 허용) */
    private String imageBase64;
    /** 지금 스케치 모습 */
    private String referenceImageBase64;
    /** true면 저장된 후보를 버리고 새로 만든다 */
    private boolean refresh;
}
