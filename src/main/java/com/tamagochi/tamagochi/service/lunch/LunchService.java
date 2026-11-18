package com.tamagochi.tamagochi.service.lunch;

import com.tamagochi.tamagochi.domain.lunch.LunchCatalog;
import com.tamagochi.tamagochi.domain.lunch.LunchCatalog.Lunchbox;
import com.tamagochi.tamagochi.domain.lunch.Pantry;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.domain.yard.Yard;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.lunch.PantryRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.yard.YardRepository;
import com.tamagochi.tamagochi.service.common.OptimisticRetry;
import com.tamagochi.tamagochi.service.tower.ExpeditionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 도시락: 열매로 만들어 찬장에 두고(cook), 집에서 가방에 챙겨(pack/unpack), 탑 원정 중에 먹는다(eat).
 * 도시락은 한 종류뿐이다 (LunchCatalog). 요청의 id는 비어 있어도, 예전 세 종류 id(riceball·hearty·feast)여도 그 도시락으로 본다.
 * 예전 종류로 저장돼 있던 찬장·가방은 읽을 때(pantry) 지금 도시락 개수로 한 번 바꾼다. 서버가 뜰 때도 한 번 훑는다 (LegacyLunchMigration).
 * 열매는 마당(Yard)의 열매를 쓴다 (작물 구분 없음). 하루 만들기·먹기 상한은 없다.
 * 햄스터를 구하기 전에는 한 번에 하나씩만 만들고, 구한 뒤에는 한 번에 여러 개를 만들 수 있고 가방도 한 칸 늘어난다 (HamsterReward).
 * 모든 동작은 한 트랜잭션이고, 같은 유저의 요청이 겹치면 다시 한다 (OptimisticRetry) — 열매·도시락이 두 번 빠지거나 늘지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LunchService {

    /** 한 번에 만들거나 옮길 수 있는 개수 (하루 상한이 아니라 요청 하나의 상한) */
    public static final int MAX_COUNT_PER_REQUEST = 99;

    private final PantryRepository pantryRepository;
    private final YardRepository yardRepository;
    private final TamagotchiRepository tamagotchiRepository;
    private final ExpeditionGuard expeditionGuard;
    private final HamsterReward hamsterReward;
    private final OptimisticRetry retry;
    private final Clock clock;

    /** locked·unlock은 예전 앱이 읽는 자리라 남겨 둔다. 지금은 잠긴 도시락이 없어 locked=false, unlock=null이다 */
    public record LunchView(String id, String emoji, String name, int fruitCost, int satiety, boolean locked,
                            LunchCatalog.Unlock unlock) {}

    /**
     * 도시락 전체 상태. recovered는 이번에 먹어서 찬 포만감 (먹기가 아니면 0).
     * catalog는 도시락 하나, cupboard·bag은 예전 모양 그대로 { id: 개수 } (키는 하나뿐인 도시락 id, 0개면 빈 맵).
     * cupboardCount·bagCount는 같은 개수를 숫자로 준 것이다 (bagCount = bagUsed).
     * batchCooking은 한 번에 여러 개 만들기가 열렸는가 (햄스터를 구한 뒤 true)
     */
    public record View(List<LunchView> catalog, Map<String, Integer> cupboard, Map<String, Integer> bag,
                       int bagUsed, int bagSlots, int fruit, int satiety, boolean onExpedition, int recovered,
                       int cupboardCount, int bagCount, boolean batchCooking) {}

    public View get(User user) {
        return retry.run(() -> view(user, pantry(user), 0));
    }

    /**
     * 열매로 도시락을 만들어 찬장에 넣는다.
     * 햄스터를 구하기 전에는 개수를 여러 개로 보내도 거절하지 않고 하나만 만든다 (열매도 하나 몫만 뺀다)
     */
    public View cook(User user, String lunchId, Integer count) {
        return retry.run(() -> {
            Lunchbox box = lunchbox(lunchId);
            int n = count(count);
            if (!batchCooking(user)) n = 1;
            if (!unlocked(user, box)) throw new CodedException("LOCKED", "아직 만들 수 없는 도시락이에요.");
            Yard yard = yard(user);
            int cost = box.fruitCost() * n;
            if (yard.getFruit() < cost) throw new CodedException("NOT_ENOUGH_FRUIT", "열매가 모자라요.");
            yard.spendFruit(cost);
            Pantry pantry = pantry(user);
            pantry.addToCupboard(n);
            return view(user, pantry, 0);
        });
    }

    /** 찬장 → 가방. 채비는 집에서만 한다 */
    public View pack(User user, String lunchId, Integer count) {
        return retry.run(() -> {
            lunchbox(lunchId);
            int n = count(count);
            requireHome(user);
            Pantry pantry = pantry(user);
            pantry.pack(n, pantry.bagCapacity(bagBonus(user)));
            return view(user, pantry, 0);
        });
    }

    /** 가방 → 찬장. 채비는 집에서만 한다 */
    public View unpack(User user, String lunchId, Integer count) {
        return retry.run(() -> {
            lunchbox(lunchId);
            int n = count(count);
            requireHome(user);
            Pantry pantry = pantry(user);
            pantry.unpack(n);
            return view(user, pantry, 0);
        });
    }

    /** 탑 원정 중에 가방의 도시락 하나를 먹는다. 포만감은 100까지만 찬다 */
    public View eat(User user, String lunchId) {
        return retry.run(() -> {
            Lunchbox box = lunchbox(lunchId);
            if (!onExpedition(user)) throw new CodedException("NOT_ON_EXPEDITION", "도시락은 탑에서만 먹을 수 있어요.");
            Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId())
                    .filter(p -> !p.isDead())
                    .orElseThrow(() -> new CodedException("NO_PET", "끄적이가 없어요."));
            if (pet.isFull()) throw new CodedException("FULL", "배가 불러서 더 먹을 수 없어요.");
            Pantry pantry = pantry(user);
            pantry.takeFromBag();
            int recovered = pet.refill(box.satiety());
            return view(user, pantry, recovered);
        });
    }

    /** 로컬 개발 전용 (DevController): 열매 없이 도시락을 찬장에 넣는다 */
    public View devGive(User user, String lunchId, int count) {
        return retry.run(() -> {
            lunchbox(lunchId);
            Pantry pantry = pantry(user);
            pantry.addToCupboard(count(count));
            return view(user, pantry, 0);
        });
    }

    /**
     * 예전 종류로 저장된 찬장·가방을 모두 지금 도시락 개수로 바꾼다 (앱을 열지 않은 사람 몫). 바꾼 찬장 수를 돌려준다.
     * 한 사람씩 따로 트랜잭션이라 하나가 실패해도 나머지는 바뀌고, 실패한 것은 그 사람이 다음에 읽을 때 바뀐다.
     * 이미 바뀐 것은 건드리지 않으니 여러 번 불러도 된다
     */
    public int migrateLegacyPantries() {
        List<Long> ids = pantryRepository.findAll().stream().filter(Pantry::hasLegacy).map(Pantry::getId).toList();
        int migrated = 0;
        for (Long id : ids) {
            try {
                boolean done = retry.run(() -> pantryRepository.findById(id).map(p -> migrate(p, p.getUser())).orElse(false));
                if (done) migrated++;
            } catch (RuntimeException e) {
                log.warn("예전 도시락을 바꾸지 못했어요 (다음에 읽을 때 다시 해요): pantry={}", id, e);
            }
        }
        return migrated;
    }

    /**
     * 이 도시락을 만들 수 있는가. 지금은 잠긴 도시락이 없어 늘 true다.
     * 확장 지점: 해금 조건이 다시 생기면 여기서 확인한다 (cook의 LOCKED 거절과 목록의 locked가 이 값을 따른다)
     */
    private boolean unlocked(User user, Lunchbox box) {
        return true;
    }

    /** 한 번에 여러 개 만들기가 열렸는가: 햄스터를 구한 뒤에 열린다 (도시락 자체는 잠그지 않는다) */
    private boolean batchCooking(User user) {
        return hamsterReward.rescued(user);
    }

    /** 가방 용량 보너스: 햄스터를 구했으면 한 칸. 확장 지점: 가구 등이 용량을 늘리면 여기서 더한다 (상한은 LunchCatalog.maxBagSlots) */
    private int bagBonus(User user) {
        return hamsterReward.rescued(user) ? HamsterReward.BAG_BONUS : 0;
    }

    /** 예전 종류가 남아 있으면 지금 도시락 개수로 바꾼다. 바꿨으면 true */
    private boolean migrate(Pantry pantry, User user) {
        return pantry.migrateLegacy(pantry.bagCapacity(bagBonus(user)));
    }

    private void requireHome(User user) {
        if (onExpedition(user)) throw new CodedException("ON_EXPEDITION", "탑에 가 있는 동안에는 가방을 챙길 수 없어요.");
    }

    private boolean onExpedition(User user) {
        return expeditionGuard.onExpedition(user);
    }

    /** id가 비었거나 예전 id여도 하나뿐인 도시락이다. 전혀 모르는 id만 거절한다 */
    private static Lunchbox lunchbox(String id) {
        return LunchCatalog.resolve(id).orElseThrow(() -> new CodedException("UNKNOWN_LUNCHBOX", "없는 도시락이에요."));
    }

    private static int count(Integer count) {
        int n = count == null ? 1 : count;
        if (n < 1 || n > MAX_COUNT_PER_REQUEST) {
            throw new CodedException("INVALID_COUNT", "개수는 1~" + MAX_COUNT_PER_REQUEST + "개로 해 주세요.");
        }
        return n;
    }

    /** 찬장을 불러온다 (없으면 만든다). 예전 종류가 남아 있으면 여기서 바꾼 뒤에 쓴다 */
    private Pantry pantry(User user) {
        Pantry pantry = pantryRepository.findByUserId(user.getId())
                .orElseGet(() -> pantryRepository.save(new Pantry(user)));
        migrate(pantry, user);
        return pantry;
    }

    private Yard yard(User user) {
        return yardRepository.findByUserId(user.getId())
                .orElseGet(() -> yardRepository.save(new Yard(user, clock.instant())));
    }

    private View view(User user, Pantry pantry, int recovered) {
        List<LunchView> catalog = LunchCatalog.all().stream()
                .map(b -> new LunchView(b.id(), b.emoji(), b.name(), b.fruitCost(), b.satiety(), !unlocked(user, b), null))
                .toList();
        int satiety = tamagotchiRepository.findActiveByUserId(user.getId()).map(Tamagotchi::satiety).orElse(0);
        return new View(catalog, new TreeMap<>(pantry.getCupboard()), new TreeMap<>(pantry.getBag()),
                pantry.bagUsed(), pantry.bagCapacity(bagBonus(user)), yard(user).getFruit(), satiety, onExpedition(user), recovered,
                pantry.cupboardCount(), pantry.bagUsed(), batchCooking(user));
    }
}
