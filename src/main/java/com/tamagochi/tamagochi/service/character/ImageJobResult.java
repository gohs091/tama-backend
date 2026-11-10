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
    private final String facing;  // 만들어진 그림이 바라보는 쪽: left | right | front. 알 수 없으면 null
    private final boolean solid;  // true면 몸 안 투명 틈은 진짜 배경이다 (앱이 흰색으로 메우지 않는다). v5 스케치·완성작

    public static ImageJobResult pending() { return new ImageJobResult("PENDING", null, null, null, null, false); }
    public static ImageJobResult done(String url) { return new ImageJobResult("DONE", url, null, null, null, false); }
    public static ImageJobResult done(String url, String species) { return new ImageJobResult("DONE", url, null, species, null, false); }
    public static ImageJobResult done(String url, String species, String facing) { return new ImageJobResult("DONE", url, null, species, facing, false); }
    public static ImageJobResult done(String url, String species, String facing, boolean solid) { return new ImageJobResult("DONE", url, null, species, facing, solid); }
    public static ImageJobResult failed(String err) { return new ImageJobResult("FAILED", null, err, null, null, false); }
}
