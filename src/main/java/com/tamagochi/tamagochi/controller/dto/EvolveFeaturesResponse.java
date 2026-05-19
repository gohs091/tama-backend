package com.tamagochi.tamagochi.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvolveFeaturesResponse {
    private CharacterFeaturesResponse baby;
    private CharacterFeaturesResponse child;
    private CharacterFeaturesResponse adult;
}
