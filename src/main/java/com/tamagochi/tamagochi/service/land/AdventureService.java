package com.tamagochi.tamagochi.service.land;

import com.tamagochi.tamagochi.domain.land.Adventure;
import com.tamagochi.tamagochi.domain.land.AdventureRules;
import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.land.LandInk;
import com.tamagochi.tamagochi.domain.land.LandProgress;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.land.AdventureRepository;
import com.tamagochi.tamagochi.repository.land.LandInkRepository;
import com.tamagochi.tamagochi.repository.land.LandProgressRepository;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 끄적랜드 모험. 잉크통처럼 서버 시계로 시간을 재고, 앱을 닫아도 이어진다.
 * 모험 중에는 잉크를 바로 빼지 않고 지금까지의 소모·줍기를 계산해 보여 주다가, 끝날 때 한 번에 정산한다.
 * 잉크 발견은 정해진 탐험 시간에 일어나며, 발견하는 즉시 보관함에 넣는다.
 */
@Service
@RequiredArgsConstructor
public class AdventureService {

    private final AdventureRepository adventureRepository;
    private final LandProgressRepository progressRepository;
    private final LandInkRepository inkRepository;
    private final PointWalletRepository walletRepository;
    private final PointService pointService;
    private final TamagotchiRepository tamagotchiRepository;
    private final Clock clock;

    public record InkView(String id, String emoji, String name, String grade, String family, String familyName) {
        public static InkView of(LandCatalog.Ink ink) {
            String familyName = LandCatalog.family(ink.family()).map(LandCatalog.Family::name).orElse(null);
            return new InkView(ink.id(), ink.emoji(), ink.name(), ink.grade().name(), ink.family(), familyName);
        }
    }

    /** 이번 모험에서 찾은 잉크와, 시작 후 몇 초에 찾았는지 (화면에서 발견 알림을 띄울 때 쓴다) */
    public record Found(InkView ink, int atSeconds) {}

    /**
     * 모험 상태. 모험 중이면 지금까지의 시간·소모·줍기와 이번에 찾은 잉크를, 끝났으면 마지막 결과를 담는다.
     * balance는 이번 모험의 소모·줍기까지 반영한 잔액이다. endedNow는 이번 호출에서 끝나 정산했는지다.
     */
    public record Status(boolean running, String regionId, String regionName, int elapsedSeconds, int maxMinutes,
                         int spentInk, int pickedInk, int balance, int costPerMinute,
                         int progressPercent, int foundCount, int totalCount, List<Found> found,
                         boolean endedNow, String endReason, int lastMinutes, int lastNetInk) {}

    @Transactional
    public Status status(User user) {
        Adventure adventure = adventureRepository.findByUserId(user.getId()).orElse(null);
        if (adventure == null) return idle(user, null, false);
        if (!adventure.isRunning()) return idle(user, adventure, false);

        Live live = live(user, adventure);
        if (live.progress.ended() != null) {
            finish(user, adventure, live, live.progress.ended());
            return idle(user, adventure, true);
        }
        return running(user, adventure, live);
    }

    @Transactional
    public Status start(User user, String regionId) {
        Status current = status(user);
        if (current.running()) throw new IllegalStateException("이미 모험 중이에요.");

        LandCatalog.Region region = LandCatalog.region(regionId)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 구역이에요."));
        if (!region.open()) throw new IllegalStateException("아직 준비 중인 구역이에요.");

        Tamagotchi pet = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("끄적이가 없어요."));
        if (pet.isDead()) throw new IllegalStateException("끄적이가 죽어서 모험을 떠날 수 없어요.");
        if (pet.getEvolutionStage() == EvolutionStage.EGG) throw new IllegalStateException("알이 깨어나면 모험을 떠날 수 있어요.");
        if (pet.isSleeping()) throw new IllegalStateException("자고 있을 땐 모험을 떠날 수 없어요.");
        PointWallet wallet = wallet(user);
        if (pet.isBathing() || wallet.bathRunning()) throw new IllegalStateException("잉크통에 들어가 있을 땐 모험을 떠날 수 없어요.");

        LandProgress progress = progress(user, regionId);
        if (progress.getExploredMinutes() >= region.totalMinutes()) throw new IllegalStateException("이미 다 탐험한 구역이에요.");
        if (!AdventureRules.canAfford(wallet.getBalance(), progress.getExploredMinutes())) {
            throw new IllegalStateException("잉크가 부족해요.");
        }

