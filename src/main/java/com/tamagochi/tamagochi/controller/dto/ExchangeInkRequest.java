package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExchangeInkRequest {
    private String tossUserId;
    private String packId;
    /** 앱이 누를 때마다 새로 만드는 값. 같은 값이 다시 오면 한 번만 바꾼다 */
    private String requestId;
}
