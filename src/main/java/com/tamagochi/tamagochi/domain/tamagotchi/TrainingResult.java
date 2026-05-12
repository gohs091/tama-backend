package com.tamagochi.tamagochi.domain.tamagotchi;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrainingResult {
    private int clickCount;
    private int reward;
}