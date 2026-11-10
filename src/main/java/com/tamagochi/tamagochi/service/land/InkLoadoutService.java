package com.tamagochi.tamagochi.service.land;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.land.LandInkRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.land.AdventureService.InkView;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 다음 진화에 반영할 대표 잉크: 특정 잉크 최대 1개 + 계열 잉크 최대 2개. 모두 비워 둬도 진화한다.
 * 클라이언트는 잉크 ID만 보내고, 서버가 보유 여부를 확인한 뒤 고정 문구로 프롬프트에 넣는다.
 */
@Service
@RequiredArgsConstructor
public class InkLoadoutService {


    private final TamagotchiRepository tamagotchiRepository;
    private final LandInkRepository inkRepository;

    /** 잉크 진화 시안 버전: v0(부위 교체, 지금 방식) | v1(속성 재해석). LandCatalog.INK_STYLE_* 참고 */
    @Value("${image.ink-style:v0}")
    private String inkStyle = LandCatalog.INK_STYLE_V0;

    public record Loadout(InkView main, List<InkView> families) {}

    @Transactional
    public Loadout get(User user) {
        return view(pet(user));
    }

    /**
     * 진화 잉크는 한 칸(2026-11-10): 특정 잉크든 계열 잉크든 하나만 넣는다. 여러 개를 섞어도 결과가 크게 다르지 않았고,
     * 칸이 하나여야 "이 잉크를 넣어서 이렇게 됐다"가 바로 읽힌다. 계열 잉크(불씨·전기·새 등)도 혼자 넣으면 좋은 잉크다.
     * 예전 앱이 계열 칸(familyIds)으로만 보내면 첫 번째를 그 한 칸으로 쓰고, 나머지는 무시한다.
     */
    @Transactional
    public Loadout set(User user, String mainId, List<String> familyIds) {
        Tamagotchi pet = pet(user);
        if (pet.getEvolutionStage() != EvolutionStage.BABY && pet.getEvolutionStage() != EvolutionStage.CHILD) {
            throw new IllegalStateException("진화할 수 있는 단계에서만 고를 수 있어요.");
        }
        String chosen = blankToNull(mainId);
        if (chosen == null && familyIds != null) {
            chosen = familyIds.stream().map(InkLoadoutService::blankToNull).filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (chosen != null) {
            LandCatalog.ink(chosen).orElseThrow(() -> new IllegalArgumentException("알 수 없는 잉크예요."));
            String id = chosen;
            boolean owned = inkRepository.findByUserIdOrderByFoundAtAsc(user.getId()).stream().anyMatch(i -> id.equals(i.getInkId()));
            if (!owned) throw new IllegalStateException("아직 찾지 못한 잉크예요.");
        }
        pet.setInkLoadout(chosen, null, null);
        return view(pet);
    }

    /** 진화 이미지에 넣을 문장. 고른 잉크가 없으면 null (지금까지와 같은 진화) */
    @Transactional
    public String promptFor(User user, String stage) {
        Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId()).orElse(null);
        if (pet == null) return null;
        return LandCatalog.singleInkPrompt(singleInk(pet), stage, inkStyle);
    }

    /** v5 완성작 디자이너에게 줄 잉크 설명. 고른 잉크가 없으면 null */
    @Transactional
    public String themeFor(User user) {
        Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId()).orElse(null);
        if (pet == null) return null;
        return LandCatalog.singleInkTheme(singleInk(pet));
    }

    /** 넣은 잉크 하나. 예전에 계열 칸에만 넣어 둔 끄적이는 첫 계열 잉크를 쓴다 */
    private static String singleInk(Tamagotchi pet) {
        return pet.getInkMain() != null ? pet.getInkMain() : pet.getInkFamily1();
    }

    private Loadout view(Tamagotchi pet) {
        InkView main = LandCatalog.ink(pet.getInkMain()).map(InkView::of).orElse(null);
        List<InkView> families = java.util.stream.Stream.of(pet.getInkFamily1(), pet.getInkFamily2())
                .map(LandCatalog::ink).flatMap(java.util.Optional::stream).map(InkView::of).toList();
        return new Loadout(main, families);
    }

    private Tamagotchi pet(User user) {
        return tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("끄적이가 없어요."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
