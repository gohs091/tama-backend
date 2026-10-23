package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 손그림이 무엇을 그린 것인지 (이미지 생성 프롬프트에 명시하기 위한 분석 결과) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DoodleSubject(String subject, String kind, String parts) {

    public boolean isAbstract() {
        return kind == null || kind.isBlank() || "abstract".equalsIgnoreCase(kind);
    }
}
