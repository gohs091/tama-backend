package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.domain.promotion.PromotionOutcome;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrainingResult {
    private int clickCount;
    private int reward;
    private int evoGain;   // 업데이트 전 앱용: 받을 수 있는 경험치를 예전 진화 게이지 칸 수로 바꾼 값
    private int evoGauge;  // 업데이트 전 앱용: 진화 가능 구간까지의 진행도를 예전 게이지 칸 수로 바꾼 값
    private boolean win;   // 스파링에서 이겼다 (서버 판정)
    private int enemyTaps; // 상대가 친 탭 수
    private int expGain;   // 이번 트레이닝으로 바로 받은 경험치 (승리 보너스 포함)
    private int winBonus;  // 그중 승리 보너스
    private int pendingExp; // 광고를 보면 더 받는 경험치 (기본 경험치만큼 = 2배)
    private int mightGain; // 이번 트레이닝으로 오른 위력 (레벨 업 몫 포함)
    private int wins;
    private int losses;
    private int level;     // 경험치를 받은 뒤 레벨
    private int exp;
    private int expToNext;
    private boolean inEvolutionZone; // 진화 가능 구간이라 경험치가 줄어 들어왔다
    private PromotionOutcome promotion; // 완성작 트레이닝의 프로모션 결과 (대상이 아니거나 꺼져 있으면 null)
}