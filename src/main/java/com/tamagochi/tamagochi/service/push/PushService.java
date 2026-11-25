package com.tamagochi.tamagochi.service.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.PetStatus;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.VitalsRule;
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
 * - 밤(22시~8시), 마지막 알림에서 3시간 안, 오늘 이미 2번 보냈을 때 (곧 떠나요는 7시~자정이면 이 셋과 상관없이 보낸다)
 * - 같은 일로 이미 보냈을 때 (배고픔이면 밥을 먹어 풀릴 때까지, 진화면 단계마다 한 번)
 * 곧 떠나요는 제 캠페인이 없다 ("곧 떠나요" 문구가 검수에서 거절됨): 떠나는 까닭에 맞는 알림의 문구를 빌려 보낸다 (templateOf)
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
    /** 생존 규칙: 떠나기까지 남은 시간을 셀 때 쓴다 */
    private final VitalsRule vitals;

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

    /** 보내는 알림 종류 (쉼표로, 예: hungry,sick). 콘솔 캠페인이 승인된 것만 적는다. 비우면 모두 */
    @Value("${push.kinds:}")
    private String enabledKinds = "";

    boolean kindEnabled(PushKind kind) {
        if (enabledKinds == null || enabledKinds.isBlank()) return true;
        for (String k : enabledKinds.split(",")) if (k.trim().equals(kind.code)) return true;
        return false;
    }

    /** 앱이 알림 동의 창을 띄울 때 쓰는 캠페인 코드 (그 캠페인에 붙은 동의문이 뜬다). 앱을 다시 내지 않고 바꾸려고 서버가 알려 준다 */
    @Value("${push.agreement-code:doodle-app-hungry}")
    private String agreementCode = "doodle-app-hungry";

    public String agreementCode() {
        return agreementCode;
    }

    /** 이 알림을 보낼 콘솔 발송 코드 */
    String codeOf(PushKind kind) {
        String override = overrideOf(kind);
        return override != null ? override : codePrefix + kind.code;
    }

    /** push.codes에 따로 적어 둔 발송 코드 (없으면 null) */
    private String overrideOf(PushKind kind) {
        for (String pair : (codeOverrides == null ? "" : codeOverrides).split(",")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(kind.code) && !kv[1].isBlank()) return kv[1].trim();
        }
        return null;
    }

    /**
     * 이 알림을 어떤 캠페인의 문구로 보낼지. 곧 떠나요만 다르다: "곧 떠나요" 문구는 검수에서 거절돼 제 캠페인이 없으므로,
     * 떠나는 까닭에 맞는 알림(굶주림 → 배고픔, 파워 → 아픔, 응가 → 응가)의 캠페인으로 보낸다.
     * 그 알림이 꺼져 있으면(승인된 캠페인이 없으면) 보내지 않는다(null).
     * 나중에 곧 떠나요 전용 캠페인이 승인되면 push.codes에 dying=코드 를 적는다. 그러면 그 캠페인으로 보낸다
     */
    PushKind templateOf(PushKind kind, Tamagotchi pet, LocalDateTime now) {
        if (kind != PushKind.DYING || overrideOf(kind) != null) return kind;
        Tamagotchi.LifeEnd end = vitals == null ? null : pet.lifeEnd(now, vitals);
        if (end == null) return null;
        PushKind as = switch (end.cause()) {
            case STARVE -> PushKind.HUNGRY;
            case EXHAUST -> PushKind.SICK;
            case POOP -> PushKind.POOP;
        };
        return kindEnabled(as) ? as : null;
    }

    public PushService(TamagotchiRepository tamagotchiRepository, StringRedisTemplate redis, TossPartnerApiClient toss, VitalsRule vitals) {
        this.tamagotchiRepository = tamagotchiRepository;
        this.redis = redis;
        this.toss = toss;
        this.vitals = vitals;
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
        List<PushKind> due = PushRules.applicable(snapshot(pet, now, vitals), now);
        // 풀린 일은 표시를 지운다 (다시 배고파지면 다시 알린다)
        for (PushKind k : PushKind.values()) {
            if (k.episode == PushKind.Episode.UNTIL_RESOLVED && !due.contains(k)) redis.delete(episodeKey(pet, k));
        }
        User user = pet.getUser();
        if (due.isEmpty() || user == null || !user.isPushAgreed()) return false;
        String anonKey = anonKey(user.getTossUserId());
        if (anonKey == null || seenRecently(user.getId(), now)) return false;
        boolean cooling = redis.opsForValue().get(cooldownKey(pet)) != null;
        String dayKey = dayKey(pet, now.toLocalDate());
        String countText = redis.opsForValue().get(dayKey);
        int count = countText == null ? 0 : Integer.parseInt(countText);
        boolean dailyFull = count >= PushRules.DAILY_MAX;

        for (PushKind kind : due) {
            if (!kindEnabled(kind) || !PushRules.allowedAt(kind, now)) continue;
            if (!PushRules.urgent(kind) && (cooling || dailyFull)) continue;   // 곧 떠나요만 쉬는 시간·하루 횟수를 넘는다
            if (redis.opsForValue().get(episodeKey(pet, kind)) != null) continue;
            if (redis.opsForValue().get(failKey(pet, kind)) != null) continue;
            PushKind as = templateOf(kind, pet, now);   // 곧 떠나요는 까닭에 맞는 알림의 문구로 보낸다
            if (as == null) continue;
            String petName = inlineParticle.contains(as.code) ? PushRules.calling(pet.getName()) : PushRules.withParticle(pet.getName(), as.particle);
            if (!send(anonKey, as, petName, pet)) {
                // 못 보냈으면 보낸 것으로 치지 않는다: 같은 일·쉬는 시간·하루 횟수를 남기지 않고, 이 알림만 잠시 쉬었다가 다시 해 본다
                redis.opsForValue().set(failKey(pet, kind), "1", PushRules.RETRY_AFTER_FAIL);
                return false;
            }
            redis.opsForValue().set(episodeKey(pet, kind), "1", kind.episode == PushKind.Episode.ONCE_PER_STAGE ? Duration.ofDays(60) : Duration.ofDays(3));
            // 남의 문구를 빌려 보냈으면 그 알림도 보낸 것으로 친다 (같은 문구가 곧바로 또 가지 않게)
            if (as != kind) redis.opsForValue().set(episodeKey(pet, as), "1", Duration.ofDays(3));
            if (as != kind) log.info("알림: 곧 떠나요를 {} 문구로 보냄 - pet={}", as.code, pet.getId());
            redis.opsForValue().set(cooldownKey(pet), "1", PushRules.COOLDOWN);
            redis.opsForValue().set(dayKey, String.valueOf(count + 1), Duration.ofHours(30));
            return true;
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

    static PushRules.Pet snapshot(Tamagotchi p, LocalDateTime now, VitalsRule vitals) {
        return new PushRules.Pet(!p.isDead(), p.getEvolutionStage() == EvolutionStage.EGG, p.isSleeping(), p.isAdventuring(),
                100 - p.getHunger(), p.getStrength(), p.getPoopCount(), p.level(), p.evolutionLevel(), p.isInEvolutionZone(),
                p.isSleepy(now), p.getLastTrainedAt(), vitals == null ? null : p.lifeLeft(now, vitals));
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
    private static String failKey(Tamagotchi p, PushKind k) { return "push:fail:" + p.getId() + ":" + k.code; }
    /**
     * 단계마다 한 번인 알림(진화)은 끄적이(publicId)까지 넣는다: 새 알로 다시 시작하면 행 ID는 그대로지만 새 끄적이라
     * 같은 단계에서 다시 알려야 한다
     */
    static String episodeKey(Tamagotchi p, PushKind k) {
        return "push:ep:" + p.getId() + ":" + k.code
                + (k.episode == PushKind.Episode.ONCE_PER_STAGE ? ":" + p.getPublicId() + ":" + p.getEvolutionStage() : "");
    }
}
