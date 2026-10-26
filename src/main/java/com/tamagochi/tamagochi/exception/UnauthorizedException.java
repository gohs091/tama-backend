package com.tamagochi.tamagochi.exception;

/** 로그인이 안 됐거나 토큰이 없거나 만료됐다 (401) */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
