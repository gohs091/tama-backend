package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class InkLoadoutRequest {
    private String tossUserId;
    private String mainInkId;          // 특정 잉크 (없으면 비움)
    private List<String> familyInkIds; // 계열 잉크 최대 2개 (없으면 비움)
}
