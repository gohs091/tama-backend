package com.tamagochi.tamagochi.service.spar;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.land.LandInk;
import com.tamagochi.tamagochi.domain.spar.InkFragment;
import com.tamagochi.tamagochi.domain.spar.SparDrop;
import com.tamagochi.tamagochi.domain.spar.SparRoster;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.land.LandInkRepository;
import com.tamagochi.tamagochi.repository.spar.InkFragmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SparDropServiceTest {

    private final Map<String, InkFragment> fragments = new HashMap<>();
    private final List<LandInk> owned = new ArrayList<>();
    private SparDropService service;
    private User me;

    @BeforeEach
    void setUp() {
        InkFragmentRepository fragmentRepository = mock(InkFragmentRepository.class);
        LandInkRepository landInkRepository = mock(LandInkRepository.class);
        when(fragmentRepository.findByUserIdAndInkId(anyLong(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(fragments.get(i.<String>getArgument(1))));
        when(fragmentRepository.save(any(InkFragment.class))).thenAnswer(i -> {
            InkFragment f = i.getArgument(0);
            fragments.put(f.getInkId(), f);
            return f;
        });
        when(landInkRepository.findByUserIdOrderByFoundAtAsc(anyLong())).thenAnswer(i -> List.copyOf(owned));
        when(landInkRepository.save(any(LandInk.class))).thenAnswer(i -> {
            owned.add(i.getArgument(0));
            return i.getArgument(0);
        });
        service = new SparDropService(fragmentRepository, landInkRepository,
                Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC));
        me = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();
    }

    private static SparRoster.Enemy enemyWith(LandCatalog.Grade grade) {
        return SparRoster.all().stream()
                .filter(e -> e.drops().stream().anyMatch(d -> LandCatalog.ink(d).orElseThrow().grade() == grade))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("지면 떨어지지 않고, 이겨도 확률(15%)을 넘기면 떨어지지 않는다")
    void noDropOnLossOrMiss() {
        String id = SparRoster.all().get(0).id();
        assertThat(service.roll(me, id, false, 0.0, 0.0)).isNull();
        assertThat(service.roll(me, id, true, 0.15, 0.0)).isNull();
        assertThat(service.roll(me, "crow", true, 0.0, 0.0)).isNull(); // 예전 임시 상대
    }

    @Test
    @DisplayName("일반 잉크는 조각 5개를 모으면 완성돼 보관함에 들어가고, 그 뒤로는 그 잉크가 나오지 않는다")
    void commonCompletesAtFive() {
        SparRoster.Enemy enemy = SparRoster.all().get(0);
        SparDrop first = service.roll(me, enemy.id(), true, 0.0, 0.0);
        assertThat(first).isNotNull();
        String inkId = first.inkId();
        int need = first.need();
        SparDrop last = first;
        for (int i = 1; i < need; i++) last = service.roll(me, enemy.id(), true, 0.0, 0.0);
        assertThat(last.inkId()).isEqualTo(inkId);
        assertThat(last.completed()).isTrue();
        assertThat(last.count()).isEqualTo(need);
        assertThat(owned).extracting(LandInk::getInkId).containsExactly(inkId);
        // 완성한 잉크는 칸에서 빠진다
        assertThat(service.roll(me, enemy.id(), true, 0.0, 0.0).inkId()).isNotEqualTo(inkId);
    }

    @Test
    @DisplayName("전설 잉크는 조각 하나로 바로 완성된다")
    void legendCompletesAtOne() {
        SparRoster.Enemy enemy = enemyWith(LandCatalog.Grade.LEGEND);
        // 전설이 아닌 칸은 이미 다 모았다고 두면 전설만 남는다
        enemy.drops().stream().filter(d -> LandCatalog.ink(d).orElseThrow().grade() != LandCatalog.Grade.LEGEND)
                .forEach(d -> owned.add(new LandInk(me, d, Instant.EPOCH)));
        SparDrop drop = service.roll(me, enemy.id(), true, 0.0, 0.5);
        assertThat(drop.grade()).isEqualTo(LandCatalog.Grade.LEGEND);
        assertThat(drop.need()).isEqualTo(1);
        assertThat(drop.completed()).isTrue();
        assertThat(service.progress(me)).isEmpty();
    }
}
