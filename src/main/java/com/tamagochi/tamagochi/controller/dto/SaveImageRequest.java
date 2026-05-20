package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SaveImageRequest {
    private String tossUserId;
    /** baby | child | adult */
    private String stage;
    private String imageBase64;
}
