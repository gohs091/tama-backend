package com.tamagochi.tamagochi.service.character;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImageJobResult {
    private final String status; // PENDING, DONE, FAILED
    private final String imageDataUrl; // data:image/jpeg;base64,...
    private final String error;
    private final String species; // 손그림의 정체(한글). 낙서를 만들 때만 채워지고, 알아보지 못했으면 null

    public static ImageJobResult pending() { return new ImageJobResult("PENDING", null, null, null); }
    public static ImageJobResult done(String url) { return new ImageJobResult("DONE", url, null, null); }
    public static ImageJobResult done(String url, String species) { return new ImageJobResult("DONE", url, null, species); }
    public static ImageJobResult failed(String err) { return new ImageJobResult("FAILED", null, err, null); }
}
