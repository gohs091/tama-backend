package com.tamagochi.tamagochi.service.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.domain.membership.PainterMembership;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.membership.PainterMembershipRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.IapOrderVerifier;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 화가 회원(토스 인앱 정기결제).
 * - 결제 직후: 앱이 processProductGrant에서 받은 orderId를 보내면, 토스 결제 상태 조회 API로 확인한 뒤 회원으로 기록한다.
 * - 이후: 토스가 콘솔에 등록한 콜백 URL로 갱신·해지·만료·환불(REVOKED)을 알려 주면 상태를 고친다.
 * 혜택(경험치 2배·잉크 보상 2배·후보 다시 그리기 5번·자는 모습 그림·자동 반복)은 isPainter로 판정한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PainterMembershipService {

    /** 콘솔에 등록한 화가 회원 상품 → 갱신 주기 (판매가 1주 1,980원 · 1달 4,950원) */
    public static final Map<String, Duration> SKUS = Map.of(
            "sub.p7f.mw56h3ji.8ad5fe2662", Duration.ofDays(7),
            "sub.p7f.mw56ho1e.a5eb9fb7a6", Duration.ofDays(30));

    private final PainterMembershipRepository repository;
    private final TamagotchiRepository tamagotchiRepository;
    private final IapOrderVerifier orderVerifier;
    private final Clock clock;

    public record Status(boolean painter, String sku, LocalDateTime expiresAt, boolean autoRenew) {
        public static final Status NONE = new Status(false, null, null, false);
    }

    /** 지금 화가 회원인가 */
    public boolean isPainter(Long userId) {
        return current(userId).isPresent();
    }

    public Status status(User user) {
        return current(user.getId())
                .map(m -> new Status(true, m.getSku(), m.getExpiresAt(), m.isAutoRenew()))
                .orElse(Status.NONE);
    }

    private Optional<PainterMembership> current(Long userId) {
        if (userId == null) return Optional.empty();
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().filter(m -> m.isAccessible(now)).findFirst();
    }

    /**
     * 결제 직후 회원 지급. 토스에 결제됐는지 확인하고 기록한다. 같은 주문은 한 번만 기록한다(멱등).
     * 다른 사람의 주문을 가져다 쓰지 못하게, 이미 다른 유저에게 기록된 주문이면 거절한다.
     */
    @Transactional
    public Status grant(User user, String orderId, String sku) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("주문 번호가 필요해요.");
        Duration cycle = SKUS.get(sku);
        if (cycle == null) throw new IllegalArgumentException("화가 회원 상품이 아니에요.");
        Optional<PainterMembership> existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            if (!existing.get().getUserId().equals(user.getId())) throw new IllegalStateException("다른 사람의 주문이에요.");
            return status(user);
        }
        if (!orderVerifier.isPaid(orderId, sku)) throw new IllegalStateException("결제를 확인하지 못했어요. 잠시 뒤에 다시 해 주세요.");
        LocalDateTime now = LocalDateTime.now(clock);
        repository.save(PainterMembership.start(user.getId(), orderId, sku, now, now.plus(cycle)));
        log.info("화가 회원 시작 - user={}, order={}, sku={}", user.getId(), orderId, sku);
        return status(user);
    }

    /**
     * 토스 웹훅 처리. 등록 확인 이벤트는 그냥 성공으로 응답한다.
     * 모르는 주문(앱이 아직 지급을 알리지 않은 주문)은 로그만 남긴다: 다음에 앱이 지급을 알리면 그때 기록된다.
     */
    @Transactional
    public void onWebhook(JsonNode body) {
        String type = body.path("eventType").asText("");
        if (!"subscription.status_changed".equals(type)) {
            log.info("토스 웹훅 - {}", type);
            return;
        }
        String orderId = body.path("orderId").asText("");
        JsonNode cur = body.path("subscription").path("current");
        String status = cur.path("status").asText("");
        Optional<PainterMembership> found = repository.findByOrderId(orderId);
        if (found.isEmpty()) {
            log.warn("토스 웹훅 - 모르는 주문 order={}, reason={}, status={}", orderId, body.path("changeReason").asText(), status);
            return;
        }
        PainterMembership m = found.get();
        LocalDateTime now = LocalDateTime.now(clock);
        m.apply(status, cur.path("accessGranted").asBoolean(false), parseTime(cur.path("expiresAt")), cur.path("autoRenew").asBoolean(false), now);
        log.info("화가 회원 상태 - user={}, order={}, reason={}, status={}, access={}, expiresAt={}",
                m.getUserId(), orderId, body.path("changeReason").asText(), status, m.isAccessGranted(), m.getExpiresAt());
        if ("REVOKED".equals(status)) revokeBenefits(m.getUserId());
    }

    /** 환불·회수: 회원으로 만든 자는 모습을 거둔다. 잉크로 산 자는 모습은 그대로 둔다 */
    private void revokeBenefits(Long userId) {
        tamagotchiRepository.findActiveByUserId(userId).ifPresent(Tamagotchi::revokeMemberSleepImages);
    }

    static LocalDateTime parseTime(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.asText().isBlank()) return null;
        try {
            return LocalDateTime.parse(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
