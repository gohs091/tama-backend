package com.tamagochi.tamagochi.service.yard;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.domain.yard.Yard;
import com.tamagochi.tamagochi.domain.yard.YardCatalog;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.yard.YardRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 끄적이 마당: 화분 수확·바로 자라기, 소품 사기·놓기, 배경 사기·고르기. 시간은 서버 시계로만 센다 */
@Service
@RequiredArgsConstructor
public class YardService {

    private final YardRepository yardRepository;
    private final TamagotchiRepository tamagotchiRepository;
    private final PointService pointService;
    private final Clock clock;

    public record PropPrice(String id, int price) {}

    public record View(int fruit, boolean ripe, long remainingSeconds, long growSeconds, int harvestAmount, int boostsLeft,
                       Map<String, String> slots, Map<String, Integer> props, String background, String ownFaction,
                       List<String> ownedBackgrounds, int backgroundPrice, List<PropPrice> shop, int balance, int harvested,
                       List<YardCatalog.RoomItem> roomShop, Map<String, int[]> roomPos) {}

    @Transactional
    public View get(User user) {
        return view(user, yard(user), 0);
    }

    @Transactional
    public View harvest(User user) {
        Yard yard = yard(user);
        int got = yard.harvest(clock.instant());
        return view(user, yard, got);
    }

    /** 광고를 본 뒤 부른다 (광고 확인은 앱에서 한다. 하루 횟수로 막는다) */
    @Transactional
    public View boost(User user) {
        Yard yard = yard(user);
        yard.boost(LocalDate.now(clock), clock.instant());
        return view(user, yard, 0);
    }

    @Transactional
    public View buyProp(User user, String propId, String slot, Integer x, Integer y) {
        Yard yard = yard(user);
        // 자리를 함께 받으면 사자마자 그 자리에 놓는다 (방 꾸미기: 놓아 보고 사기). 못 놓는 자리면 사지도 않는다
        if (slot != null && !slot.isBlank()) YardCatalog.requireFit(slot, propId);
        yard.buyProp(propId);
        if (slot != null && !slot.isBlank()) {
            yard.place(slot, propId);
            if (x != null && y != null) yard.moveRoom(slot, x, y);
        }
        return view(user, yard, 0);
    }

    @Transactional
    public View moveRoom(User user, String slot, int x, int y) {
        Yard yard = yard(user);
        yard.moveRoom(slot, x, y);
        return view(user, yard, 0);
    }

    @Transactional
    public View place(User user, String slot, String propId, Integer x, Integer y) {
        Yard yard = yard(user);
        yard.place(slot, propId);
        if (propId != null && !propId.isBlank() && x != null && y != null) yard.moveRoom(slot, x, y);
        return view(user, yard, 0);
    }

    @Transactional
    public View buyBackground(User user, String background) {
        YardCatalog.requireBackground(background);
        Yard yard = yard(user);
        String own = ownFaction(user);
        if (yard.ownsBackground(background, own)) throw new IllegalStateException("이미 갖고 있는 배경이에요.");
        // 배경마다 고정 키라서 같은 배경을 두 번 사도 잉크는 한 번만 빠진다
        pointService.usePoint(user, YardCatalog.BACKGROUND_PRICE, "yard-bg-" + user.getId() + "-" + background);
        yard.addBackground(background);
        yard.useBackground(background, own);
        return view(user, yard, 0);
    }

    @Transactional
    public View useBackground(User user, String background) {
        Yard yard = yard(user);
        yard.useBackground(background, ownFaction(user));
        return view(user, yard, 0);
    }

    /** 로컬 개발 전용 (DevController) */
    @Transactional
    public View devAddFruit(User user, int amount) {
        Yard yard = yard(user);
        yard.addFruit(amount);
        return view(user, yard, 0);
    }

    private Yard yard(User user) {
        return yardRepository.findByUserId(user.getId())
                .orElseGet(() -> yardRepository.save(new Yard(user, clock.instant())));
    }

    private String ownFaction(User user) {
        return tamagotchiRepository.findActiveByUserId(user.getId()).map(Tamagotchi::getFaction).orElse(null);
    }

    private static Map<String, int[]> roomPos(Yard yard) {
        Map<String, int[]> out = new TreeMap<>();
        yard.getRoomPos().forEach((slot, pos) -> {
            String[] p = pos.split(",");
            try { out.put(slot, new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])}); } catch (RuntimeException ignored) { /* 깨진 값은 기본 자리로 */ }
        });
        return out;
    }

    private View view(User user, Yard yard, int harvested) {
        Instant now = clock.instant();
        List<PropPrice> shop = YardCatalog.PROPS.entrySet().stream().map(e -> new PropPrice(e.getKey(), e.getValue())).toList();
        List<String> owned = YardCatalog.BACKGROUNDS.stream().filter(yard.getBackgrounds()::contains).toList();
        return new View(yard.getFruit(), yard.ripe(now), yard.remaining(now).toSeconds(), YardCatalog.GROW.toSeconds(),
                YardCatalog.HARVEST, yard.boostsLeft(LocalDate.now(clock)),
                new TreeMap<>(yard.getSlots()), new TreeMap<>(yard.getProps()), yard.getBackground(), ownFaction(user),
                owned, YardCatalog.BACKGROUND_PRICE, shop, pointService.getWallet(user).getBalance(), harvested,
                List.copyOf(YardCatalog.ROOM_ITEMS.values()), roomPos(yard));
    }
}
