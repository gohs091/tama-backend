package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionOutcome;
import com.tamagochi.tamagochi.domain.promotion.PromotionRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private PromotionGrantRepository repository;
    private User me;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T01:00:00Z"), KST);

    @BeforeEach
    void setUp() {
        repository = mock(PromotionGrantRepository.class);
        me = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();
    }

    private PromotionService service(String mode) {
        return new PromotionService(repository, clock, mode);
    }

    private void usedToday(long count) {
        when(repository.countByUserIdAndGrantDate(anyLong(), any(LocalDate.class))).thenReturn(count);
    }

    @Test
    @DisplayName("탭 수 구간: 60 미만 0, 60~79 1, 80 이상 2, 100을 넘는 탭은 잘라낸다")
    void tiers() {
        assertThat(PromotionRules.tierForTaps(0)).isZero();
        assertThat(PromotionRules.tierForTaps(59)).isZero();
        assertThat(PromotionRules.tierForTaps(60)).isEqualTo(1);
        assertThat(PromotionRules.tierForTaps(79)).isEqualTo(1);
        assertThat(PromotionRules.tierForTaps(80)).isEqualTo(2);
        assertThat(PromotionRules.tierForTaps(500)).isEqualTo(2);
        assertThat(PromotionRules.tierForTaps(-5)).isZero();
        assertThat(PromotionRules.amountForTier(0)).isZero();
        assertThat(PromotionRules.amountForTier(1)).isEqualTo(1);
        assertThat(PromotionRules.amountForTier(2)).isEqualTo(2);
    }

    @Test
    @DisplayName("꺼져 있으면(off) 판정도 기록도 하지 않는다")
    void off() {
        assertThat(service("off").evaluate(me, "s1", 90)).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("대상이 되면 기록하고 오늘 남은 횟수를 돌려준다")
    void recordsGrant() {
        usedToday(0);

        PromotionOutcome outcome = service("record").evaluate(me, "s1", 85);

        assertThat(outcome.getResult()).isEqualTo(PromotionOutcome.Result.RECORDED);
        assertThat(outcome.getTier()).isEqualTo(2);
        assertThat(outcome.getAmount()).isEqualTo(2);
        assertThat(outcome.getRemainingToday()).isEqualTo(PromotionRules.DAILY_LIMIT - 1);

        ArgumentCaptor<PromotionGrant> saved = ArgumentCaptor.forClass(PromotionGrant.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTrainingSessionId()).isEqualTo("s1");
        assertThat(saved.getValue().getGrantDate()).isEqualTo(LocalDate.of(2026, 10, 26)); // KST 기준 날짜
        assertThat(saved.getValue().getClickCount()).isEqualTo(85);
    }

    @Test
    @DisplayName("탭 수가 모자라면 기록하지 않는다")
    void belowTier() {
        usedToday(1);

        PromotionOutcome outcome = service("record").evaluate(me, "s1", 40);

        assertThat(outcome.getResult()).isEqualTo(PromotionOutcome.Result.BELOW_TIER);
        assertThat(outcome.getAmount()).isZero();
        assertThat(outcome.getRemainingToday()).isEqualTo(PromotionRules.DAILY_LIMIT - 1);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("하루 상한을 다 쓰면 기록하지 않고 상한 도달로 알린다")
    void dailyLimit() {
        usedToday(PromotionRules.DAILY_LIMIT);

        PromotionOutcome outcome = service("record").evaluate(me, "s1", 95);

        assertThat(outcome.getResult()).isEqualTo(PromotionOutcome.Result.DAILY_LIMIT);
        assertThat(outcome.getAmount()).isZero();
        assertThat(outcome.getRemainingToday()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("한 판이 오늘의 마지막 기회였으면 남은 횟수가 0이 된다")
    void lastChance() {
        usedToday(PromotionRules.DAILY_LIMIT - 1);

        PromotionOutcome outcome = service("record").evaluate(me, "s1", 70);

        assertThat(outcome.getResult()).isEqualTo(PromotionOutcome.Result.RECORDED);
        assertThat(outcome.getRemainingToday()).isZero();
    }
}
