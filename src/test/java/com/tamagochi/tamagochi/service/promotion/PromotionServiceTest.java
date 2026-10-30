package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import com.tamagochi.tamagochi.domain.promotion.PromotionRecordedEvent;
import com.tamagochi.tamagochi.domain.promotion.PromotionOutcome;
import com.tamagochi.tamagochi.domain.promotion.PromotionRules;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

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
    private ApplicationEventPublisher events;
    private User me;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T01:00:00Z"), KST);

    @BeforeEach
    void setUp() {
        repository = mock(PromotionGrantRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(repository.save(any(PromotionGrant.class))).thenAnswer(i -> i.getArgument(0));
        me = User.builder().id(1L).tossUserId("toss_me").nickname("me").build();
    }

    private PromotionService service(String mode) {
        return new PromotionService(repository, clock, events, mode);
    }

    private void usedToday(long count) {
        when(repository.countByUserIdAndGrantDateAndStatusNot(anyLong(), any(LocalDate.class), any(PromotionGrantStatus.class)))
                .thenReturn(count);
    }

    @Test
    @DisplayName("관리자 테스트 건의 키는 training_session_id 열(36자)에 들어가는 길이다")
    void adminTestKeysFitTheColumn() {
        PromotionGrant training = service("off").recordTest(me, 1);
        PromotionGrant evolution = service("off").recordEvolutionTest(me, 1);

        assertThat(training.getTrainingSessionId()).startsWith("admin-test-").hasSizeLessThanOrEqualTo(36);
        assertThat(evolution.getTrainingSessionId()).startsWith("admin-test-").hasSizeLessThanOrEqualTo(36);
        assertThat(evolution.isAdminTest()).isTrue();
        assertThat(training.getTrainingSessionId()).isNotEqualTo(service("off").recordTest(me, 1).getTrainingSessionId());
    }

    @Test
    @DisplayName("탭 수 구간(5초): 30 미만 0, 30~39 1, 40 이상 2, 50을 넘는 탭은 잘라낸다")
    void tiers() {
        assertThat(PromotionRules.tierForTaps(0)).isZero();
        assertThat(PromotionRules.tierForTaps(29)).isZero();
        assertThat(PromotionRules.tierForTaps(30)).isEqualTo(1);
        assertThat(PromotionRules.tierForTaps(39)).isEqualTo(1);
        assertThat(PromotionRules.tierForTaps(40)).isEqualTo(2);
        assertThat(PromotionRules.tierForTaps(500)).isEqualTo(2);
        assertThat(PromotionRules.tierForTaps(-5)).isZero();
        assertThat(PromotionRules.amountForTier(0)).isZero();
        assertThat(PromotionRules.amountForTier(1)).isEqualTo(1);
        assertThat(PromotionRules.amountForTier(2)).isEqualTo(2);
    }

    @Test
    @DisplayName("안내: 구간표와 오늘 남은 횟수를 돌려준다")
    void info() {
        usedToday(1);

        var info = service("record").info(me);

        assertThat(info.isEnabled()).isTrue();
        assertThat(info.getDailyLimit()).isEqualTo(PromotionRules.DAILY_LIMIT);
        assertThat(info.getRemainingToday()).isEqualTo(PromotionRules.DAILY_LIMIT - 1);
        assertThat(info.getMaxCountedTaps()).isEqualTo(PromotionRules.MAX_COUNTED_TAPS);
        assertThat(info.getTiers()).extracting("minTaps", "amount").containsExactly(
                org.assertj.core.groups.Tuple.tuple(30, 1), org.assertj.core.groups.Tuple.tuple(40, 2));
    }

    @Test
    @DisplayName("꺼져 있으면 안내도 꺼짐으로 내려가고 남은 횟수는 0이다")
    void infoWhenOff() {
        var info = service("off").info(me);
        assertThat(info.isEnabled()).isFalse();
        assertThat(info.getRemainingToday()).isZero();
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
    @DisplayName("record 모드에서는 기록만 하고 지급 신호는 보내지 않는다")
    void recordModeDoesNotPublish() {
        usedToday(0);
        service("record").evaluate(me, "s1", 85);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("grant 모드에서는 기록한 뒤 지급 신호를 보낸다")
    void grantModePublishesEvent() {
        usedToday(0);
        service("grant").evaluate(me, "s1", 85);
        verify(events).publishEvent(any(PromotionRecordedEvent.class));
    }

    @Test
    @DisplayName("대상이 아니거나 상한이면 grant 모드여도 지급 신호를 보내지 않는다")
    void noEventWhenNotEligible() {
        usedToday(0);
        service("grant").evaluate(me, "s1", 10);
        usedToday(PromotionRules.DAILY_LIMIT);
        service("grant").evaluate(me, "s2", 95);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("탭 수가 모자라면 기록하지 않는다")
    void belowTier() {
        usedToday(1);

        PromotionOutcome outcome = service("record").evaluate(me, "s1", 20);

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
