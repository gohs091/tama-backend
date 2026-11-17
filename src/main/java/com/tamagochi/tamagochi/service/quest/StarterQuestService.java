package com.tamagochi.tamagochi.service.quest;

import com.tamagochi.tamagochi.domain.farm.Farm;
import com.tamagochi.tamagochi.domain.farm.FarmCatalog;
import com.tamagochi.tamagochi.domain.farm.FarmPlot;
import com.tamagochi.tamagochi.domain.quest.StarterQuest;
import com.tamagochi.tamagochi.domain.quest.StarterQuestProgress;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.domain.yard.YardCatalog;
import com.tamagochi.tamagochi.repository.battle.MeetBattleRepository;
import com.tamagochi.tamagochi.repository.farm.FarmRepository;
import com.tamagochi.tamagochi.repository.quest.StarterQuestProgressRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.yard.YardRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 첫걸음 퀘스트: 처음 해 본 순간을 기록하고(markDone), 끝낸 항목마다 고정 잉크를 한 번 준다(claim).
 * - 기록은 돌보기·농장·방·배틀 서비스가 동작에 성공한 자리에서 부른다. 이미 끝낸 항목은 조회 한 번으로 지나간다.
 * - 보상은 조건이 붙은 UPDATE(끝냈고 아직 안 받은 줄만)가 1줄을 바꿨을 때만 주므로 동시에 여러 번 눌러도 한 번이다.
 * - 이 기능이 생기기 전부터 쓰던 사람은 처음 조회할 때 서버 상태로 확실한 항목을 끝낸 것으로 채운다(fill).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StarterQuestService {

    /**
     * 이 기능이 생기기 전에 이미 해 본 항목에도 보상을 줄지. false면 보상 없이 받은 것으로 닫고(보상 0으로 보인다),
     * true면 끝낸 것으로만 채워 직접 받게 한다 (기존 유저 한 명에 많게는 260잉크).
     */
    public static final boolean RETRO_REWARD = false;

    private final StarterQuestProgressRepository progressRepository;
    private final TamagotchiRepository tamagotchiRepository;
    private final FarmRepository farmRepository;
    private final YardRepository yardRepository;
    private final MeetBattleRepository meetBattleRepository;
    private final PointService pointService;
    private final Clock clock;

    private boolean retroReward = RETRO_REWARD;

    /** 테스트 전용: 소급 지급을 켠 것처럼 돌려 본다 */
    void setRetroReward(boolean retroReward) {
        this.retroReward = retroReward;
    }

    /** reward: 받을(받은) 잉크. 예전에 이미 해 봐서 보상 없이 닫힌 항목은 0. claimable: 지금 받을 수 있다 */
    public record QuestView(String id, int order, boolean done, boolean claimed, boolean claimable, int reward, boolean clientReported) {}

    /** granted: 이번 요청으로 받은 잉크 (조회·알리기는 0) */
    public record View(List<QuestView> quests, int doneCount, int total, int balance, int granted) {}

    @Transactional
    public View get(User user) {
        return view(user, 0);
    }

    /** 끝낸 퀘스트의 보상을 받는다. 끝내지 않았거나 이미 받았으면 거절한다 */
    @Transactional
    public View claim(User user, String questId) {
        StarterQuest quest = StarterQuest.of(questId);
        if (progressRepository.claim(user.getId(), quest.id, clock.instant()) == 0) {
            boolean claimed = progressRepository.findByUserIdAndQuest(user.getId(), quest.id)
                    .map(StarterQuestProgress::isClaimed).orElse(false);
            throw new IllegalStateException(claimed ? "이미 받은 보상이에요." : "아직 끝내지 않은 퀘스트예요.");
        }
        // 유저·퀘스트마다 고정 키라서 지급 기록(point_transaction)에서도 한 번만 들어간다
        pointService.earnPoint(user, quest.reward, "starter-quest-" + user.getId() + "-" + quest.id);
        log.info("첫걸음 보상 - user={}, quest={}, 잉크 {}", user.getId(), quest.id, quest.reward);
        return view(user, quest.reward);
    }

    /** 앱이 끝냈다고 알린다. 서버가 스스로 알 수 있는 항목은 받지 않는다 */
    @Transactional
    public View reportDone(User user, String questId) {
        StarterQuest quest = StarterQuest.of(questId);
        if (!quest.clientReported) throw new IllegalArgumentException("앱에서 알릴 수 없는 퀘스트예요.");
        record(user.getId(), quest);
        return view(user, 0);
    }

    /** 다른 서비스가 동작에 성공한 뒤 부른다. 기록에 실패해도 원래 동작을 막지 않게 조용히 넘어간다 */
    public void markDone(User user, StarterQuest quest) {
        if (user != null) markDone(user.getId(), quest);
    }

    public void markDone(Long userId, StarterQuest quest) {
        if (userId == null || quest == null) return;
        try {
            record(userId, quest);
        } catch (RuntimeException e) {
            log.warn("첫걸음 기록 실패 - user={}, quest={}", userId, quest.id, e);
        }
    }

    /** 엔티티를 읽지 않고 쿼리로만 한다 (부른 쪽 트랜잭션에 묵은 줄을 남기지 않도록) */
    private void record(Long userId, StarterQuest quest) {
        if (progressRepository.countDone(userId, quest.id) > 0) return; // 대부분 여기서 끝난다
        Instant now = clock.instant();
        if (progressRepository.markDone(userId, quest.id, now) > 0) return;
        // 줄이 아직 없다. 그 사이 처음 조회(fill)가 빈 줄을 만들었으면 넣지 못하므로 한 번 더 바꾼다
        if (progressRepository.insertDone(userId, quest.id, now) == 0) progressRepository.markDone(userId, quest.id, now);
    }

    /**
     * 아직 줄이 없는 퀘스트의 줄을 만든다 (유저마다 사실상 처음 조회할 때 한 번).
     * 서버 상태로 이미 해 본 게 확실하면 끝낸 것으로 채우고, 소급 지급이 꺼져 있으면 보상 없이 받은 것으로 닫는다.
     * 먼저 기록된 줄(기능이 생긴 뒤 직접 한 것)은 건드리지 않는다.
     */
    private List<StarterQuestProgress> fill(Long userId) {
        List<StarterQuestProgress> rows = progressRepository.findByUserId(userId);
        Set<String> have = rows.stream().map(StarterQuestProgress::getQuest).collect(Collectors.toSet());
        List<StarterQuest> missing = Arrays.stream(StarterQuest.values()).filter(q -> !have.contains(q.id)).toList();
        if (missing.isEmpty()) return rows;

        Set<StarterQuest> proven = alreadyDone(userId);
        Instant now = clock.instant();
        for (StarterQuest q : missing) {
            if (!proven.contains(q)) progressRepository.insertOpen(userId, q.id);
            else if (retroReward) progressRepository.insertDone(userId, q.id, now);
            else progressRepository.insertBackfilled(userId, q.id, now);
        }
        if (!proven.isEmpty()) log.info("첫걸음 기존 유저 채우기 - user={}, 이미 한 항목 {}", userId, proven);
        return progressRepository.findByUserId(userId);
    }

    /** 서버 상태로 이미 해 본 게 확실한 항목. 애매한 것(청소, 보관함, 광장 구경)은 넣지 않는다 */
    private Set<StarterQuest> alreadyDone(Long userId) {
        Set<StarterQuest> out = EnumSet.noneOf(StarterQuest.class);
        tamagotchiRepository.findActiveByUserId(userId).ifPresent(pet -> {
            EvolutionStage stage = pet.getEvolutionStage();
            boolean evolved = stage == EvolutionStage.CHILD || stage == EvolutionStage.ADULT;
            if (pet.getFeedCount() > 0) out.add(StarterQuest.CARE);
            // 레벨은 트레이닝으로만 오른다
            if (stage != EvolutionStage.EGG && (pet.level() >= 2 || pet.wins() + pet.losses() > 0 || evolved)) out.add(StarterQuest.TRAIN);
            if (evolved) out.add(StarterQuest.EVOLVE);
            if (pet.getSleptAt() != null) out.add(StarterQuest.SLEEP);
            if (pet.battleWins() + pet.battleLosses() > 0) out.add(StarterQuest.MEET);
        });
        if (!out.contains(StarterQuest.MEET) && meetBattleRepository.countBattles(userId, LocalDateTime.of(2000, 1, 1, 0, 0)) > 0) {
            out.add(StarterQuest.MEET);
        }
        if (farmRepository.findByUserId(userId).map(StarterQuestService::everPlanted).orElse(false)) out.add(StarterQuest.FARM);
        if (yardRepository.findByUserId(userId)
                .map(yard -> yard.getSlots().keySet().stream().anyMatch(YardCatalog.ROOM_SLOTS::containsKey)).orElse(false)) {
            out.add(StarterQuest.ROOM);
        }
        return out;
    }

    private static boolean everPlanted(Farm farm) {
        return IntStream.range(0, FarmCatalog.PLOTS).mapToObj(farm::plot)
                .anyMatch((FarmPlot p) -> p.getCrop() != null || p.getSeq() > 0);
    }

    private View view(User user, int granted) {
        Map<String, StarterQuestProgress> rows = fill(user.getId()).stream()
                .collect(Collectors.toMap(StarterQuestProgress::getQuest, Function.identity(), (a, b) -> a));
        List<QuestView> quests = Arrays.stream(StarterQuest.values()).map(q -> {
            StarterQuestProgress p = rows.get(q.id);
            boolean done = p != null && p.isDone();
            boolean claimed = p != null && p.isClaimed();
            return new QuestView(q.id, q.order(), done, claimed, done && !claimed,
                    p != null && p.isBackfilled() ? 0 : q.reward, q.clientReported);
        }).toList();
        return new View(quests, (int) quests.stream().filter(QuestView::done).count(), quests.size(),
                pointService.getWallet(user).getBalance(), granted);
    }
}
