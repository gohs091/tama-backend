package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PurchasePointRequest {
    private String tossUserId;
    private String orderId;
    private String sku;
    /** 붉은 잉크를 아는 앱이면 true. 예전 앱은 이 값을 보내지 않는다(null) */
    private Boolean redInk;
}
