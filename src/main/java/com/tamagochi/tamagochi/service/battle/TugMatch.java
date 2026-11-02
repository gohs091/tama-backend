package com.tamagochi.tamagochi.service.battle;

import java.util.Optional;

import static com.tamagochi.tamagochi.service.battle.MeetBattleRules.*;

/**
 * 한 판의 연타 줄다리기. 연타 수는 시작부터 흐른 시간으로 낼 수 있는 만큼만 센다 (매크로·조작 방지).
 * 판정: 한쪽이 {@link MeetBattleRules#KO_LEAD}만큼 앞서면 바로 끝, 아니면 {@link MeetBattleRules#FIGHT_MS} 뒤 많이 누른 쪽이 이긴다.
 */
public final class TugMatch {

    /** 결과: 1 = 방장 승, -1 = 손님 승, 0 = 무승부 */
    public record Outcome(int winner, boolean ko) {}

    private final long startAt;
    private int hostTaps;
    private int guestTaps;

    public TugMatch(long startAt) {
        this.startAt = startAt;
    }

    private int allowed(long now) {
        long elapsed = Math.max(0, Math.min(now, startAt + FIGHT_MS) - startAt);
        return (int) (MAX_TAPS_PER_SECOND * elapsed / 1000) + TAP_BURST;
    }

    /** 연타를 더한다. 시간 밖이거나 상한을 넘는 만큼은 버린다 */
    public void addTaps(boolean host, int n, long now) {
        if (n <= 0 || now < startAt || now > startAt + FIGHT_MS + TICK_MS) return;
        int cap = allowed(now);
        if (host) hostTaps = Math.min(cap, hostTaps + n);
        else guestTaps = Math.min(cap, guestTaps + n);
    }

    public int hostTaps() { return hostTaps; }
    public int guestTaps() { return guestTaps; }

    public Optional<Outcome> decided(long now) {
        int lead = hostTaps - guestTaps;
        if (Math.abs(lead) >= KO_LEAD) return Optional.of(new Outcome(Integer.signum(lead), true));
        if (now - startAt >= FIGHT_MS) return Optional.of(new Outcome(Integer.signum(lead), false));
        return Optional.empty();
    }
}
