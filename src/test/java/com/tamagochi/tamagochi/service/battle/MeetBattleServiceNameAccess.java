package com.tamagochi.tamagochi.service.battle;

/** 다른 패키지 테스트에서 MeetBattleService.battleName(패키지 공개)을 부르기 위한 통로 */
public final class MeetBattleServiceNameAccess {
    private MeetBattleServiceNameAccess() {
    }

    public static String battleName(String raw) {
        return MeetBattleService.battleName(raw);
    }
}
