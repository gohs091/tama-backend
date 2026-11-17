package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.domain.lunch.Pantry;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import com.tamagochi.tamagochi.domain.tower.TowerRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.lunch.PantryRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.tower.TowerProgressRepository;
import com.tamagochi.tamagochi.service.common.OptimisticRetry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 탑 원정의 서버 기록 (최소형): 출발 → 맵 통과 → 귀환. 포만감 소모와 맵·층 전진은 서버가 계산한다 (앱은 "통과했다"만 알린다).
 * 시간 제한은 하루 포만감뿐이다: 하루 출발 횟수·새 층 돌파 횟수 제한이 없다. 패배도 없다 — 포만감이 모자라고
 * 가방에 도시락도 없으면 집으로 돌아올 뿐이다 (도시락이 남아 있으면 원정은 이어지고, 먹어야 다음 맵을 통과할 수 있다).
 * 보상 지급·무작위는 없다. 포만감은 끄적이의 hunger를 그대로 쓴다 (포만감 = 100 − hunger).
 */
@Service
@RequiredArgsConstructor
public class TowerService {

    /** 원정이 끝난 이유 */
    public static final String SATIETY_EMPTY = "SATIETY_EMPTY";     // 포만감이 모자라고 가방에 도시락도 없어 더 갈 수 없다
    public static final String PET_UNAVAILABLE = "PET_UNAVAILABLE"; // 원정을 이어 갈 끄적이가 없다
    public static final String RETURNED = "RETURNED";               // 스스로 돌아왔다

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

    /** 탑 전체 상태. satiety는 남은 포만감, spentToday는 오늘(KST) 탑에서 쓴 포만감, bagCount는 가방에 든 도시락 수 */
    public record View(int highestFloor, int maxDepartFloor, int currentFloor, int currentMap, int mapsPerFloor,
                       boolean onExpedition, int satiety, int spentToday, int mapSatietyCost, int bagCount, Result result) {}

    public View get(User user) {
        return retry.run(() -> view(user, progress(user), Result.NONE));
    }

    /**
     * 원정을 떠난다 (집에서만). floor를 비우면 돌파한 층 바로 위에서 시작한다. 갈 수 있는 층은 1 ~ 돌파한 층 + 1.
     * 포만감이 맵 1장 값보다 적어도 가방에 도시락이 있으면 떠날 수 있다 (도시락은 탑에서만 먹을 수 있다)
     */
    public View depart(User user, Integer floor) {
        return retry.run(() -> {
            TowerProgress progress = progress(user);
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
            progress.depart(target);
            return view(user, progress, Result.NONE);
        });
    }

    /**
     * 지금 맵을 통과했다고 기록한다: 포만감을 쓰고 다음 맵(층의 마지막 맵이면 다음 층 첫 맵)으로 간다.
     * 포만감이 맵 1장 값보다 적으면 통과시키지 않는다:
     * 가방에 도시락이 있으면 원정은 그대로 두고 거절하고 (NOT_ENOUGH_SATIETY — 도시락을 먹고 다시 부른다),
     * 가방이 비었으면 원정을 끝낸다 (SATIETY_EMPTY).
     * 통과한 뒤 포만감이 맵 1장 값보다 적고 가방에 도시락도 없으면 더 갈 수 없으니 원정이 끝난다.
     * TODO(H-1): 전투 검증 추가 — 지금은 앱이 통과했다고 알리면 그대로 믿는다
     */
    public View clearMap(User user) {
        return retry.run(() -> {
            TowerProgress progress = progress(user);
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

            boolean ended = pet.satiety() < TowerRules.MAP_SATIETY_COST && bagCount(user) == 0;
            if (ended) progress.endExpedition();
            return view(user, progress, new Result(true, spent, advance.floorCleared(), advance.firstClear(),
                    ended, ended ? SATIETY_EMPTY : null));
        });
    }

    /** 집으로 돌아온다. 가방에 남은 도시락은 가방에 그대로 있다 */
    public View returnHome(User user) {
        return retry.run(() -> {
            TowerProgress progress = progress(user);
            if (!progress.isOnExpedition()) return view(user, progress, Result.NONE);
            progress.endExpedition();
            return view(user, progress, Result.ended(RETURNED));
        });
    }

    private int bagCount(User user) {
        return pantryRepository.findByUserId(user.getId()).map(Pantry::bagUsed).orElse(0);
    }

    private TowerProgress progress(User user) {
        return progressRepository.findByUserId(user.getId())
                .orElseGet(() -> progressRepository.save(new TowerProgress(user)));
    }

    private View view(User user, TowerProgress progress, Result result) {
        int satiety = tamagotchiRepository.findActiveByUserId(user.getId()).map(Tamagotchi::satiety).orElse(0);
        return new View(progress.getHighestFloor(), progress.maxDepartFloor(), progress.getCurrentFloor(), progress.getCurrentMap(),
                TowerRules.MAPS_PER_FLOOR, progress.isOnExpedition(), satiety, progress.spentToday(LocalDate.now(clock)),
                TowerRules.MAP_SATIETY_COST, bagCount(user), result);
    }
}
