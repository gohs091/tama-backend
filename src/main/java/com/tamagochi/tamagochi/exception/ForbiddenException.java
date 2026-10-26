package com.tamagochi.tamagochi.exception;

/** 로그인은 됐지만 다른 유저의 데이터를 쓰려 했다 (403) */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
