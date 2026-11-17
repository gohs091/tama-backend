package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 도시락 요청. id = 도시락 종류, count = 개수 (비우면 1. 먹기는 항상 1개) */
@Getter
@NoArgsConstructor
public class LunchRequest {
    private String tossUserId;
    private String id;
    private Integer count;
}
