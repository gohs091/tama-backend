package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 탑 요청. floor는 출발할 때만 쓴다 (비우면 돌파한 층 바로 위) */
@Getter
@NoArgsConstructor
public class TowerRequest {
    private String tossUserId;
    private Integer floor;
}
