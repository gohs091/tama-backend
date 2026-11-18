package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.domain.lunch.Pantry;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tower.TowerFloorCatalog;
import com.tamagochi.tamagochi.domain.tower.TowerMonsterCatalog;
import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import com.tamagochi.tamagochi.domain.tower.TowerRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.lunch.PantryRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.tower.TowerProgressRepository;
import com.tamagochi.tamagochi.service.common.OptimisticRetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 탑 원정의 서버 기록 (최소형): 출발 → 맵 통과 → 귀환. 포만감 소모와 맵·층 전진은 서버가 계산한다 (앱은 "통과했다"만 알린다).
 * 하루 출발 횟수·새 층 돌파 횟수 제한이 없다. 패배도 없다 — 포만감이 모자라고
 * 가방에 도시락도 없으면 집으로 돌아올 뿐이다 (도시락이 남아 있으면 원정은 이어지고, 먹어야 다음 맵을 통과할 수 있다).
 * 한 번의 원정은 출발한 지 TowerRules.EXPEDITION_TIMEOUT_MINUTES분이 지나면 끝난다: 읽거나 동작하기 전에 확인해 집으로
 * 돌려보내고, 앱을 열지 않은 사람은 스케줄러(TowerExpeditionScheduler)가 돌려보낸다. 가방은 그대로다.
 * 층의 맵 수·공개 층 상한·진영·바퀴·구출 NPC id·맵별 몬스터 수는 TowerFloorCatalog(resources/tower/floors.json)의 데이터다.
 * 보상 지급·무작위는 없다. 포만감은 끄적이의 hunger를 그대로 쓴다 (포만감 = 100 − hunger).
 * 맵 통과 검증(H-1): 서버가 가진 지금 층·맵에서 한 칸만 전진하고, 원정 id(runId)·층·맵을 보내면 서버 값과 같아야 하며,
 * 맵에 들어온 뒤 최소 체류 시간(tower.clear-map.min-seconds)이 지나야 통과한다. 한 대 한 대의 전투는 검증하지 않는다.
 */
