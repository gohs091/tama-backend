package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알 깨우기 요청. redInk: 붉은 잉크로 깨우는 것을 아는 앱이 true 로 보낸다(예전 앱은 보내지 않는다) */
@Getter
@NoArgsConstructor
public class HatchRequest {
    private String tossUserId;
    private String idempotencyKey;
    private Boolean redInk;
}
