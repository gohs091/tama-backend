package com.tamagochi.tamagochi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LocalCompositeResponse {
    private final String imageDataUrl; // data:image/png;base64,...
    private final String error;

    public static LocalCompositeResponse ok(String dataUrl) { return new LocalCompositeResponse(dataUrl, null); }
    public static LocalCompositeResponse fail(String err)   { return new LocalCompositeResponse(null, err); }
}