@Service
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
        TowerMonsterCatalog.monsters(); // 몬스터 데이터도 마찬가지
    }

    private final TowerProgressRepository progressRepository;
    private final PantryRepository pantryRepository;
    private final TamagotchiRepository tamagotchiRepository;
    private final OptimisticRetry retry;
    private final Clock clock;
    /** 맵 하나에 머물러야 하는 최소 시간(초). 이보다 일찍 온 통과는 거절한다. 0 이하면 검사하지 않는다 */
    private final int minMapSeconds;

    public TowerService(TowerProgressRepository progressRepository, PantryRepository pantryRepository,
                        TamagotchiRepository tamagotchiRepository, OptimisticRetry retry, Clock clock,
                        @Value("${tower.clear-map.min-seconds:20}") int minMapSeconds) {
        this.progressRepository = progressRepository;
        this.pantryRepository = pantryRepository;
        this.tamagotchiRepository = tamagotchiRepository;
        this.retry = retry;
        this.clock = clock;
        this.minMapSeconds = minMapSeconds;
    }

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
     * expeditionSecondsLeft는 원정 제한 시간까지 남은 초 (원정 중이 아니면 null).
     * runId는 지금 원정의 id (원정 중이 아니거나, 이 값이 생기기 전에 떠난 원정이면 null).
     * secondsUntilClear는 지금 맵을 통과할 수 있을 때까지 남은 초 (원정 중이 아니면 null, 바로 통과할 수 있으면 0).
     * 층 데이터(M-0, 지금 층 기준): faction은 진영 id, wheel은 바퀴(1 창고·2 공장·3 본사), rescueNpcIds는 그 층에서 구출하는
     * NPC의 id (없으면 빈 목록 — 구출 로직은 아직 없다), currentMapMonsters는 지금 맵의 몬스터 수 (아직 적지 않은 층이면 null).
     * 전투력 숫자는 싣지 않는다
     */
    public record View(int highestFloor, int maxDepartFloor, int currentFloor, int currentMap, int mapsPerFloor,
                       boolean onExpedition, int satiety, int spentToday, int mapSatietyCost, int bagCount, Result result,
                       int openFloorCap, Long expeditionSecondsLeft, String runId, Long secondsUntilClear,
                       String faction, Integer wheel, List<String> rescueNpcIds, MapMonstersView currentMapMonsters) {}

    /** 맵 한 장의 몬스터 수. provisional: 임시 값인가 */
    public record MapMonstersView(int normal, int swarm, int boss, boolean provisional) {}

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
     * 포만감이 맵 1장 값보다 적어도 가방에 도시락이 있으면 떠날 수 있다 (도시락은 탑에서만 먹을 수 있다).
     * 떠날 때마다 새 원정 id(runId)를 주고, 첫 맵의 시계를 시작한다
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
            progress.depart(target, LocalDateTime.now(clock), UUID.randomUUID().toString());
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
     * 검증 (거절하면 아무것도 바뀌지 않고 포만감도 쓰지 않는다):
     * runId를 보냈으면 지금 원정의 것과 같아야 한다 (RUN_MISMATCH). floor·map을 보냈으면 서버의 지금 층·맵과 같아야 한다
     * (MAP_MISMATCH) — 어느 쪽이든 전진은 서버의 지금 맵에서 한 칸뿐이라 건너뛸 수 없다. 비우면(예전 앱) 서버 값만 따른다.
     * 다만 floor·map을 둘 다 보냈고 그것이 이번 원정에서 방금 통과한 맵(지금 위치의 바로 이전 맵)이면 재전송으로 보고,
     * 포만감도 맵도 건드리지 않은 채 지금 상태를 그대로 돌려준다 (멱등, result는 비어 있다). 더 옛 맵은 MAP_MISMATCH다.
     * 맵에 들어온 지 minMapSeconds초가 안 됐으면 거절한다 (MAP_TOO_SOON) — runId를 보내지 않아도 적용한다.
     * 포만감 검사가 최소 시간보다 먼저라, 포만감이 모자라면 기다리지 않고 바로 NOT_ENOUGH_SATIETY 또는 귀가(SATIETY_EMPTY)다.
     * 맵에 들어온 시각이 없는 원정(그 기록이 생기기 전에 떠난 원정)은 이번 한 번은 시간 검사 없이 받고, 다음 맵부터 시계가 돈다.
     * 한 대 한 대의 전투(퍼히트)는 검증하지 않는다
     */
    public View clearMap(User user, String runId, Integer floor, Integer map) {
        return retry.run(() -> {
            Loaded loaded = load(user);
            TowerProgress progress = loaded.progress();
            // 거절(예외)하면 귀가 기록까지 되돌려지므로, 만료는 예외가 아니라 결과로 알린다
            if (loaded.timedOut()) return view(user, progress, Result.ended(TIME_UP));
            if (!progress.isOnExpedition()) throw new CodedException("NOT_ON_EXPEDITION", "탑에 가 있지 않아요.");
            if (runId != null && !runId.isBlank() && !runId.equals(progress.getRunId())) {
                throw new CodedException("RUN_MISMATCH", "지금 원정과 맞지 않아요. 탑 화면을 다시 열어 주세요.");
            }
            // 방금 통과한 맵을 다시 보낸 요청(응답을 못 받고 재전송)은 멱등: 아무것도 바꾸지 않고 지금 상태를 돌려준다
            if (floor != null && map != null && progress.justCleared(floor, map)) {
                return view(user, progress, Result.NONE);
            }
            if ((floor != null && floor != progress.getCurrentFloor()) || (map != null && map != progress.getCurrentMap())) {
                throw new CodedException("MAP_MISMATCH", "지금 있는 맵과 맞지 않아요. 탑 화면을 다시 열어 주세요.");
            }

            Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId()).filter(p -> !p.isDead()).orElse(null);
            if (pet == null) { // 원정 중에 끄적이가 없어졌다 (새로 시작 등): 원정만 접는다
                progress.endExpedition();
                return view(user, progress, Result.ended(PET_UNAVAILABLE));
            }
            // 포만감 검사가 최소 시간보다 먼저다: 배고픈 사람을 기다리게 한 뒤에 돌려보내지 않는다
            if (pet.satiety() < TowerRules.MAP_SATIETY_COST) {
                // 가방에 도시락이 있으면 원정을 끝내지 않고 거절한다 (앱이 먹기를 권한다)
                if (bagCount(user) > 0) {
                    throw new CodedException("NOT_ENOUGH_SATIETY", "배가 고파요. 가방의 도시락을 먼저 먹어요.");
                }
                progress.endExpedition();
                return view(user, progress, Result.ended(SATIETY_EMPTY));
            }
            LocalDateTime now = LocalDateTime.now(clock);
            if (progress.secondsUntilClear(now, minMapSeconds) > 0) {
                throw new CodedException("MAP_TOO_SOON", "아직 이 맵을 다 돌지 못했어요.");
            }

            // 탑은 포만감을 SATIETY_FLOOR 아래로 내리지 않는다 (포만감이 딱 맵 값이면 1을 남긴다): 굶주림 타이머를 탑이 켜지 않게
            int spent = pet.spendSatiety(Math.min(TowerRules.MAP_SATIETY_COST, pet.satiety() - TowerRules.SATIETY_FLOOR));
            progress.addSpent(LocalDate.now(clock), spent);
            TowerProgress.Advance advance = progress.advance(now);

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

    /** 원정 id·층·맵 없이 통과를 알린다 (예전 앱과 같은 요청). 순서·최소 시간·포만감·층 상한은 똑같이 적용된다 */
    public View clearMap(User user) {
        return clearMap(user, null, null, null);
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
        LocalDateTime now = LocalDateTime.now(clock);
        TowerFloorCatalog.Floor floor = TowerFloorCatalog.floor(progress.getCurrentFloor()).orElse(null);
        MapMonstersView monsters = floor == null ? null : floor.map(progress.getCurrentMap())
                .map(m -> new MapMonstersView(m.normal(), m.swarm(), m.boss(), m.provisional())).orElse(null);
        return new View(progress.getHighestFloor(), progress.maxDepartFloor(), progress.getCurrentFloor(), progress.getCurrentMap(),
                TowerFloorCatalog.mapsPerFloor(progress.getCurrentFloor()), progress.isOnExpedition(), satiety,
                progress.spentToday(LocalDate.now(clock)), TowerRules.MAP_SATIETY_COST, bagCount(user), result,
                TowerFloorCatalog.openFloorCap(), progress.secondsLeft(now),
                progress.getRunId(), progress.secondsUntilClear(now, minMapSeconds),
                floor == null ? null : floor.faction(), floor == null ? null : floor.wheel(),
                floor == null ? List.of() : floor.rescueNpcIds(), monsters);
    }
}
