package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 손그림이 무엇을 그린 것인지 (이미지 생성 프롬프트에 명시하기 위한 분석 결과) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DoodleSubject(String subject, String kind, String parts, String subjectKo) {

    public DoodleSubject(String subject, String kind, String parts) {
        this(subject, kind, parts, null);
    }

    /** 앱에 보여줄 종족 이름. 알아보지 못했으면 null (앱이 "정체불명"으로 표시) */
    public String species() {
        if (isAbstract() || subjectKo == null || subjectKo.isBlank()) return null;
        return subjectKo.trim();
    }

    public boolean isAbstract() {
        return kind == null || kind.isBlank() || "abstract".equalsIgnoreCase(kind);
    }
}
