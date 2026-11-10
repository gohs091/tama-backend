package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 농장 요청. 동작마다 필요한 칸만 채운다 (심기: plot+crop, 거두기: plot 비우면 전부, 열기·비우기: plot, 퀘스트: quest) */
@Getter
@NoArgsConstructor
public class FarmRequest {
    private String tossUserId;
    private Integer plot;
    private String crop;
    private String quest;
}
