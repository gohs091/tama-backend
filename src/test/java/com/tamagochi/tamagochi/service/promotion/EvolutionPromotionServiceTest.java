package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EvolutionPromotionServiceTest {

    PromotionGrantRepository repository = mock(PromotionGrantRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    User user = User.builder().id(7L).tossUserId("toss_x").nickname("n").build();
    Map<String, PromotionGrant> saved = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(repository.findByTrainingSessionId(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.<String>getArgument(0))));
        when(repository.save(any(PromotionGrant.class))).thenAnswer(i -> {
            PromotionGrant g = i.getArgument(0);
            saved.put(g.getTrainingSessionId(), g);
            return g;
        });
    }

    private EvolutionPromotionService service(String mode) {
        return new EvolutionPromotionService(repository, clock, events, mode);
    }

    @Test
    @DisplayName("성체가 되면 10원, 완전체가 되면 50원을 진화 보상으로 기록하고 grant 모드면 지급을 시작한다")
    void recordsChildAndAdult() {
        EvolutionPromotionService s = service("grant");

        s.onEvolved(user, EvolutionStage.CHILD);
        s.onEvolved(user, EvolutionStage.ADULT);

        assertThat(saved.get("evolution-CHILD-7").getAmount()).isEqualTo(10);
        assertThat(saved.get("evolution-ADULT-7").getAmount()).isEqualTo(50);
        assertThat(saved.get("evolution-ADULT-7").isEvolution()).isTrue();
        verify(events, times(2)).publishEvent(any(PromotionRecordedEvent.class));
        assertThat(s.info(user).rewards()).allMatch(EvolutionPromotionService.Reward::received);
    }

    @Test
    @DisplayName("같은 단계는 다시 키워 또 진화해도 한 번만 준다")
    void oncePerStage() {
        EvolutionPromotionService s = service("grant");

        s.onEvolved(user, EvolutionStage.CHILD);
        s.onEvolved(user, EvolutionStage.CHILD);

        verify(repository, times(1)).save(any(PromotionGrant.class));
    }

    @Test
    @DisplayName("record 모드는 기록만 하고 지급을 시작하지 않는다. off 모드는 아무것도 하지 않는다")
    void modes() {
        service("record").onEvolved(user, EvolutionStage.CHILD);
        verify(events, never()).publishEvent(any());
        assertThat(saved).containsKey("evolution-CHILD-7");

        saved.clear();
        EvolutionPromotionService off = service("off");
        off.onEvolved(user, EvolutionStage.ADULT);
        assertThat(saved).isEmpty();
        assertThat(off.info(user).enabled()).isFalse();
    }

    @Test
    @DisplayName("유체로 부화할 때는 보상이 없다")
    void noRewardForBaby() {
        service("grant").onEvolved(user, EvolutionStage.BABY);
        verify(repository, never()).save(any());
    }
}
