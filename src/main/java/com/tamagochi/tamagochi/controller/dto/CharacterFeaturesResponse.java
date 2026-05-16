package com.tamagochi.tamagochi.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CharacterFeaturesResponse {
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String bodyShape;
    private String eyeStyle;
    private boolean hasTail;
    private boolean hasHorns;
    private boolean hasWings;
    private boolean hasEars;
    private String personality;
}
