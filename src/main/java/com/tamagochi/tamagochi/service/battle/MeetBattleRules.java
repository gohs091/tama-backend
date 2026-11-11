package com.tamagochi.tamagochi.service.battle;

/**
 * 친구와 배틀 규칙. 한 사람이 방을 열어 초대(방 코드)를 보내고 친구가 들어오면, 5초 동안 동시에 연타해 줄다리기를 한다.
 */
public final class MeetBattleRules {
    private MeetBattleRules() {}

    /** 방 코드로 들어올 수 있는 시간. 방이 열려 있는 동안 코드는 바뀌지 않는다 (초대 주소·불러 준 코드가 그대로 통하게). 앱이 처음인 친구가 끄적이를 그리는 동안에도 들어올 수 있게 넉넉히 */
    public static final long CODE_VALID_MS = 10 * 60_000;
    /** 친구를 기다리는 방이 아무도 안 오면 닫히는 시간 */
    public static final long WAITING_ROOM_TTL_MS = 10 * 60_000;
    /** 친구가 들어오자마자 나오는 시작 연출 (걸어 들어와 빙글 돌고 갈라선다) */
    public static final long INTRO_MS = 2_800;
    /** 끄적이 그림이 늦게 와도 이만큼 기다린 뒤에는 시작한다 */
    public static final long PROFILE_WAIT_MS = 3_000;
    public static final long COUNTDOWN_MS = 3_000;
    public static final long FIGHT_MS = 5_000;
    public static final long TICK_MS = 100;

    /** 사람 손으로 낼 수 있는 연타 상한 (초당). 넘는 만큼은 버린다 */
    public static final int MAX_TAPS_PER_SECOND = 15;
    public static final int TAP_BURST = 3;
    /** 이만큼 앞서면 바로 이긴다 (줄을 끝까지 당겼다) */
    public static final int KO_LEAD = 30;

    /** 하루(KST) 이만큼은 그냥 배틀하고, 그 뒤로는 한 판에 포만감·파워를 이만큼 쓴다 (트레이닝과 같다) */
    public static final int FREE_DAILY_BATTLES = 3;
    public static final int BATTLE_COST = 10;

    public static final int WIN_INK = 30;
    public static final int LOSE_INK = 10;
    public static final int DRAW_INK = 10;
    /** 하루(KST) 보상을 받는 대결 수 (그 뒤로도 대결은 할 수 있다) */
    public static final int DAILY_REWARDED = 5;
    /** 같은 친구와 하루 보상을 받는 대결 수 (둘이 짜고 번갈아 이겨 주며 잉크를 모으지 못하게) */
    public static final int PAIR_DAILY_REWARDED = 2;

    public static final int TAUNT_COUNT = 8;
    public static final long TAUNT_GAP_MS = 600;
    /** 상대 화면에 보여줄 끄적이 작은 그림 (data URL) 최대 길이 */
    public static final int THUMB_MAX_CHARS = 80_000;
}
