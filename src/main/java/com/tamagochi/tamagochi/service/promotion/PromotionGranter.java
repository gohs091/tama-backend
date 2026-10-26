package com.tamagochi.tamagochi.service.promotion;

import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import com.tamagochi.tamagochi.service.toss.TossPromotionApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 기록된 프로모션을 토스 포인트로 실제 지급한다.
 * 한 건의 흐름: 선점(RECORDED→SENDING) → 지급 키 발급 → 지급 요청 → 결과 조회(PENDING이면 나중에 다시 조회).
 * 키는 한 번 받으면 재시도에도 그대로 써서, 요청이 끊긴 뒤 다시 보내도 두 번 지급되지 않는다(토스가 4113으로 막는다).
 * 토스 호출 중에는 DB 트랜잭션을 잡지 않는다.
 */
@Service
@Slf4j
public class PromotionGranter {

    /** 다시 시도해도 소용없는 오류: 프로모션 없음·중지·종료·미승인, 예산 부족, 1회 한도 초과, 잘못된 요청, 권한 없음 */
    private static final Set<String> PERMANENT_ERRORS =
            Set.of("4000", "4010", "4034", "4100", "4105", "4108", "4109", "4112", "4114");
    private static final String ALREADY_GRANTED = "4113";
    private static final String TOSS_USER_PREFIX = "toss_";

    private static final Duration RETRY_AFTER = Duration.ofSeconds(20);
    private static final Duration POLL_AFTER = Duration.ofSeconds(10);
    private static final Duration SENDING_STUCK_AFTER = Duration.ofMinutes(2);

    private final PromotionGrantRepository repository;
    private final TossPromotionApi api;
    private final Clock clock;
    private final String promotionCode;

    public PromotionGranter(PromotionGrantRepository repository, TossPromotionApi api, Clock clock,
                            @Value("${tamagotchi.promotion.code:}") String promotionCode) {
        this.repository = repository;
        this.api = api;
        this.clock = clock;
        this.promotionCode = promotionCode == null ? "" : promotionCode.trim();
    }

    public boolean isConfigured() {
        return !promotionCode.isBlank();
    }

    /** 새로 기록된(또는 재시도할) 한 건을 지급한다. 이미 다른 곳에서 처리 중이면 아무것도 하지 않는다. */
    public void grant(Long grantId) {
        if (!isConfigured()) {
            log.error("프로모션 코드(tamagotchi.promotion.code)가 없어 지급하지 않습니다. grantId: {}", grantId);
            return;
        }
        if (repository.claim(grantId, PromotionGrantStatus.RECORDED, PromotionGrantStatus.SENDING, now()) == 0) {
            return;
        }
        PromotionGrant grant = repository.findWithUserById(grantId).orElse(null);
        if (grant == null) return;

        String anonKey = anonKeyOf(grant);
        if (anonKey == null) {
            grant.markFailed("토스 사용자가 아니어서 지급할 수 없어요");
            repository.save(grant);
            return;
        }

        try {
            if (grant.getGrantKey() == null) {
                TossPromotionApi.Response key = api.getKey();
                if (!key.ok()) {
                    fail(grant, "get-key", key);
                    return;
                }
                grant.saveGrantKey(key.success().path("key").asText());
                repository.save(grant); // 키를 먼저 저장해 두어야 요청이 끊겨도 같은 키로 재시도한다
            }
            TossPromotionApi.Response executed = api.execute(anonKey, promotionCode, grant.getGrantKey(), grant.getAmount());
            if (executed.ok() || ALREADY_GRANTED.equals(executed.errorCode())) {
                confirm(grant, anonKey);
            } else {
                fail(grant, "execute", executed);
            }
        } catch (Exception e) {
            log.warn("프로모션 지급 중 오류 - grantId: {}", grantId, e);
            grant.retryLater("호출 오류: " + e.getMessage());
            repository.save(grant);
        }
    }

    /** 접수된 지급의 결과를 조회해 상태를 확정한다. */
    private void confirm(PromotionGrant grant, String anonKey) {
        TossPromotionApi.Response result = api.result(anonKey, promotionCode, grant.getGrantKey());
        String status = result.ok() ? result.success().asText("") : "";
        switch (status) {
            case "SUCCESS" -> {
                grant.markGranted();
                log.info("프로모션 지급 완료 - grantId: {}, amount: {}", grant.getId(), grant.getAmount());
            }
            case "FAILED" -> {
                // 토스가 지급에 실패했고 예산은 되돌려졌다. 새 키로 다시 시도한다.
                grant.saveGrantKey(null);
                grant.retryLater("토스 지급 실패(FAILED)");
            }
            default -> grant.markPending(); // PENDING이거나 조회 자체가 실패: 접수는 됐으니 나중에 다시 조회한다
        }
        repository.save(grant);
    }

    private void fail(PromotionGrant grant, String step, TossPromotionApi.Response response) {
        String reason = step + " " + response.resultType() + " " + response.errorCode() + " " + response.reason();
        if (PERMANENT_ERRORS.contains(response.errorCode())) {
            log.error("프로모션 지급 불가 - grantId: {}, {}", grant.getId(), reason);
            grant.markFailed(reason);
        } else {
            log.warn("프로모션 지급 재시도 예정 - grantId: {}, {}", grant.getId(), reason);
            grant.retryLater(reason);
        }
        repository.save(grant);
    }

    /** 주기적으로 돌며 재시도 대기·접수 중·멈춘 건을 처리한다. */
    public void sweep() {
        if (!isConfigured()) return;
        LocalDateTime now = now();

        // 서버가 지급 도중 멈춰 SENDING에 남은 건은 다시 대기로 돌린다 (같은 키로 재시도해서 중복 지급은 없다)
        for (PromotionGrant g : repository.findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(
                PromotionGrantStatus.SENDING, now.minus(SENDING_STUCK_AFTER))) {
            repository.claim(g.getId(), PromotionGrantStatus.SENDING, PromotionGrantStatus.RECORDED, now);
        }

        List<PromotionGrant> retry = repository.findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(
                PromotionGrantStatus.RECORDED, now.minus(RETRY_AFTER));
        retry.forEach(g -> grant(g.getId()));

        for (PromotionGrant g : repository.findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(
                PromotionGrantStatus.PENDING, now.minus(POLL_AFTER))) {
            poll(g);
        }
    }

    private void poll(PromotionGrant grant) {
        String anonKey = anonKeyOf(grant);
        if (anonKey == null || grant.getGrantKey() == null) return;
        try {
            confirm(grant, anonKey);
        } catch (Exception e) {
            log.warn("프로모션 결과 조회 오류 - grantId: {}", grant.getId(), e);
        }
    }

    /** 토스 서버 API가 요구하는 사용자 키(x-anon-key). 우리 사용자 ID는 toss_ + 익명키다. */
    static String anonKeyOf(PromotionGrant grant) {
        String id = grant.getUser().getTossUserId();
        if (id == null || !id.startsWith(TOSS_USER_PREFIX) || id.length() == TOSS_USER_PREFIX.length()) return null;
        return id.substring(TOSS_USER_PREFIX.length());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
