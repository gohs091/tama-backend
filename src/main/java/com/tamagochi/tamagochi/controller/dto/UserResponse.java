package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.user.NicknameRules;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String tossUserId;
    private String nickname;
    /** 바뀌지 않는 유저 코드, 화면용 4-4 형식 (예: K7Q2-9XMA) */
    private String userCode;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .tossUserId(user.getTossUserId())
                .nickname(user.getNickname())
                .userCode(NicknameRules.formatCode(user.getUserCode()))
                .build();
    }
}
