package com.tamagochi.tamagochi.service.farm;

import com.tamagochi.tamagochi.domain.farm.Farm;
import com.tamagochi.tamagochi.domain.farm.FarmCatalog;
import com.tamagochi.tamagochi.domain.farm.FarmCatalog.Crop;
import com.tamagochi.tamagochi.domain.farm.FarmCatalog.Quest;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.domain.yard.Yard;
import com.tamagochi.tamagochi.repository.farm.FarmRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.repository.yard.YardRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 끄적이 농장: 심기(잉크) → 자라기(물이 있으면 빠르게) → 거두기(열매, 같은 작물을 다시 심는다).
 * 열매는 마당(Yard)의 열매와 같은 주머니를 쓴다. 시간은 서버 시계로만 센다.
 */
@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farmRepository;
    private final YardRepository yardRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final Clock clock;

    public record PlotView(int index, boolean open, String crop, double progress, long remainingSeconds, boolean ripe) {}
    public record CropView(String id, long growSeconds, int fruit, int seedCost) {}
    public record QuestView(String id, int count, int need, boolean claimed, long waterMinutes, int fruit, boolean side) {}
    /** 이번 동작의 결과: 받은 열매, 쓴 잉크, 잉크가 모자라 다시 못 심은 칸 수 */
    public record Result(int fruit, int ink, int missed) {
        static final Result NONE = new Result(0, 0, 0);
    }
    public record View(List<PlotView> plots, List<CropView> crops, long waterSeconds, int waterSpeed, List<QuestView> quests,
                       boolean bonusReady, boolean bonusClaimed, int adsLeft, long adWaterMinutes, int unlockPrice,
                       int fruit, int balance, Result result) {}

    @Transactional
    public View get(User user) {
        Farm farm = farm(user);
        farm.settle(clock.instant());
        return view(user, farm, Result.NONE);
    }

    /** 빈 밭에 심거나, 자라는 밭의 작물을 바꾼다 (처음부터 다시 자란다). 씨앗값은 잉크 */
    @Transactional
    public View plant(User user, int plot, String cropId) {
        Crop crop = FarmCatalog.crop(cropId);
        Farm farm = farm(user);
        if (!farm.isOpen(plot)) throw new IllegalStateException("아직 열지 않은 밭이에요.");
        payInk(user, crop.seedCost(), seedKey(user, plot, farm.nextSeq(plot)));
        farm.plant(plot, cropId, clock.instant());
        return view(user, farm, new Result(0, crop.seedCost(), 0));
    }

    @Transactional
    public View clear(User user, int plot) {
        Farm farm = farm(user);
        farm.settle(clock.instant());
        farm.clear(plot);
        return view(user, farm, Result.NONE);
    }

    /** 다 자란 밭을 거둔다 (plot이 비어 있으면 다 자란 밭 모두). 거둔 칸은 씨앗값을 내고 같은 작물을 다시 심는다 */
    @Transactional
    public View harvest(User user, Integer plot) {
        Instant now = clock.instant();
        Farm farm = farm(user);
        farm.settle(now);
        List<Integer> targets = plot != null ? List.of(plot)
                : IntStream.range(0, FarmCatalog.PLOTS).filter(farm::ripe).boxed().toList();
        if (targets.isEmpty() || (plot != null && !farm.ripe(plot))) throw new IllegalStateException("아직 자라는 중이에요.");
        int fruit = 0, ink = 0, missed = 0;
        int balance = pointService.getWallet(user).getBalance();
        for (int i : targets) {
            Crop crop = FarmCatalog.crop(farm.plot(i).getCrop());
            // 잉크가 모자라면 결제를 시도하지 않는다 (실패한 결제는 트랜잭션 전체를 되돌리므로 미리 본다)
            boolean replant = balance - ink >= crop.seedCost();
            if (replant) {
                pointService.usePoint(user, crop.seedCost(), seedKey(user, i, farm.nextSeq(i)));
                ink += crop.seedCost();
            } else missed++;
            fruit += farm.harvest(i, replant, now);
        }
        yard(user).addFruit(fruit);
        return view(user, farm, new Result(fruit, ink, missed));
    }

    /** 잠긴 밭을 연다 (열매) */
    @Transactional
    public View unlock(User user, int plot) {
        Farm farm = farm(user);
        int price = farm.unlockPrice();
        if (price < 0) throw new IllegalStateException("밭을 다 열었어요.");
        yard(user).spendFruit(price);
        farm.unlock(plot);
        return view(user, farm, Result.NONE);
    }

    @Transactional
    public View claimQuest(User user, String questId) {
        Quest q = quest(questId);
        Farm farm = farm(user);
        int fruit = farm.claim(q, LocalDate.now(clock), clock.instant());
        if (fruit > 0) yard(user).addFruit(fruit);
        return view(user, farm, new Result(fruit, 0, 0));
    }

    @Transactional
    public View claimBonus(User user) {
        Farm farm = farm(user);
        farm.claimBonus(LocalDate.now(clock), clock.instant());
        return view(user, farm, Result.NONE);
    }

    /** 광고를 본 뒤 부른다 (광고 확인은 앱에서 한다. 하루 횟수로 막는다) */
    @Transactional
    public View adWater(User user) {
        Farm farm = farm(user);
        farm.adWater(LocalDate.now(clock), clock.instant());
        return view(user, farm, Result.NONE);
    }

    /** 다른 화면에서 한 행동을 오늘의 퀘스트에 센다. 실패해도 원래 동작을 막지 않게 조용히 넘어간다 */
    @Transactional
    public void progress(User user, Quest q, int n) {
        if (user == null || n <= 0) return;
        farm(user).progress(q, n, LocalDate.now(clock));
    }

    @Transactional
    public void progress(Long userId, Quest q, int n) {
        if (userId == null) return;
        userRepository.findById(userId).ifPresent(u -> progress(u, q, n));
    }

    private void payInk(User user, int amount, String key) {
        if (pointService.getWallet(user).getBalance() < amount) throw new IllegalStateException("잉크가 모자라요.");
        pointService.usePoint(user, amount, key);
    }

    private static String seedKey(User user, int plot, int seq) {
        return "farm-seed-" + user.getId() + "-" + plot + "-" + seq;
    }

    private static Quest quest(String id) {
        try {
            return Quest.valueOf(id == null ? "" : id.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("없는 퀘스트예요.");
        }
    }

    private Farm farm(User user) {
        return farmRepository.findByUserId(user.getId())
                .orElseGet(() -> farmRepository.save(new Farm(user, clock.instant())));
    }

    private Yard yard(User user) {
        return yardRepository.findByUserId(user.getId())
                .orElseGet(() -> yardRepository.save(new Yard(user, clock.instant())));
    }

    private View view(User user, Farm farm, Result result) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        List<PlotView> plots = new ArrayList<>();
        for (int i = 0; i < FarmCatalog.PLOTS; i++) {
            plots.add(new PlotView(i, farm.isOpen(i), farm.plot(i).getCrop(), farm.progress(i), farm.remainingSeconds(i, now), farm.ripe(i)));
        }
        List<CropView> crops = FarmCatalog.CROPS.values().stream()
                .map(c -> new CropView(c.id(), c.grow().toSeconds(), c.fruit(), c.seedCost())).toList();
        List<QuestView> quests = java.util.Arrays.stream(Quest.values()).filter(q -> farm.questActive(q, today))
                .map(q -> new QuestView(q.name().toLowerCase(), farm.questCount(q, today), q.need, farm.questClaimed(q, today),
                        q.water.toMinutes(), q.fruit, q.side)).toList();
        return new View(plots, crops, farm.waterSeconds(now), FarmCatalog.WATER_SPEED, quests, farm.bonusReady(today), farm.bonusClaimed(today),
                farm.adsLeft(today), FarmCatalog.AD_WATER.toMinutes(), farm.unlockPrice(), yard(user).getFruit(),
                pointService.getWallet(user).getBalance(), result);
    }
}
