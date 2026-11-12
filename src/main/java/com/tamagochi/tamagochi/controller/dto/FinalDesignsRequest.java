package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 진화 디자인 후보 요청: v5 완성작(/final-designs)과 v6 스케치(/sketch-designs)가 함께 쓴다 */
@Getter
@NoArgsConstructor
public class FinalDesignsRequest {
    private String tossUserId;
    /** 처음 그린 낙서 (data: 접두사 포함/불포함 모두 허용) */
    private String imageBase64;
    /** 지금 모습: 완성작 후보면 스케치, 스케치 후보면 낙서 단계 그림 */
    private String referenceImageBase64;
    /** 진영 (스케치 후보에서만 쓴다) */
    private String faction;
    /** true면 저장된 후보를 버리고 새로 만든다 */
    private boolean refresh;
}
