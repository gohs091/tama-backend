package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import com.tamagochi.tamagochi.domain.promotion.PromotionRecordedEvent;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EvolutionPromotionServiceTest {

    PromotionGrantRepository repository = mock(PromotionGrantRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    User user = User.builder().id(7L).tossUserId("toss_x").nickname("n").build();
    Map<String, PromotionGrant> saved = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        when(repository.countByTrainingSessionIdStartingWith(anyString()))
                .thenAnswer(i -> saved.keySet().stream().filter(k -> k.startsWith(i.getArgument(0))).count());
        when(repository.countByTrainingSessionIdStartingWithAndStatusNot(anyString(), eq(PromotionGrantStatus.FAILED)))
                .thenAnswer(i -> saved.entrySet().stream()
                        .filter(e -> e.getKey().startsWith(i.getArgument(0)) && e.getValue().getStatus() != PromotionGrantStatus.FAILED)
                        .count());
        when(repository.save(any(PromotionGrant.class))).thenAnswer(i -> {
            PromotionGrant g = i.getArgument(0);
            saved.put(g.getTrainingSessionId(), g);
            return g;
        });
    }

    private EvolutionPromotionService service(String mode, String instant) {
        return new EvolutionPromotionService(repository, Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul")), events, mode);
    }

    @Test
    @DisplayName("성체가 되면 10원, 완전체가 되면 50원을 기록하고 grant 모드면 지급을 시작한다")
    void recordsChildAndAdult() {
        EvolutionPromotionService s = service("grant", "2026-10-28T03:00:00Z");

        s.onEvolved(user, EvolutionStage.CHILD);
        s.onEvolved(user, EvolutionStage.ADULT);

        assertThat(saved.get("evolution-CHILD-7-202610-1").getAmount()).isEqualTo(10);
        assertThat(saved.get("evolution-ADULT-7-202610-1").getAmount()).isEqualTo(50);
        assertThat(saved.get("evolution-ADULT-7-202610-1").isEvolution()).isTrue();
        verify(events, times(2)).publishEvent(any(PromotionRecordedEvent.class));
        assertThat(s.info(user).rewards()).allMatch(r -> r.remainingThisMonth() == 3);
    }

    @Test
    @DisplayName("다시 키워 또 진화하면 또 주되, 단계마다 한 달에 4번까지다")
    void fourTimesPerMonth() {
        EvolutionPromotionService s = service("grant", "2026-10-28T03:00:00Z");

        for (int i = 0; i < 6; i++) s.onEvolved(user, EvolutionStage.ADULT);

        assertThat(saved).hasSize(4).containsKey("evolution-ADULT-7-202610-4");
        assertThat(s.info(user).rewards().get(1).remainingThisMonth()).isZero();
    }

    @Test
    @DisplayName("달이 바뀌면 다시 4번 받을 수 있다 (KST 기준)")
    void resetsNextMonth() {
        EvolutionPromotionService october = service("grant", "2026-10-28T03:00:00Z");
        for (int i = 0; i < 4; i++) october.onEvolved(user, EvolutionStage.CHILD);

        // 10/31 15:00 UTC = 11/1 00:00 KST
        EvolutionPromotionService november = service("grant", "2026-10-31T15:00:00Z");
        november.onEvolved(user, EvolutionStage.CHILD);

        assertThat(saved).containsKey("evolution-CHILD-7-202611-1");
    }

    @Test
    @DisplayName("지급에 실패한 건은 횟수에서 빼되, 순번은 겹치지 않게 다음 번호를 쓴다")
    void failedNotCounted() {
        EvolutionPromotionService s = service("grant", "2026-10-28T03:00:00Z");
        s.onEvolved(user, EvolutionStage.CHILD);
        saved.get("evolution-CHILD-7-202610-1").markFailed("test");

        s.onEvolved(user, EvolutionStage.CHILD);

        assertThat(saved).containsKey("evolution-CHILD-7-202610-2");
        assertThat(s.info(user).rewards().get(0).remainingThisMonth()).isEqualTo(3);
    }

    @Test
    @DisplayName("record 모드는 기록만 하고 지급을 시작하지 않는다. off 모드는 아무것도 하지 않는다")
    void modes() {
        service("record", "2026-10-28T03:00:00Z").onEvolved(user, EvolutionStage.CHILD);
        verify(events, never()).publishEvent(any());
        assertThat(saved).hasSize(1);

        saved.clear();
        EvolutionPromotionService off = service("off", "2026-10-28T03:00:00Z");
        off.onEvolved(user, EvolutionStage.ADULT);
        assertThat(saved).isEmpty();
        assertThat(off.info(user).enabled()).isFalse();
    }

    @Test
    @DisplayName("유체로 부화할 때는 보상이 없다")
    void noRewardForBaby() {
        service("grant", "2026-10-28T03:00:00Z").onEvolved(user, EvolutionStage.BABY);
        verify(repository, never()).save(any());
    }
}
