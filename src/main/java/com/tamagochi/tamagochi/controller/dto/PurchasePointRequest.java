package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PurchasePointRequest {
    private String tossUserId;
    private String orderId;
    private String sku;
}
