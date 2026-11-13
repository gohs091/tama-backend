package com.tamagochi.tamagochi.service.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.PetStatus;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.toss.TossPartnerApiClient;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 끄적이 알림(토스 스마트 발송, 직접 API).
 * 5분마다 끄적이 상태를 보고, 해당하는 알림이 있으면 하나만 보낸다. 보내지 않는 때:
 * - 알림에 동의하지 않은 사람
 * - 앱을 30분 안에 본 사람 (보고 있는 사람에게 알림은 소음이다)
 * - 밤(22시~8시), 마지막 알림에서 3시간 안, 오늘 이미 4번 보냈을 때
 * - 같은 일로 이미 보냈을 때 (배고픔이면 밥을 먹어 풀릴 때까지, 진화면 단계마다 한 번)
 * push.enabled가 꺼져 있으면(기본) 아무것도 보내지 않는다. 콘솔 템플릿 검수가 끝난 뒤에 켠다.
 */
@Service
@Slf4j
public class PushService {

    static final String SEND_PATH = "/api-partner/v1/apps-in-toss/messenger/send-message";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TamagotchiRepository tamagotchiRepository;
    private final StringRedisTemplate redis;
    private final TossPartnerApiClient toss;

    @Value("${push.enabled:false}")
    private boolean enabled;

    /** 콘솔 발송 코드 앞부분. 알림 종류의 code를 붙인다 (예: doodle-app-hungry) */
    @Value("${push.code-prefix:doodle-app-}")
    private String codePrefix;

    /**
     * 템플릿 문구에 조사가 이미 적힌 알림 코드들 ("{{petName}}가 배가 고파요"). 콘솔 캠페인은 고칠 수 없어서 서버가 맞춘다:
     * 여기 있으면 조사 없이 부르는 이름만("용이", "냥냥이"), 없으면 조사까지 붙여("용이", "냥냥이가") 보낸다
     */
    @Value("${push.inline-particle:}")
    private java.util.Set<String> inlineParticle = java.util.Set.of();

    /**
     * 알림별 발송 코드를 바꿔 쓴다 ("hungry=doodle-app-hungry2,sick=..."). 콘솔 캠페인은 고치거나 지울 수 없어서,
     * 다시 만들면 새 코드를 여기에 적는다. 적지 않은 알림은 code-prefix + 코드를 쓴다
     */
    @Value("${push.codes:}")
    private String codeOverrides = "";

    /** 앱이 알림 동의 창을 띄울 때 쓰는 캠페인 코드 (그 캠페인에 붙은 동의문이 뜬다). 앱을 다시 내지 않고 바꾸려고 서버가 알려 준다 */
    @Value("${push.agreement-code:doodle-app-hungry}")
    private String agreementCode = "doodle-app-hungry";

    public String agreementCode() {
        return agreementCode;
    }

