package com.tamagochi.tamagochi.service.character;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 손그림이 무엇을 그린 것인지 (이미지 생성 프롬프트에 명시하기 위한 분석 결과).
 * traits: 이 그림만의 두드러진 특징 2~3개, bodyPlan: 몸 구조(biped, quadruped, winged ...). 진화 단계에서도 이어서 쓴다.
 * kind=tool(칼·방패 같은 도구)이면 attach: 도구가 귀여운 생물의 어느 몸 부위(ears, shell, hands, wings)가 될지.
 * 기차·자동차처럼 완성된 물체(object)는 attach 없이 하나의 캐릭터로 만든다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DoodleSubject(String subject, String kind, String parts, String subjectKo, String traits, String bodyPlan, String attach) {

    public DoodleSubject(String subject, String kind, String parts, String subjectKo, String traits, String bodyPlan) {
        this(subject, kind, parts, subjectKo, traits, bodyPlan, null);
    }

    public DoodleSubject(String subject, String kind, String parts, String subjectKo) {
        this(subject, kind, parts, subjectKo, null, null, null);
    }

    public DoodleSubject(String subject, String kind, String parts) {
        this(subject, kind, parts, null, null, null, null);
    }

    /** 칼·방패 같은 도구: 도구 자체가 아니라 생물의 몸 한 부분이 되어 캐릭터가 만들어진다 */
    public boolean isTool() {
        return "tool".equalsIgnoreCase(kind);
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
