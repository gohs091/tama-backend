package com.tamagochi.tamagochi.service.battle;

import com.tamagochi.tamagochi.domain.battle.MeetBattle;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.battle.MeetBattleRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.tamagochi.tamagochi.service.battle.MeetBattleRules.*;

/**
 * 대결 결과를 저장하고 잉크 보상을 준다 (하루 보상 횟수 제한에 기록을 쓴다).
 * 하루 {@value MeetBattleRules#FREE_DAILY_BATTLES}판은 그냥 하고, 그 뒤로는 한 판마다 포만감·파워를 쓴다. 끄적이의 이번 단계 배틀 전적도 남긴다.
 */
@Service
@RequiredArgsConstructor
public class MeetBattleRecorder {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MeetBattleRepository battleRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final TamagotchiRepository tamagotchiRepository;

    /** 한 판 결과: 받은 잉크, 이번 판에 포만감·파워를 썼는지, 오늘 남은 무료 판 */
    public record Rewards(int host, int guest, boolean hostCharged, boolean guestCharged, int hostFreeLeft, int guestFreeLeft) {
        public Rewards(int host, int guest) { this(host, guest, false, false, 0, 0); }
    }

    /** 오늘 한 배틀 수 */
    @Transactional(readOnly = true)
    public long battlesToday(Long userId) {
        return battleRepository.countBattles(userId, startOfTodayKst());
    }

    /** 이번 판을 할 수 없는 이유 (무료 판을 다 쓰고 포만감·파워가 모자람). 할 수 있으면 null */
    @Transactional(readOnly = true)
    public String blockReason(Long userId) {
        if (battlesToday(userId) < FREE_DAILY_BATTLES) return null;
        return tamagotchiRepository.findActiveByUserId(userId).map(p -> p.battleShortage(BATTLE_COST)).orElse(null);
    }

    /** 저장 시각(created_at)은 서버 시간대라, 오늘(KST) 0시를 서버 시간대로 바꿔 비교한다 */
    static LocalDateTime startOfTodayKst() {
        return LocalDate.now(KST).atStartOfDay(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * @param winner 1 = 방장 승, -1 = 손님 승, 0 = 무승부
     * @param quitter 대결 중에 나간 사람 (없으면 null). 나간 사람은 지고 보상이 없다
     */
    @Transactional
    public Rewards record(Long hostId, Long guestId, int winner, Long quitter, int hostTaps, int guestTaps) {
        LocalDateTime since = startOfTodayKst();
        long hostBefore = battleRepository.countBattles(hostId, since);
        long guestBefore = battleRepository.countBattles(guestId, since);
        boolean pairOk = battleRepository.countPairRewarded(hostId, guestId, since) < PAIR_DAILY_REWARDED;
        boolean hostOk = pairOk && battleRepository.countRewarded(hostId, since) < DAILY_REWARDED;
        boolean guestOk = pairOk && battleRepository.countRewarded(guestId, since) < DAILY_REWARDED;

        Long winnerId = winner > 0 ? hostId : winner < 0 ? guestId : null;
        int hostReward = hostOk ? amount(hostId, winnerId, quitter) : 0;
        int guestReward = guestOk ? amount(guestId, winnerId, quitter) : 0;

        MeetBattle battle = battleRepository.save(MeetBattle.builder()
                .hostUserId(hostId).guestUserId(guestId).winnerUserId(winnerId)
                .hostTaps(hostTaps).guestTaps(guestTaps)
                .hostReward(hostReward).guestReward(guestReward)
                .quit(quitter != null)
                .build());
        grant(hostId, hostReward, battle.getId());
        grant(guestId, guestReward, battle.getId());
        int hostResult = quitter != null ? (quitter.equals(hostId) ? -1 : 1) : winner;
        boolean hostCharged = settle(hostId, hostResult, hostBefore);
        boolean guestCharged = settle(guestId, -hostResult, guestBefore);
        return new Rewards(hostReward, guestReward, hostCharged, guestCharged,
                (int) Math.max(0, FREE_DAILY_BATTLES - hostBefore - 1), (int) Math.max(0, FREE_DAILY_BATTLES - guestBefore - 1));
    }

    /** 끄적이에 전적을 남기고, 무료 판을 다 썼으면 포만감·파워를 쓴다. 썼으면 true */
    private boolean settle(Long userId, int result, long battlesBefore) {
        Tamagotchi pet = tamagotchiRepository.findActiveByUserId(userId).orElse(null);
        if (pet == null) return false;
        pet.recordBattle(result);
        if (battlesBefore < FREE_DAILY_BATTLES || pet.battleShortage(BATTLE_COST) != null) return false;
        pet.consumeForTraining(BATTLE_COST, BATTLE_COST);
        return true;
    }

    private static int amount(Long me, Long winnerId, Long quitter) {
        if (me.equals(quitter)) return 0;
        if (winnerId == null) return DRAW_INK;
        return me.equals(winnerId) ? WIN_INK : LOSE_INK;
    }

    private void grant(Long userId, int amount, Long battleId) {
        if (amount <= 0) return;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        pointService.earnPoint(user, amount, "meet-battle-" + battleId + "-" + userId);
    }

}
