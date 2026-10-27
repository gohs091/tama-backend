package com.tamagochi.tamagochi.service.land;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.land.LandInk;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.land.LandInkRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.land.AdventureService.InkView;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 다음 진화에 반영할 대표 잉크: 특정 잉크 최대 1개 + 계열 잉크 최대 2개. 모두 비워 둬도 진화한다.
 * 클라이언트는 잉크 ID만 보내고, 서버가 보유 여부를 확인한 뒤 고정 문구로 프롬프트에 넣는다.
 */
@Service
@RequiredArgsConstructor
public class InkLoadoutService {

    public static final int MAX_FAMILY = 2;

    private final TamagotchiRepository tamagotchiRepository;
    private final LandInkRepository inkRepository;

    public record Loadout(InkView main, List<InkView> families) {}

    @Transactional
    public Loadout get(User user) {
        return view(pet(user));
    }

    @Transactional
    public Loadout set(User user, String mainId, List<String> familyIds) {
        Tamagotchi pet = pet(user);
        if (pet.getEvolutionStage() != EvolutionStage.BABY && pet.getEvolutionStage() != EvolutionStage.CHILD) {
            throw new IllegalStateException("진화할 수 있는 단계에서만 고를 수 있어요.");
        }
        Set<String> owned = inkRepository.findByUserIdOrderByFoundAtAsc(user.getId()).stream()
                .map(LandInk::getInkId).collect(Collectors.toSet());

        String main = blankToNull(mainId);
        if (main != null) {
            LandCatalog.Ink ink = LandCatalog.ink(main).orElseThrow(() -> new IllegalArgumentException("알 수 없는 잉크예요."));
            if (ink.grade() == LandCatalog.Grade.FAMILY) throw new IllegalArgumentException("계열 잉크는 계열 칸에 넣어 주세요.");
            if (!owned.contains(main)) throw new IllegalStateException("아직 찾지 못한 잉크예요.");
        }
        List<String> families = new ArrayList<>();
        for (String id : familyIds == null ? List.<String>of() : familyIds) {
            String f = blankToNull(id);
            if (f == null || families.contains(f)) continue;
            LandCatalog.Ink ink = LandCatalog.ink(f).orElseThrow(() -> new IllegalArgumentException("알 수 없는 잉크예요."));
            if (ink.grade() != LandCatalog.Grade.FAMILY) throw new IllegalArgumentException("특정 잉크는 특정 칸에 넣어 주세요.");
            if (!owned.contains(f)) throw new IllegalStateException("아직 찾지 못한 잉크예요.");
            families.add(f);
        }
        if (families.size() > MAX_FAMILY) throw new IllegalArgumentException("계열 잉크는 2개까지 고를 수 있어요.");

        pet.setInkLoadout(main, families.size() > 0 ? families.get(0) : null, families.size() > 1 ? families.get(1) : null);
        return view(pet);
    }

    /** 진화 이미지에 넣을 문장. 고른 잉크가 없으면 null (지금까지와 같은 진화) */
    @Transactional
    public String promptFor(User user, String stage) {
        Tamagotchi pet = tamagotchiRepository.findByUserId(user.getId()).orElse(null);
        if (pet == null) return null;
        return LandCatalog.absorbedPrompt(pet.getInkMain(),
                java.util.stream.Stream.of(pet.getInkFamily1(), pet.getInkFamily2()).filter(Objects::nonNull).toList(), stage);
    }

    private Loadout view(Tamagotchi pet) {
        InkView main = LandCatalog.ink(pet.getInkMain()).map(InkView::of).orElse(null);
        List<InkView> families = java.util.stream.Stream.of(pet.getInkFamily1(), pet.getInkFamily2())
                .map(LandCatalog::ink).flatMap(java.util.Optional::stream).map(InkView::of).toList();
        return new Loadout(main, families);
    }

    private Tamagotchi pet(User user) {
        return tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("끄적이가 없어요."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
