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
    private String publicId;       // 밖으로 보이는 끄적이 ID (그림 경로 등). 새로 시작하면 바뀐다
    private String name;
    private String evolutionStage;
    private String faction;
    private int hunger;
    private int strength;
    private int poopCount;
    private boolean sleeping;
    private boolean sleepImageUnlocked; // 자는 모습 이미지 값을 냈다 (이후 단계는 무료)
    private java.util.List<String> sleepImageStages; // 자는 모습을 이미 만든 단계 (BABY | CHILD | ADULT)
    private java.util.List<String> sleepImageRedoStages; // 이미지를 잃어버렸을 때 무료로 한 번 다시 만들 수 있는 단계
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
    private boolean inEvolutionZone; // 진화 가능 구간: 그려 둔 후보로 언제든 진화할 수 있다
    private int evolutionEndLevel;   // 진화 가능 구간의 마지막 레벨. 이 레벨을 넘기면 저절로 진화한다. 0이면 진화할 수 없는 단계
    private int weightDg;          // 무게 (dg)
    private int might;             // 위력
    private int wins;              // 스파링 승
    private int losses;            // 스파링 패
    private int battleWins;        // 만나서 배틀 승 (이번 단계)
    private int battleLosses;      // 만나서 배틀 패 (이번 단계)
    private int championTier;      // 지금 진화하면 받는 배틀 등급 (0 없음, 1 챔피언 +15, 2 그랜드 챔피언 +30)
    private boolean legendGlow;    // 전설 잉크로 진화한 적이 있다 → 앱이 금테 + 반짝이를 바른다
    private LocalDateTime lastFedAt;
    private String currentImageBase64; // S3로 옮기기 전 그림 (옮긴 뒤에는 null)
    private String currentImageUrl;    // S3에 올린 지금 단계 그림 주소 (CDN). 있으면 이것을 쓴다
    private boolean imageUploadEnabled; // 앱이 단계 그림을 서버(S3)에 올려도 되는지
    private com.tamagochi.tamagochi.domain.battle.BattleSkill skill; // 지금 단계 배틀 기술 (아직 없으면 null → 앱이 /skill로 정해 달라고 한다)
    private String sketchImageUrl;     // 유저가 그린 원본 그림 주소 (CDN). 폰 저장소가 지워졌을 때 되살린다

    public static TamagotchiResponse from(Tamagotchi tamagotchi) {
        return TamagotchiResponse.builder()
                .id(tamagotchi.getId())
                .publicId(tamagotchi.getPublicId())
                .name(tamagotchi.getName())
                .evolutionStage(tamagotchi.getEvolutionStage().name())
                .faction(tamagotchi.getFaction())
                .hunger(tamagotchi.getHunger())
                .strength(tamagotchi.getStrength())
                .poopCount(tamagotchi.getPoopCount())
                .sleeping(tamagotchi.isSleeping())
                .sleepImageUnlocked(tamagotchi.isSleepImageUnlocked())
                .sleepImageStages(tamagotchi.sleepImageStages())
                .sleepImageRedoStages(tamagotchi.sleepImageRedoStages())
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
                .evolutionEndLevel(tamagotchi.evolutionEndLevel())
                .weightDg(tamagotchi.weightDg())
                .might(tamagotchi.might())
                .wins(tamagotchi.wins())
                .losses(tamagotchi.losses())
                .battleWins(tamagotchi.battleWins())
                .battleLosses(tamagotchi.battleLosses())
                .championTier(tamagotchi.championTier())
                .legendGlow(tamagotchi.legendGlow())
                .lastFedAt(tamagotchi.getLastFedAt())
                .currentImageBase64(tamagotchi.getCurrentImageBase64())
                .currentImageUrl(com.tamagochi.tamagochi.service.storage.PetImageUrls.of(tamagotchi.getCurrentImageKey()))
                .imageUploadEnabled(com.tamagochi.tamagochi.service.storage.PetImageUrls.uploadEnabled())
                .skill(com.tamagochi.tamagochi.service.battle.BattleSkillService.current(tamagotchi).orElse(null))
                .sketchImageUrl(com.tamagochi.tamagochi.service.storage.PetImageUrls.of(tamagotchi.getSketchImageKey()))
                .build();
    }
}