    /** 이 알림을 보낼 콘솔 발송 코드 */
    String codeOf(PushKind kind) {
        for (String pair : (codeOverrides == null ? "" : codeOverrides).split(",")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(kind.code) && !kv[1].isBlank()) return kv[1].trim();
        }
        return codePrefix + kind.code;
    }

    public PushService(TamagotchiRepository tamagotchiRepository, StringRedisTemplate redis, TossPartnerApiClient toss) {
        this.tamagotchiRepository = tamagotchiRepository;
        this.redis = redis;
        this.toss = toss;
    }

    /** 앱이 끄적이 상태를 읽어 갔다 = 지금 보고 있다 */
    public void seen(Long userId) {
        try {
            redis.opsForValue().set(seenKey(userId), String.valueOf(System.currentTimeMillis()), Duration.ofHours(2));
        } catch (Exception e) {
            log.debug("알림: 접속 기록 실패 - {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    @Transactional
    public void tick() {
        if (!enabled) return;
        LocalDateTime now = LocalDateTime.now(KST);
        int sent = 0;
        for (Tamagotchi pet : tamagotchiRepository.findAllByStatus(PetStatus.ACTIVE)) {
            try {
                if (consider(pet, now)) sent++;
            } catch (Exception e) {
                log.warn("알림: 처리 실패 - pet={}: {}", pet.getId(), e.getMessage());
            }
        }
        if (sent > 0) log.info("알림: {}건 보냄", sent);
    }

    /** 한 마리를 보고, 보냈으면 true */
    boolean consider(Tamagotchi pet, LocalDateTime now) {
        if (pet.isDead() || pet.getEvolutionStage() == EvolutionStage.EGG) return false;
        pet.markTrainingBaseline(now);
        List<PushKind> due = PushRules.applicable(snapshot(pet, now), now);
        // 풀린 일은 표시를 지운다 (다시 배고파지면 다시 알린다)
        for (PushKind k : PushKind.values()) {
            if (k.episode == PushKind.Episode.UNTIL_RESOLVED && !due.contains(k)) redis.delete(episodeKey(pet, k));
        }
        User user = pet.getUser();
        if (due.isEmpty() || user == null || !user.isPushAgreed()) return false;
        String anonKey = anonKey(user.getTossUserId());
        if (anonKey == null || seenRecently(user.getId(), now)) return false;
        if (redis.opsForValue().get(cooldownKey(pet)) != null) return false;
        String dayKey = dayKey(pet, now.toLocalDate());
        String countText = redis.opsForValue().get(dayKey);
        if (countText != null && Integer.parseInt(countText) >= PushRules.DAILY_MAX) return false;

        for (PushKind kind : due) {
            if (!PushRules.allowedAt(kind, now)) continue;
            if (redis.opsForValue().get(episodeKey(pet, kind)) != null) continue;
            String petName = inlineParticle.contains(kind.code) ? PushRules.calling(pet.getName()) : PushRules.withParticle(pet.getName(), kind.particle);
            boolean ok = send(anonKey, kind, petName, pet);
            // 실패해도 잠시 쉰다 (설정이 틀렸을 때 5분마다 두드리지 않게). 같은 일은 다시 보내지 않는다
            redis.opsForValue().set(episodeKey(pet, kind), "1", kind.episode == PushKind.Episode.ONCE_PER_STAGE ? Duration.ofDays(60) : Duration.ofDays(3));
            redis.opsForValue().set(cooldownKey(pet), "1", PushRules.COOLDOWN);
            redis.opsForValue().set(dayKey, String.valueOf(countText == null ? 1 : Integer.parseInt(countText) + 1), Duration.ofHours(30));
            return ok;
        }
        return false;
    }

    private boolean send(String anonKey, PushKind kind, String petName, Tamagotchi pet) {
        String code = codeOf(kind);
        try {
            JsonNode res = toss.post(SEND_PATH, Map.of("templateSetCode", code, "context", Map.of("petName", petName)), Map.of("x-anon-key", anonKey));
            boolean ok = "SUCCESS".equals(res.path("resultType").asText());
            if (ok) log.info("알림: 보냄 - pet={}, kind={}", pet.getId(), code);
            else log.warn("알림: 실패 - pet={}, kind={}, 응답={}", pet.getId(), code, res.toString().substring(0, Math.min(300, res.toString().length())));
            return ok;
        } catch (Exception e) {
            log.warn("알림: 실패 - pet={}, kind={}: {}", pet.getId(), code, e.getMessage());
            return false;
        }
    }

    static PushRules.Pet snapshot(Tamagotchi p, LocalDateTime now) {
        return new PushRules.Pet(!p.isDead(), p.getEvolutionStage() == EvolutionStage.EGG, p.isSleeping(), p.isAdventuring(),
                100 - p.getHunger(), p.getStrength(), p.getPoopCount(), p.level(), p.evolutionLevel(), p.isInEvolutionZone(),
                p.isSleepy(now), p.getLastTrainedAt());
    }

    /** 토스 로그인 사용자 ID("toss_" + 익명 키)에서 익명 키를 꺼낸다. 로컬 개발 ID 등은 null */
    static String anonKey(String tossUserId) {
        if (tossUserId == null || !tossUserId.startsWith("toss_") || tossUserId.length() <= 5) return null;
        return tossUserId.substring(5);
    }

    private boolean seenRecently(Long userId, LocalDateTime now) {
        String at = redis.opsForValue().get(seenKey(userId));
        if (at == null) return false;
        return System.currentTimeMillis() - Long.parseLong(at) < PushRules.SEEN_RECENTLY.toMillis();
    }

    private static String seenKey(Long userId) { return "push:seen:" + userId; }
    private static String cooldownKey(Tamagotchi p) { return "push:cool:" + p.getId(); }
    private static String dayKey(Tamagotchi p, LocalDate d) { return "push:day:" + p.getId() + ":" + d; }
    private static String episodeKey(Tamagotchi p, PushKind k) {
        return "push:ep:" + p.getId() + ":" + k.code + (k.episode == PushKind.Episode.ONCE_PER_STAGE ? ":" + p.getEvolutionStage() : "");
    }
}
