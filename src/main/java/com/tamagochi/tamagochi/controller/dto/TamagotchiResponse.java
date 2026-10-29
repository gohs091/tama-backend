package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionRules;
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
    private boolean bathing;        // 잉크통 충전 중 (돌보기·훈련 불가)
    private boolean adventuring;    // 끄적랜드 모험 중 (돌보기·훈련 불가)
    private boolean dead;
    private int feedCount;
    private int evoGauge;
    private int evoGaugeMax;       // 0이면 진화할 수 없는 단계(알·완성작)
    private int pendingEvoGain;      // 마지막 훈련으로 받을 수 있는 진화 게이지 (광고를 보면 받는다)
    private boolean evolutionReady; // 게이지가 가득 차고 컨디션 조건을 만족해 지금 진화할 수 있음
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
                .bathing(tamagotchi.isBathing())
                .adventuring(tamagotchi.isAdventuring())
                .dead(tamagotchi.isDead())
                .feedCount(tamagotchi.getFeedCount())
                .evoGauge(tamagotchi.getEvoGauge())
                .evoGaugeMax(EvolutionRules.gaugeMax(tamagotchi.getEvolutionStage()))
                .pendingEvoGain(tamagotchi.getPendingEvoGain())
                .evolutionReady(tamagotchi.isEvolutionReady())
                .lastFedAt(tamagotchi.getLastFedAt())
                .currentImageBase64(tamagotchi.getCurrentImageBase64())
                .build();
    }
}
