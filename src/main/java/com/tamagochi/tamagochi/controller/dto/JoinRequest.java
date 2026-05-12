package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinRequest {
    private String tossUserId;
    private String nickname;
}