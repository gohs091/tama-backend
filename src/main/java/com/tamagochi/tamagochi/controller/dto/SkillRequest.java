package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 배틀 기술 정하기 요청. imageBase64: 앱이 가진 지금 단계 그림(data URL 가능). 없으면 서버에 올라간 그림을 쓴다 */
@Getter
@NoArgsConstructor
public class SkillRequest {
    private String tossUserId;
    private String imageBase64;
}
