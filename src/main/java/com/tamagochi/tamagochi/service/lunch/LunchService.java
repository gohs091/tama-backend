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
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 도시락: 열매로 만들어 찬장에 두고(cook), 집에서 가방에 챙겨(pack/unpack), 탑 원정 중에 먹는다(eat).
 * 열매는 마당(Yard)의 열매를 쓴다 (작물 구분 없음). 하루 만들기·먹기 상한은 없다.
 * 모든 동작은 한 트랜잭션이고, 같은 유저의 요청이 겹치면 다시 한다 (OptimisticRetry) — 열매·도시락이 두 번 빠지거나 늘지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LunchService {

    /** 한 번에 만들거나 옮길 수 있는 개수 (하루 상한이 아니라 요청 하나의 상한) */
    public static final int MAX_COUNT_PER_REQUEST = 99;

    private final PantryRepository pantryRepository;
    private final YardRepository yardRepository;
    private final TamagotchiRepository tamagotchiRepository;
    private final ExpeditionGuard expeditionGuard;
    private final OptimisticRetry retry;
    private final Clock clock;

    public record LunchView(String id, String emoji, String name, int fruitCost, int satiety, boolean locked,
                            LunchCatalog.Unlock unlock) {}

    /** 도시락 전체 상태. recovered는 이번에 먹어서 찬 포만감 (먹기가 아니면 0) */
    public record View(List<LunchView> catalog, Map<String, Integer> cupboard, Map<String, Integer> bag,
                       int bagUsed, int bagSlots, int fruit, int satiety, boolean onExpedition, int recovered) {}

    public View get(User user) {
        return retry.run(() -> view(user, pantry(user), 0));
    }

    /** 열매로 도시락을 만들어 찬장에 넣는다 */
    public View cook(User user, String lunchId, Integer count) {
        return retry.run(() -> {
            Lunchbox box = lunchbox(lunchId);
            int n = count(count);
            if (!unlocked(user, box)) throw new CodedException("LOCKED", "아직 만들 수 없는 도시락이에요.");
            Yard yard = yard(user);
            int cost = box.fruitCost() * n;
            if (yard.getFruit() < cost) throw new CodedException("NOT_ENOUGH_FRUIT", "열매가 모자라요.");
            yard.spendFruit(cost);
            Pantry pantry = pantry(user);
            pantry.addToCupboard(box.id(), n);
            return view(user, pantry, 0);
        });
    }

    /** 찬장 → 가방. 채비는 집에서만 한다 */
    public View pack(User user, String lunchId, Integer count) {
        return retry.run(() -> {
            Lunchbox box = lunchbox(lunchId);
            int n = count(count);
            requireHome(user);
            Pantry pantry = pantry(user);
            pantry.pack(box.id(), n, pantry.bagCapacity(bagBonus(user)));
            return view(user, pantry, 0);
        });
    }

    /** 가방 → 찬장. 채비는 집에서만 한다 */
    public View unpack(User user, String lunchId, Integer count) {
        return retry.run(() -> {
            Lunchbox box = lunchbox(lunchId);
            int n = count(count);
            requireHome(user);
            Pantry pantry = pantry(user);
            pantry.unpack(box.id(), n);
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
            pantry.takeFromBag(box.id());
            int recovered = pet.refill(box.satiety());
            return view(user, pantry, recovered);
        });
    }

    /** 로컬 개발 전용 (DevController): 열매 없이, 잠긴 도시락도 찬장에 넣는다 */
    public View devGive(User user, String lunchId, int count) {
        return retry.run(() -> {
            Lunchbox box = lunchbox(lunchId);
            Pantry pantry = pantry(user);
            pantry.addToCupboard(box.id(), count(count));
            return view(user, pantry, 0);
        });
    }

    /**
     * 이 도시락을 만들 수 있는가. 해금 조건(unlock)이 없는 도시락만 열려 있다.
     * 확장 지점: 해금 수단(NPC 구출 등)이 생기면 여기서 조건을 확인한다
     */
    private boolean unlocked(User user, Lunchbox box) {
        return box.unlock() == null;
    }

    /** 가방 칸 보너스. 확장 지점: NPC·가구가 칸을 늘리면 여기서 더한다 (상한은 Pantry.MAX_BAG_SLOTS) */
    private int bagBonus(User user) {
        return 0;
    }

    private void requireHome(User user) {
        if (onExpedition(user)) throw new CodedException("ON_EXPEDITION", "탑에 가 있는 동안에는 가방을 챙길 수 없어요.");
    }

    private boolean onExpedition(User user) {
        return expeditionGuard.onExpedition(user);
    }

    private static Lunchbox lunchbox(String id) {
        return LunchCatalog.find(id).orElseThrow(() -> new CodedException("UNKNOWN_LUNCHBOX", "없는 도시락이에요."));
    }

    private static int count(Integer count) {
        int n = count == null ? 1 : count;
        if (n < 1 || n > MAX_COUNT_PER_REQUEST) {
            throw new CodedException("INVALID_COUNT", "개수는 1~" + MAX_COUNT_PER_REQUEST + "개로 해 주세요.");
        }
        return n;
    }

    private Pantry pantry(User user) {
        return pantryRepository.findByUserId(user.getId())
                .orElseGet(() -> pantryRepository.save(new Pantry(user)));
    }

    private Yard yard(User user) {
        return yardRepository.findByUserId(user.getId())
                .orElseGet(() -> yardRepository.save(new Yard(user, clock.instant())));
    }

    private View view(User user, Pantry pantry, int recovered) {
        List<LunchView> catalog = LunchCatalog.all().stream()
                .map(b -> new LunchView(b.id(), b.emoji(), b.name(), b.fruitCost(), b.satiety(), !unlocked(user, b), b.unlock()))
                .toList();
        int satiety = tamagotchiRepository.findActiveByUserId(user.getId()).map(Tamagotchi::satiety).orElse(0);
        return new View(catalog, new TreeMap<>(pantry.getCupboard()), new TreeMap<>(pantry.getBag()),
                pantry.bagUsed(), pantry.bagCapacity(bagBonus(user)), yard(user).getFruit(), satiety, onExpedition(user), recovered);
    }
}
