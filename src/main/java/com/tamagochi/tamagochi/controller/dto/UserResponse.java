package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.user.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String tossUserId;
    private String nickname;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .tossUserId(user.getTossUserId())
                .nickname(user.getNickname())
                .build();
    }
}