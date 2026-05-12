package com.tamagochi.tamagochi.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {
    private String message;

    public static ErrorResponse of(String message) {
        return ErrorResponse.builder()
                .message(message)
                .build();
    }
}
