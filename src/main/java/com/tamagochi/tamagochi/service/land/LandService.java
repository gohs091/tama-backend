package com.tamagochi.tamagochi.service.land;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.land.LandInk;
import com.tamagochi.tamagochi.domain.land.LandProgress;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.land.LandInkRepository;
import com.tamagochi.tamagochi.repository.land.LandProgressRepository;
import com.tamagochi.tamagochi.service.land.AdventureService.InkView;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 끄적랜드 지도·구역·보관함 조회. 아직 못 찾은 잉크는 개수만 알려 주고 이름·발견 시간은 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LandService {

    private final LandProgressRepository progressRepository;
    private final LandInkRepository inkRepository;

    public record RegionView(String id, String emoji, String name, boolean open, int progressPercent,
                             int foundCount, int totalCount, int costPerMinute) {}

    public record RegionDetail(RegionView region, List<InkView> found, int unknownCount) {}

    public record OwnedInk(InkView ink, List<String> regionIds, Instant foundAt) {}

    @Transactional
    public List<RegionView> regions(User user) {
        Map<String, Integer> explored = explored(user);
        Set<String> owned = owned(user).keySet();
        return LandCatalog.regions().stream().map(r -> view(r, explored, owned)).toList();
    }

    @Transactional
    public RegionDetail region(User user, String regionId) {
        LandCatalog.Region region = LandCatalog.region(regionId)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 구역이에요."));
        Set<String> owned = owned(user).keySet();
        List<InkView> found = region.inks().stream().filter(i -> owned.contains(i.id())).map(InkView::of).toList();
        return new RegionDetail(view(region, explored(user), owned), found, region.inks().size() - found.size());
    }

    /** 보관함: 찾은 순서대로 */
    @Transactional
    public List<OwnedInk> inventory(User user) {
        return inkRepository.findByUserIdOrderByFoundAtAsc(user.getId()).stream()
                .flatMap(o -> LandCatalog.ink(o.getInkId()).stream()
                        .map(ink -> new OwnedInk(InkView.of(ink), regionsOf(ink.id()), o.getFoundAt())))
                .toList();
    }

    private static RegionView view(LandCatalog.Region r, Map<String, Integer> explored, Set<String> owned) {
        int minutes = explored.getOrDefault(r.id(), 0);
        int found = (int) r.inks().stream().filter(i -> owned.contains(i.id())).count();
        return new RegionView(r.id(), r.emoji(), r.name(), r.open(), AdventureService.percent(minutes, r.totalMinutes()),
                found, r.inks().size(), r.open() ? LandCatalog.costPerMinute(minutes) : 0);
    }

    private static List<String> regionsOf(String inkId) {
        return LandCatalog.regions().stream()
                .filter(r -> r.inks().stream().anyMatch(i -> i.id().equals(inkId)))
                .map(LandCatalog.Region::id).toList();
    }

    private Map<String, Integer> explored(User user) {
        return progressRepository.findByUserId(user.getId()).stream()
                .collect(Collectors.toMap(LandProgress::getRegionId, LandProgress::getExploredMinutes, Integer::sum));
    }

    private Map<String, Instant> owned(User user) {
        return inkRepository.findByUserIdOrderByFoundAtAsc(user.getId()).stream()
                .collect(Collectors.toMap(LandInk::getInkId, LandInk::getFoundAt, (a, b) -> a));
    }
}
