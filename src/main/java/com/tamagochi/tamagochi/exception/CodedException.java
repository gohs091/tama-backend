package com.tamagochi.tamagochi.exception;

import lombok.Getter;

/**
 * 앱이 이유를 구분해야 하는 거절 (400). 응답에 message와 함께 code가 실린다 (예: NOT_ENOUGH_FRUIT, BAG_FULL).
 * IllegalStateException이라 code를 모르는 화면에는 지금까지처럼 message만 보인다.
 */
@Getter
public class CodedException extends IllegalStateException {

    private final String code;

    public CodedException(String code, String message) {
        super(message);
        this.code = code;
    }
}
