package com.tamagochi.tamagochi.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {
    private String message;

    /** 앱이 이유를 구분할 때 쓰는 코드 (CodedException). 없으면 응답에 싣지 않는다 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;

    public static ErrorResponse of(String message) {
        return ErrorResponse.builder()
                .message(message)
                .build();
    }

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .build();
    }
}
