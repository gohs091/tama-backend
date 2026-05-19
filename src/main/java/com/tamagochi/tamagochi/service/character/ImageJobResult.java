package com.tamagochi.tamagochi.service.character;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImageJobResult {
    private final String status; // PENDING, DONE, FAILED
    private final String imageDataUrl; // data:image/jpeg;base64,...
    private final String error;

    public static ImageJobResult pending() { return new ImageJobResult("PENDING", null, null); }
    public static ImageJobResult done(String url) { return new ImageJobResult("DONE", url, null); }
    public static ImageJobResult failed(String err) { return new ImageJobResult("FAILED", null, err); }
}
