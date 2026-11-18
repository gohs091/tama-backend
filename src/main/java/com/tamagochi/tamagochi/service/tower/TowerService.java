package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.domain.lunch.Pantry;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tower.TowerFloorCatalog;
import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import com.tamagochi.tamagochi.domain.tower.TowerRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.lunch.PantryRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.tower.TowerProgressRepository;
import com.tamagochi.tamagochi.service.common.OptimisticRetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 탑 원정의 서버 기록 (최소형): 출발 → 맵 통과 → 귀환. 포만감 소모와 맵·층 전진은 서버가 계산한다 (앱은 "통과했다"만 알린다).
 * 하루 출발 횟수·새 층 돌파 횟수 제한이 없다. 패배도 없다 — 포만감이 모자라고
 * 가방에 도시락도 없으면 집으로 돌아올 뿐이다 (도시락이 남아 있으면 원정은 이어지고, 먹어야 다음 맵을 통과할 수 있다).
 * 한 번의 원정은 출발한 지 TowerRules.EXPEDITION_TIMEOUT_MINUTES분이 지나면 끝난다: 읽거나 동작하기 전에 확인해 집으로
 * 돌려보내고, 앱을 열지 않은 사람은 스케줄러(TowerExpeditionScheduler)가 돌려보낸다. 가방은 그대로다.
 * 층의 맵 수와 공개 층 상한은 TowerFloorCatalog(resources/tower/floors.json)의 데이터다.
 * 보상 지급·무작위는 없다. 포만감은 끄적이의 hunger를 그대로 쓴다 (포만감 = 100 − hunger).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TowerService {

    /** 원정이 끝난 이유 */
    public static final String SATIETY_EMPTY = "SATIETY_EMPTY";     // 포만감이 모자라고 가방에 도시락도 없어 더 갈 수 없다
    public static final String PET_UNAVAILABLE = "PET_UNAVAILABLE"; // 원정을 이어 갈 끄적이가 없다
    public static final String RETURNED = "RETURNED";               // 스스로 돌아왔다
    public static final String TIME_UP = "TIME_UP";                 // 원정 제한 시간이 지나 집으로 돌아왔다
    public static final String TOP_FLOOR_CLEARED = "TOP_FLOOR_CLEARED"; // 맨 위 공개 층을 돌파해 더 올라갈 곳이 없다

    static {
        TowerFloorCatalog.floors(); // 층 데이터가 없거나 잘못됐으면 서버가 뜰 때 바로 실패한다
    }

    private final TowerProgressRepository progressRepository;
    private final PantryRepository pantryRepository;
    private final TamagotchiRepository tamagotchiRepository;
    private final OptimisticRetry retry;
    private final Clock clock;

    /**
     * 이번 동작의 결과. mapCleared: 맵을 통과했는가, spentSatiety: 이번에 쓴 포만감, floorCleared: 층을 돌파했는가,
     * firstClear: 그 층을 처음 돌파했는가, expeditionEnded: 이번에 원정이 끝났는가(endReason에 이유)
     */
    public record Result(boolean mapCleared, int spentSatiety, boolean floorCleared, boolean firstClear,
                         boolean expeditionEnded, String endReason) {
        static final Result NONE = new Result(false, 0, false, false, false, null);

        static Result ended(String reason) {
            return new Result(false, 0, false, false, true, reason);
        }
    }

    /**
     * 탑 전체 상태. satiety는 남은 포만감, spentToday는 오늘(KST) 탑에서 쓴 포만감, bagCount는 가방에 든 도시락 수.
     * mapsPerFloor는 지금 층(currentFloor)의 맵 수다 (층마다 다를 수 있다). openFloorCap은 열려 있는 가장 높은 층,
     * expeditionSecondsLeft는 원정 제한 시간까지 남은 초 (원정 중이 아니면 null)
     */
    public record View(int highestFloor, int maxDepartFloor, int currentFloor, int currentMap, int mapsPerFloor,
                       boolean onExpedition, int satiety, int spentToday, int mapSatietyCost, int bagCount, Result result,
                       int openFloorCap, Long expeditionSecondsLeft) {}

    /** 불러온 진행. timedOut: 이번에 불러오면서 제한 시간이 지나 원정을 접었다 */
    private record Loaded(TowerProgress progress, boolean timedOut) {}

    /** 탑 상태를 읽는다. 원정 제한 시간이 지났으면 이번에 집으로 돌려보내고 TIME_UP으로 알린다 */
    public View get(User user) {
        return retry.run(() -> {
            Loaded loaded = load(user);
            return view(user, loaded.progress(), loaded.timedOut() ? Result.ended(TIME_UP) : Result.NONE);
        });
    }

    /**
     * 제한 시간이 지난 원정을 모두 접는다 (스케줄러용). 접은 수를 돌려준다.
     * 한 사람씩 따로 처리해, 한 사람의 요청과 겹쳐 실패해도 나머지는 접힌다
     */
    public int expireTimedOut() {
        int ended = 0;
        for (Long id : progressRepository.findIdsOnExpedition()) {
            try {
                boolean expired = retry.run(() -> progressRepository.findById(id)
                        .map(p -> p.expireIfTimedOut(LocalDateTime.now(clock))).orElse(false));
                if (expired) ended++;
            } catch (RuntimeException e) {
                log.warn("원정 만료 처리 실패 - progress={}", id, e);
            }
        }
        return ended;
    }

    /**
     * 원정을 떠난다 (집에서만). floor를 비우면 돌파한 층 바로 위에서 시작한다. 갈 수 있는 층은 1 ~ 돌파한 층 + 1이고
     * 공개 층 상한(TowerFloorCatalog.openFloorCap)을 넘지 않는다. 제한 시간이 지난 원정은 먼저 접고 새로 떠난다.
     * 포만감이 맵 1장 값보다 적어도 가방에 도시락이 있으면 떠날 수 있다 (도시락은 탑에서만 먹을 수 있다)
     */
    public View depart(User user, Integer floor) {
        return retry.run(() -> {
            TowerProgress progress = load(user).progress();
            if (progress.isOnExpedition()) throw new CodedException("ON_EXPEDITION", "이미 탑에 가 있어요.");

            Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId())
                    .orElseThrow(() -> new CodedException("NO_PET", "끄적이가 없어요."));
            if (pet.isDead()) throw new CodedException("PET_UNAVAILABLE", "끄적이가 죽어서 탑에 갈 수 없어요.");
            if (pet.getEvolutionStage() == EvolutionStage.EGG) throw new CodedException("PET_UNAVAILABLE", "알이 깨어나면 탑에 갈 수 있어요.");
            if (pet.isSleeping()) throw new CodedException("PET_UNAVAILABLE", "자고 있을 땐 탑에 갈 수 없어요.");
            if (pet.isBathing()) throw new CodedException("PET_UNAVAILABLE", "잉크통에 들어가 있을 땐 탑에 갈 수 없어요.");
            if (pet.isAdventuring()) throw new CodedException("PET_UNAVAILABLE", "모험을 떠나 있을 땐 탑에 갈 수 없어요.");

            int target = floor == null ? progress.maxDepartFloor() : floor;
            if (target < TowerRules.FIRST_FLOOR || target > progress.maxDepartFloor()) {
                throw new CodedException("FLOOR_LOCKED", "아직 갈 수 없는 층이에요.");
            }
            if (pet.satiety() < TowerRules.MAP_SATIETY_COST && bagCount(user) == 0) {
                throw new CodedException("NOT_ENOUGH_SATIETY", "배가 고파서 탑에 갈 수 없어요.");
            }
            progress.depart(target, LocalDateTime.now(clock));
            return view(user, progress, Result.NONE);
        });
    }

    /**
     * 지금 맵을 통과했다고 기록한다: 포만감을 쓰고 다음 맵(그 층의 마지막 맵이면 다음 층 첫 맵)으로 간다.
     * 층의 맵 수는 그 층의 데이터를 쓴다. 맨 위 공개 층의 마지막 맵을 통과하면 더 올라가지 않고 원정이 끝난다 (TOP_FLOOR_CLEARED).
     * 원정 제한 시간이 지났으면 통과시키지 않고 집으로 돌려보낸다 (TIME_UP).
     * 포만감이 맵 1장 값보다 적으면 통과시키지 않는다:
     * 가방에 도시락이 있으면 원정은 그대로 두고 거절하고 (NOT_ENOUGH_SATIETY — 도시락을 먹고 다시 부른다),
     * 가방이 비었으면 원정을 끝낸다 (SATIETY_EMPTY).
     * 통과한 뒤 포만감이 맵 1장 값보다 적고 가방에 도시락도 없으면 더 갈 수 없으니 원정이 끝난다.
     * TODO(H-1): 전투 검증 추가 — 지금은 앱이 통과했다고 알리면 그대로 믿는다
     */
    public View clearMap(User user) {
        return retry.run(() -> {
            Loaded loaded = load(user);
            TowerProgress progress = loaded.progress();
            // 거절(예외)하면 귀가 기록까지 되돌려지므로, 만료는 예외가 아니라 결과로 알린다
            if (loaded.timedOut()) return view(user, progress, Result.ended(TIME_UP));
            if (!progress.isOnExpedition()) throw new CodedException("NOT_ON_EXPEDITION", "탑에 가 있지 않아요.");

            Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId()).filter(p -> !p.isDead()).orElse(null);
            if (pet == null) { // 원정 중에 끄적이가 없어졌다 (새로 시작 등): 원정만 접는다
                progress.endExpedition();
                return view(user, progress, Result.ended(PET_UNAVAILABLE));
            }
            if (pet.satiety() < TowerRules.MAP_SATIETY_COST) {
                // 가방에 도시락이 있으면 원정을 끝내지 않고 거절한다 (앱이 먹기를 권한다)
                if (bagCount(user) > 0) {
                    throw new CodedException("NOT_ENOUGH_SATIETY", "배가 고파요. 가방의 도시락을 먼저 먹어요.");
                }
                progress.endExpedition();
                return view(user, progress, Result.ended(SATIETY_EMPTY));
            }

            // 탑은 포만감을 SATIETY_FLOOR 아래로 내리지 않는다 (포만감이 딱 맵 값이면 1을 남긴다): 굶주림 타이머를 탑이 켜지 않게
            int spent = pet.spendSatiety(Math.min(TowerRules.MAP_SATIETY_COST, pet.satiety() - TowerRules.SATIETY_FLOOR));
            progress.addSpent(LocalDate.now(clock), spent);
            TowerProgress.Advance advance = progress.advance();

            String endReason = null;
            if (advance.topCleared()) {
                endReason = TOP_FLOOR_CLEARED; // advance가 이미 원정을 접었다
            } else if (pet.satiety() < TowerRules.MAP_SATIETY_COST && bagCount(user) == 0) {
                progress.endExpedition();
                endReason = SATIETY_EMPTY;
            }
            return view(user, progress, new Result(true, spent, advance.floorCleared(), advance.firstClear(),
                    endReason != null, endReason));
        });
    }

    /** 집으로 돌아온다. 가방에 남은 도시락은 가방에 그대로 있다. 이미 제한 시간이 지나 있었으면 TIME_UP으로 알린다 */
    public View returnHome(User user) {
        return retry.run(() -> {
            Loaded loaded = load(user);
            TowerProgress progress = loaded.progress();
            if (loaded.timedOut()) return view(user, progress, Result.ended(TIME_UP));
            if (!progress.isOnExpedition()) return view(user, progress, Result.NONE);
            progress.endExpedition();
            return view(user, progress, Result.ended(RETURNED));
        });
    }

    private int bagCount(User user) {
        return pantryRepository.findByUserId(user.getId()).map(Pantry::bagUsed).orElse(0);
    }

    /** 진행을 불러온다 (없으면 만든다). 원정 제한 시간이 지났으면 읽거나 동작하기 전에 여기서 집으로 돌려보낸다 */
    private Loaded load(User user) {
        TowerProgress progress = progressRepository.findByUserId(user.getId())
                .orElseGet(() -> progressRepository.save(new TowerProgress(user)));
        return new Loaded(progress, progress.expireIfTimedOut(LocalDateTime.now(clock)));
    }

    private View view(User user, TowerProgress progress, Result result) {
        int satiety = tamagotchiRepository.findActiveByUserId(user.getId()).map(Tamagotchi::satiety).orElse(0);
        return new View(progress.getHighestFloor(), progress.maxDepartFloor(), progress.getCurrentFloor(), progress.getCurrentMap(),
                TowerFloorCatalog.mapsPerFloor(progress.getCurrentFloor()), progress.isOnExpedition(), satiety,
                progress.spentToday(LocalDate.now(clock)), TowerRules.MAP_SATIETY_COST, bagCount(user), result,
                TowerFloorCatalog.openFloorCap(), progress.secondsLeft(LocalDateTime.now(clock)));
    }
}
