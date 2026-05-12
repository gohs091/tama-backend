package com.tamagochi.tamagochi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException e) {
        log.warn("IllegalStateException: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of("서버 오류가 발생했습니다."));
    }
}