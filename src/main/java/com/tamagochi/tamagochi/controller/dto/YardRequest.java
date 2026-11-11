package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 마당 요청. 동작마다 필요한 칸만 채운다 (소품 사기: propId, 놓기: slot+propId, 배경: background) */
@Getter
@NoArgsConstructor
public class YardRequest {
    private String tossUserId;
    private String propId;
    private String slot;
    private String background;
    private Integer x;   // 방 가구 자리 (방 폭·높이의 천분율)
    private Integer y;
}
