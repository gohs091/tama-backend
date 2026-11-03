package com.tamagochi.tamagochi.service.spar;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.land.LandInk;
import com.tamagochi.tamagochi.domain.spar.InkFragment;
import com.tamagochi.tamagochi.domain.spar.SparDrop;
import com.tamagochi.tamagochi.domain.spar.SparDropRules;
import com.tamagochi.tamagochi.domain.spar.SparRoster;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.land.LandInkRepository;
import com.tamagochi.tamagochi.repository.spar.InkFragmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** 스파링에서 이긴 상대가 잉크 조각을 떨어뜨린다 (규칙은 SparDropRules) */
@Slf4j
@Service
@RequiredArgsConstructor
public class SparDropService {

    private final InkFragmentRepository fragmentRepository;
    private final LandInkRepository landInkRepository;
    private final Clock clock;

    @Transactional
    public SparDrop roll(User user, String enemyId, boolean win) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return roll(user, enemyId, win, r.nextDouble(), r.nextDouble());
    }

    /** chance: 조각이 떨어질지 굴린 값, pick: 어떤 잉크인지 굴린 값 (둘 다 0 이상 1 미만) */
    @Transactional
    public SparDrop roll(User user, String enemyId, boolean win, double chance, double pick) {
        if (!win || chance >= SparDropRules.PER_WIN) return null;
        SparRoster.Enemy enemy = SparRoster.find(enemyId).orElse(null);
        if (enemy == null) return null; // 예전 임시 상대(crow 등)는 잉크가 없다

        Set<String> owned = landInkRepository.findByUserIdOrderByFoundAtAsc(user.getId()).stream()
                .map(LandInk::getInkId).collect(Collectors.toSet());
        List<LandCatalog.Ink> candidates = enemy.drops().stream()
                .filter(id -> !owned.contains(id))
                .map(LandCatalog::ink).flatMap(java.util.Optional::stream)
                .toList();
        LandCatalog.Ink ink = SparDropRules.pickWeighted(candidates, i -> SparDropRules.weight(i.grade()), pick);
        if (ink == null) return null; // 이 상대의 잉크를 모두 모았다

        Instant now = clock.instant();
        InkFragment fragment = fragmentRepository.findByUserIdAndInkId(user.getId(), ink.id())
                .orElseGet(() -> fragmentRepository.save(new InkFragment(user, ink.id())));
        fragment.add(now);
        int need = SparDropRules.need(ink.grade());
        boolean completed = fragment.getCount() >= need;
        if (completed) landInkRepository.save(new LandInk(user, ink.id(), now));
        log.info("스파링 드롭 - user: {}, enemy: {}, ink: {} ({}/{}){}", user.getId(), enemyId, ink.id(),
                fragment.getCount(), need, completed ? " 완성" : "");
        return new SparDrop(ink.id(), ink.emoji(), ink.name(), ink.grade(), Math.min(fragment.getCount(), need), need, completed);
    }

    /** 모으는 중인 조각 (완성한 잉크는 빼고) */
    @Transactional(readOnly = true)
    public List<SparDrop> progress(User user) {
        Set<String> owned = landInkRepository.findByUserIdOrderByFoundAtAsc(user.getId()).stream()
                .map(LandInk::getInkId).collect(Collectors.toSet());
        return fragmentRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .filter(f -> !owned.contains(f.getInkId()))
                .flatMap(f -> LandCatalog.ink(f.getInkId()).stream()
                        .map(ink -> new SparDrop(ink.id(), ink.emoji(), ink.name(), ink.grade(), f.getCount(),
                                SparDropRules.need(ink.grade()), false)))
                .toList();
    }
}
