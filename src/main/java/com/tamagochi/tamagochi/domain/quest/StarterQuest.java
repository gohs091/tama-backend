package com.tamagochi.tamagochi.domain.quest;

import java.util.Arrays;

/**
 * "첫걸음" 퀘스트 목록. 적힌 순서가 앱에 보이는 순서이고, id는 앱(starterGuide.ts의 GuideStepId)과 같다.
 * 보상(잉크)은 항목마다 고정이다 (랜덤 없음). 값은 여기서만 바꾼다.
 *
 * 보상 기준 (합계 300 = 다른 진영 배경 한 장 값, 가입 잉크 500의 60%, 광고 1.5번, 접속 보상 3번):
 * - 한 번 누르면 끝나는 돌보기류는 20 (고기·알약 4번 값)
 * - 화면을 옮겨 가서 잉크·열매를 써야 하는 것은 30 (씨앗 5~20, 트레이닝 한 판 회복 40)
 * - 며칠 키워야 하는 진화만 100 (접속 보상 한 번과 같다)
 */
public enum StarterQuest {
    CARE("care", 20, false),      // 고기·알약 주기
    TRAIN("train", 30, false),    // 트레이닝 1회
    CLEAN("clean", 20, false),    // 청소하기
    SLEEP("sleep", 20, false),    // 불 끄고 재우기
    BAG("bag", 20, true),         // 보관함 열어 보기 (서버가 알 수 없어 앱이 알린다)
    FARM("farm", 30, false),      // 씨앗 심기
    ROOM("room", 30, false),      // 방에 가구 놓기
    EVOLVE("evolve", 100, false), // 진화시키기
    MEET("meet", 30, false);      // 친구 배틀 또는 광장

    /** 앱과 주고받는 이름. DB에도 이 값으로 적는다 */
    public final String id;
    /** 보상(잉크) */
    public final int reward;
    /** 서버에 해당 동작이 없어 앱이 끝냈다고 알려 주는 항목 (POST /api/quests/starter/{id}/done) */
    public final boolean clientReported;

    StarterQuest(String id, int reward, boolean clientReported) {
        this.id = id;
        this.reward = reward;
        this.clientReported = clientReported;
    }

    /** 앱에 보이는 순서 (1부터) */
    public int order() {
        return ordinal() + 1;
    }

    public static StarterQuest of(String id) {
        String wanted = id == null ? "" : id.strip();
        return Arrays.stream(values()).filter(q -> q.id.equalsIgnoreCase(wanted)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("없는 퀘스트예요."));
    }
}
