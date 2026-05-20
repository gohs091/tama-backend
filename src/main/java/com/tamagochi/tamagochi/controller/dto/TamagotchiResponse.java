package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TamagotchiResponse {
    private Long id;
    private String name;
    private String evolutionStage;
    private String faction;
    private int hunger;
    private int strength;
    private int poopCount;
    private boolean sleeping;
    private boolean dead;
    private int feedCount;
    private LocalDateTime lastFedAt;
    private String currentImageBase64;

    public static TamagotchiResponse from(Tamagotchi tamagotchi) {
        return TamagotchiResponse.builder()
                .id(tamagotchi.getId())
                .name(tamagotchi.getName())
                .evolutionStage(tamagotchi.getEvolutionStage().name())
                .faction(tamagotchi.getFaction())
                .hunger(tamagotchi.getHunger())
                .strength(tamagotchi.getStrength())
                .poopCount(tamagotchi.getPoopCount())
                .sleeping(tamagotchi.isSleeping())
                .dead(tamagotchi.isDead())
                .feedCount(tamagotchi.getFeedCount())
                .lastFedAt(tamagotchi.getLastFedAt())
                .currentImageBase64(tamagotchi.getCurrentImageBase64())
                .build();
    }
}
