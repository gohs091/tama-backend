package com.tamagochi.tamagochi.service.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.membership.PainterMembership;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.membership.PainterMembershipRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.IapOrderVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PainterMembershipServiceTest {

    static final String WEEK = "sub.p7f.mw56h3ji.8ad5fe2662";
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    final Clock clock = Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), SEOUL); // 12:00 KST
    final LocalDateTime now = LocalDateTime.now(clock);

    final List<PainterMembership> saved = new ArrayList<>();
    final PainterMembershipRepository repo = mock(PainterMembershipRepository.class);
    final TamagotchiRepository pets = mock(TamagotchiRepository.class);
    final IapOrderVerifier verifier = mock(IapOrderVerifier.class);
    final PainterMembershipService service = new PainterMembershipService(repo, pets, verifier, clock);
    final User user = User.builder().id(1L).build();

    PainterMembershipServiceTest() {
        when(repo.save(any())).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        when(repo.findByUserIdOrderByCreatedAtDesc(any())).thenAnswer(inv -> saved.stream().filter(m -> m.getUserId().equals(inv.getArgument(0))).toList());
        when(repo.findByOrderId(any())).thenAnswer(inv -> saved.stream().filter(m -> m.getOrderId().equals(inv.getArgument(0))).findFirst());
    }

    @Test
    @DisplayName("토스에 결제가 확인되면 회원이 되고, 1주 상품은 7일 뒤까지 쓸 수 있다. 같은 주문은 한 번만 기록한다")
    void grant() {
        when(verifier.isPaid("o1", WEEK)).thenReturn(true);
        assertThat(service.isPainter(1L)).isFalse();

        PainterMembershipService.Status s = service.grant(user, "o1", WEEK);

        assertThat(s.painter()).isTrue();
        assertThat(s.expiresAt()).isEqualTo(now.plusDays(7));
        service.grant(user, "o1", WEEK);
        assertThat(saved).hasSize(1);
    }

    @Test
    @DisplayName("결제가 확인되지 않거나, 화가 회원 상품이 아니거나, 다른 사람의 주문이면 회원이 되지 않는다")
    void grantRejects() {
        when(verifier.isPaid("o2", WEEK)).thenReturn(false);
        assertThatThrownBy(() -> service.grant(user, "o2", WEEK)).hasMessageContaining("결제를 확인");
        assertThatThrownBy(() -> service.grant(user, "o3", "ink.100")).hasMessageContaining("화가 회원 상품");

        saved.add(PainterMembership.start(2L, "o4", WEEK, now, now.plusDays(7)));
        assertThatThrownBy(() -> service.grant(user, "o4", WEEK)).hasMessageContaining("다른 사람");
        assertThat(service.isPainter(1L)).isFalse();
    }

    @Test
    @DisplayName("웹훅: 갱신이면 만료일이 늘고, 만료·환불(REVOKED)이면 혜택이 꺼진다. 환불이면 회원으로 만든 자는 모습을 거둔다")
    void webhook() throws Exception {
        saved.add(PainterMembership.start(1L, "o5", WEEK, now, now.plusDays(7)));
        ObjectMapper om = new ObjectMapper();

        service.onWebhook(om.readTree("""
                {"eventType":"subscription.status_changed","orderId":"o5","sku":"%s","changeReason":"RENEWED",
                 "subscription":{"current":{"status":"ACTIVE","accessGranted":true,"expiresAt":"2026-11-21T12:00:00","autoRenew":true}}}
                """.formatted(WEEK)));
        assertThat(service.status(user).expiresAt()).isEqualTo(LocalDateTime.parse("2026-11-21T12:00:00"));

        Tamagotchi pet = mock(Tamagotchi.class);
        when(pets.findActiveByUserId(1L)).thenReturn(Optional.of(pet));
        service.onWebhook(om.readTree("""
                {"eventType":"subscription.status_changed","orderId":"o5","changeReason":"REVOKED",
                 "subscription":{"current":{"status":"REVOKED","accessGranted":false,"expiresAt":null,"autoRenew":false}}}
                """));
        assertThat(service.isPainter(1L)).isFalse();
        verify(pet).revokeMemberSleepImages();

        service.onWebhook(om.readTree("{\"eventType\":\"callback.registration_verification\"}")); // 등록 확인은 그냥 받는다
    }

    @Test
    @DisplayName("한 판만큼 회복: 고기·알약을 각각 최대 4개, 가득 찬 쪽은 쓰지 않는다")
    void recoveryPlan() {
        Tamagotchi tired = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(60).strength(30).build();
        assertThat(tired.planRecovery()).isEqualTo(new Tamagotchi.Recovery(4, 4));
        tired.recover(tired.planRecovery());
        assertThat(tired.getHunger()).isEqualTo(48);
        assertThat(tired.getStrength()).isEqualTo(42);

        Tamagotchi almostFull = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(4).strength(100).build();
        assertThat(almostFull.planRecovery()).isEqualTo(new Tamagotchi.Recovery(2, 0));
    }

    @Test
    @DisplayName("자는 모습: 예전에 잉크로 산 끄적이는 그대로, 회원으로 만든 것만 환불 때 거둔다")
    void memberSleepImages() {
        Tamagotchi legacy = Tamagotchi.builder().evolutionStage(EvolutionStage.CHILD).hunger(20).strength(80).build();
        legacy.markSleepImage(EvolutionStage.BABY); // 예전 행: 잉크로만 살 수 있었다
        assertThat(legacy.isSleepImagePaidByInk()).isTrue();
        legacy.revokeMemberSleepImages();
        assertThat(legacy.hasSleepImage(EvolutionStage.BABY)).isTrue();

        Tamagotchi member = Tamagotchi.builder().evolutionStage(EvolutionStage.CHILD).hunger(20).strength(80).build();
        member.markSleepImageByMember();
        member.markSleepImage(EvolutionStage.CHILD);
        assertThat(member.isSleepImagePaidByInk()).isFalse();
        member.revokeMemberSleepImages();
        assertThat(member.hasSleepImage(EvolutionStage.CHILD)).isFalse();
    }
}
