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
    private boolean sleepImageUnlocked; // 자는 모습 이미지 값을 냈다 (이후 단계는 무료)
    private java.util.List<String> sleepImageStages; // 자는 모습을 이미 만든 단계 (BABY | CHILD | ADULT)
    private boolean sleepy;         // 졸린 시간대인데 깨어 있다 (불을 끄면 잠든다)
    private int sleepyStartHour;    // 졸린 시간대 시작 시각(0~23시, KST). 여기서부터 8시간 졸리다
    private boolean bathing;       // 잉크통 충전 중 (돌보기·훈련 불가)
    private boolean adventuring;    // 끄적랜드 모험 중 (돌보기·훈련 불가)
    private boolean dead;
    private int feedCount;
    private int evoGauge;          // 업데이트 전 앱용: 진화 가능 구간까지의 진행도를 예전 게이지 칸 수로 바꾼 값
    private int evoGaugeMax;       // 0이면 진화할 수 없는 단계(알·완성작)
    private int pendingEvoGain;    // 업데이트 전 앱용: 받을 수 있는 경험치를 예전 게이지 칸 수로 바꾼 값
    private boolean evolutionReady; // 진화 가능 구간이고 컨디션 조건을 만족해 지금 진화할 수 있음
    private int level;
    private int exp;               // 지금 레벨에서 쌓은 경험치
    private int expToNext;         // 다음 레벨까지 필요한 경험치
    private int pendingExp;        // 마지막 훈련으로 받을 수 있는 경험치 (광고를 보면 받는다)
    private int evolutionLevel;    // 이 레벨부터 진화할 수 있다. 0이면 진화할 수 없는 단계(알·완성작)
    private boolean inEvolutionZone; // 진화 가능 구간: 레벨이 오를 때마다 새 모습을 받아 진화할지 고른다
    private int weightDg;          // 무게 (dg)
    private int might;             // 위력
    private int wins;              // 스파링 승
    private int losses;            // 스파링 패
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
                .sleepImageUnlocked(tamagotchi.isSleepImageUnlocked())
                .sleepImageStages(tamagotchi.sleepImageStages())
                .sleepy(tamagotchi.isSleepy(LocalDateTime.now()))
                .sleepyStartHour(tamagotchi.sleepyStartHour())
                .bathing(tamagotchi.isBathing())
                .adventuring(tamagotchi.isAdventuring())
                .dead(tamagotchi.isDead())
                .feedCount(tamagotchi.getFeedCount())
                .evoGauge(tamagotchi.legacyEvoGauge())
                .evoGaugeMax(EvolutionRules.gaugeMax(tamagotchi.getEvolutionStage()))
                .pendingEvoGain(tamagotchi.legacyPendingEvoGain())
                .evolutionReady(tamagotchi.isEvolutionReady())
                .level(tamagotchi.level())
                .exp(tamagotchi.exp())
                .expToNext(tamagotchi.expToNext())
                .pendingExp(tamagotchi.pendingExp())
                .evolutionLevel(tamagotchi.evolutionLevel())
                .inEvolutionZone(tamagotchi.isInEvolutionZone())
                .weightDg(tamagotchi.weightDg())
                .might(tamagotchi.might())
                .wins(tamagotchi.wins())
                .losses(tamagotchi.losses())
                .lastFedAt(tamagotchi.getLastFedAt())
                .currentImageBase64(tamagotchi.getCurrentImageBase64())
                .build();
    }
}
