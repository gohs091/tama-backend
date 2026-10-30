package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SleepImageRequest {
    private String tossUserId;
    /** 지금 단계의 캐릭터 이미지 (data: 접두사 포함/불포함 모두 허용) */
    private String imageBase64;
    private String faction;
    /** baby | child | adult — 서버가 가진 지금 단계와 같아야 한다 */
    private String stage;
    /** 같은 요청이 두 번 결제되지 않게 하는 키 (요청마다 새로 만든다) */
    private String idempotencyKey;
}
