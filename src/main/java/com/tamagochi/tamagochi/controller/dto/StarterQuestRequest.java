package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 첫걸음 퀘스트 요청 (보상 받기·앱이 끝냈다고 알리기). 퀘스트는 주소에 적는다 */
@Getter
@NoArgsConstructor
public class StarterQuestRequest {
    private String tossUserId;
}