        Adventure adventure = adventureRepository.findByUserId(user.getId())
                .orElseGet(() -> adventureRepository.save(new Adventure(user)));
        adventure.start(regionId, clock.instant(), LocalDate.now(clock), progress.getExploredMinutes());
        pet.setAdventuring(true);
        return running(user, adventure, live(user, adventure));
    }

    /** 멈추기. 지금까지 다 채운 분만큼 탐험을 저장하고 잉크를 정산한다 */
    @Transactional
    public Status stop(User user) {
        Adventure adventure = adventureRepository.findByUserId(user.getId()).orElse(null);
        if (adventure == null || !adventure.isRunning()) return status(user);
        Live live = live(user, adventure);
        finish(user, adventure, live, live.progress.ended() != null ? live.progress.ended() : AdventureRules.EndReason.STOPPED);
        return idle(user, adventure, true);
    }

    /** 지금 시각 기준으로 계산한 이번 모험 (아직 저장하지 않은 값) */
    private record Live(int seconds, AdventureRules.Progress progress, int spent, int picked, int balanceBefore) {
        int net() { return picked - spent; }
    }

    private Live live(User user, Adventure adventure) {
        LandCatalog.Region region = LandCatalog.region(adventure.getRegionId()).orElseThrow();
        int elapsed = (int) Math.max(0, Duration.between(adventure.getStartedAt(), Instant.now(clock)).getSeconds());
        int balance = wallet(user).getBalance();
        AdventureRules.Progress progress = AdventureRules.progress(elapsed, adventure.getExploredAtStart(),
                region.totalMinutes(), adventure.isBonus(), balance);
        // 끝났으면 끝난 시점까지만, 아니면 지금까지(줍기는 초 단위, 소모는 다 채운 분 단위)
        int seconds = progress.ended() != null ? progress.minutes() * 60 : elapsed;
        int spent = AdventureRules.spentInk(progress.minutes(), adventure.getExploredAtStart());
        int picked = AdventureRules.pickedInk(seconds, adventure.isBonus());
        discover(user, region, adventure.getExploredAtStart() + progress.minutes());
        return new Live(seconds, progress, spent, picked, balance);
    }

    private void finish(User user, Adventure adventure, Live live, AdventureRules.EndReason reason) {
        int net = live.net();
        // 같은 모험을 두 번 정산하지 않도록 모험마다 고정 키를 쓴다
        String key = "adventure-" + user.getId() + "-" + adventure.getStartedAt().toEpochMilli();
        if (net > 0) pointService.earnPoint(user, net, key);
        if (net < 0) pointService.usePoint(user, -net, key);
        progress(user, adventure.getRegionId()).explore(live.progress.minutes());
        adventure.finish(live.progress.minutes(), net, reason);
        tamagotchiRepository.findByUserId(user.getId()).ifPresent(p -> p.setAdventuring(false));
    }

    /** 누적 탐험 시간까지 발견된 잉크 중 보관함에 없는 것을 넣는다 */
    private void discover(User user, LandCatalog.Region region, int exploredMinutes) {
        Set<String> owned = ownedInkIds(user);
        Instant now = clock.instant();
        for (int i = 0; i < region.inks().size(); i++) {
            if (region.discoverAt().get(i) > exploredMinutes) break;
            String id = region.inks().get(i).id();
            if (owned.add(id)) inkRepository.save(new LandInk(user, id, now));
        }
    }

    private Status running(User user, Adventure adventure, Live live) {
        LandCatalog.Region region = LandCatalog.region(adventure.getRegionId()).orElseThrow();
        int explored = adventure.getExploredAtStart() + live.progress.minutes();
        List<Found> found = new ArrayList<>();
        for (int i = 0; i < region.inks().size(); i++) {
            int at = region.discoverAt().get(i);
            if (at > explored) break;
            if (at > adventure.getExploredAtStart()) {
                found.add(new Found(InkView.of(region.inks().get(i)), (at - adventure.getExploredAtStart()) * 60));
            }
        }
        return new Status(true, region.id(), region.name(), live.seconds, AdventureRules.MAX_MINUTES,
                live.spent, live.picked, live.balanceBefore + live.net(), LandCatalog.costPerMinute(explored),
                percent(explored, region.totalMinutes()), foundCount(user, region), region.inks().size(), found,
                false, null, 0, 0);
    }

    private Status idle(User user, Adventure adventure, boolean endedNow) {
        int balance = wallet(user).getBalance();
        if (adventure == null || adventure.getRegionId() == null) {
            return new Status(false, null, null, 0, AdventureRules.MAX_MINUTES, 0, 0, balance, 0, 0, 0, 0,
                    List.of(), false, null, 0, 0);
        }
        LandCatalog.Region region = LandCatalog.region(adventure.getRegionId()).orElseThrow();
        int explored = progress(user, region.id()).getExploredMinutes();
        return new Status(false, region.id(), region.name(), 0, AdventureRules.MAX_MINUTES, 0, 0, balance,
                LandCatalog.costPerMinute(explored), percent(explored, region.totalMinutes()),
                foundCount(user, region), region.inks().size(), List.of(), endedNow,
                adventure.getLastEndReason() == null ? null : adventure.getLastEndReason().name(),
                adventure.getLastMinutes(), adventure.getLastNetInk());
    }

    static int percent(int explored, int total) {
        return total <= 0 ? 0 : Math.min(100, explored * 100 / total);
    }

    private int foundCount(User user, LandCatalog.Region region) {
        Set<String> owned = ownedInkIds(user);
        return (int) region.inks().stream().filter(i -> owned.contains(i.id())).count();
    }

    private Set<String> ownedInkIds(User user) {
        return inkRepository.findByUserIdOrderByFoundAtAsc(user.getId()).stream()
                .map(LandInk::getInkId).collect(Collectors.toSet());
    }

    private LandProgress progress(User user, String regionId) {
        return progressRepository.findByUserIdAndRegionId(user.getId(), regionId)
                .orElseGet(() -> progressRepository.save(new LandProgress(user, regionId)));
    }

    private PointWallet wallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("지갑이 존재하지 않습니다."));
    }
}
