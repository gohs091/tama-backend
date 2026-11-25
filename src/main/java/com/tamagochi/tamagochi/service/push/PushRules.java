package com.tamagochi.tamagochi.service.push;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 어떤 알림이 지금 해당하는지, 지금 보내도 되는지 정한다 (DB·Redis 없이 값만 보고 정해서 시험하기 쉽다).
 */
public final class PushRules {

    /** 포만감(100이 가득)이 이 이하면 배고프다 */
    public static final int HUNGRY_SATIATION = 20;
    /** 파워가 이 이하면 아프다 */
    public static final int SICK_STRENGTH = 20;
    /** 응가가 이만큼 쌓이면 알린다: 지저분함 디버프가 시작되는 개수와 같다 (완성작 전에는 13개면 떠난다: 한 시간에 하나씩 쌓이니 8시간쯤 남는다) */
    public static final int POOP_WARN = com.tamagochi.tamagochi.domain.tamagotchi.DebuffRules.DIRTY_AT;
    /** 트레이닝을 이만큼 안 하면 심심해한다 */
    public static final Duration BORED_AFTER = Duration.ofHours(24);
    /** 앱을 이 안에 봤으면 보내지 않는다 (보고 있는 사람에게 알림은 소음이다) */
    public static final Duration SEEN_RECENTLY = Duration.ofMinutes(30);
    /** 알림끼리 이만큼은 띄운다 */
    public static final Duration COOLDOWN = Duration.ofHours(3);
    /** 하루에 이보다 많이 보내지 않는다 (곧 떠나요는 예외). 시끄러우면 알림을 꺼 버려서 곧 떠나요도 못 받는다 */
    public static final int DAILY_MAX = 2;
    /** 이대로 두면 이 안에 떠나면 "곧 떠나요"를 보낸다 (5분마다 보니 늦어도 1시간 25분 전에는 간다) */
    public static final Duration DYING_WITHIN = Duration.ofMinutes(90);
    /** 곧 떠나요를 보내기 시작하는 시각. 자정부터 이 시각까지는 깨우지 않는다 */
    public static final int DYING_FROM_HOUR = 7;
    /** 이 시각부터 자정까지는, 밤사이(아침 알림이 닿기 전)에 떠날 끄적이를 미리 알린다 */
    public static final int OVERNIGHT_WARN_FROM_HOUR = 21;
    /** 아침 첫 알림을 받고도 이만큼은 남아야 한다. 그보다 일찍 떠날 끄적이는 전날 저녁에 알린다 */
    public static final Duration MORNING_MARGIN = Duration.ofHours(1);
    /** 보내기에 실패한 알림은 이만큼 쉬었다가 다시 해 본다 (설정이 틀렸을 때 5분마다 두드리지 않게) */
    public static final Duration RETRY_AFTER_FAIL = Duration.ofHours(1);

    private PushRules() {
    }

    /** 알림을 정하는 데 쓰는 끄적이 상태. lifeLeft: 이대로 두면 떠나기까지 남은 시간 (떠날 일이 없으면 null) */
    public record Pet(boolean alive, boolean egg, boolean sleeping, boolean adventuring,
                      int satiation, int strength, int poop, int level, int evolutionLevel, boolean inEvolutionZone,
                      boolean sleepy, LocalDateTime lastTrainedAt, Duration lifeLeft) {
    }

    /** 쉬는 시간·하루 횟수와 상관없이 보내는 알림 */
    public static boolean urgent(PushKind kind) {
        return kind == PushKind.DYING;
    }

    /** 지금 해당하는 알림 (보낼 순서대로) */
    public static List<PushKind> applicable(Pet p, LocalDateTime now) {
        List<PushKind> out = new ArrayList<>();
        if (!p.alive() || p.egg() || p.adventuring()) return out;
        if (p.sleeping()) return out;   // 자는 동안은 깨우지 않는다 (자는 동안은 배고픔·응가도 천천히 온다)
        if (dying(p.lifeLeft(), now)) out.add(PushKind.DYING);
        if (p.poop() >= POOP_WARN) out.add(PushKind.POOP);
        if (p.satiation() <= HUNGRY_SATIATION) out.add(PushKind.HUNGRY);
        if (p.strength() <= SICK_STRENGTH) out.add(PushKind.SICK);
        if (p.inEvolutionZone()) out.add(PushKind.EVO_READY);
        if (p.evolutionLevel() > 0 && p.level() == p.evolutionLevel() - 1) out.add(PushKind.EVO_SOON);
        if (p.sleepy()) out.add(PushKind.SLEEPY);
        if (p.lastTrainedAt() != null && !p.lastTrainedAt().isAfter(now.minus(BORED_AFTER))) out.add(PushKind.BORED);
        return out;
    }

    /**
     * 곧 떠나요에 해당하는지: 떠나기까지 1시간 30분 이하이거나, 저녁(21시~자정)인데 내일 아침 8시 전에 떠날 때.
     * 자정~7시에는 알림을 보내지 않으니, 그 사이에 떠날 끄적이는 자기 전에 미리 알려야 한다
     * (아침에 밥을 주고 하루를 보낸 끄적이는 새벽에 떠나서, 이 규칙이 없으면 알림 없이 떠난다)
     */
    static boolean dying(Duration lifeLeft, LocalDateTime now) {
        if (lifeLeft == null) return false;
        if (lifeLeft.compareTo(DYING_WITHIN) <= 0) return true;
        if (now.getHour() < OVERNIGHT_WARN_FROM_HOUR) return false;
        LocalDateTime morning = now.toLocalDate().plusDays(1).atTime(DYING_FROM_HOUR, 0);
        return now.plus(lifeLeft).isBefore(morning.plus(MORNING_MARGIN));
    }

    /**
     * 이 시각에 이 알림을 보내도 되는지. 밤(22시~8시)에는 보내지 않는다. 졸림은 저녁(20~23시)에만 보낸다.
     * 곧 떠나요는 7시~자정에 보낸다 (늦은 밤에도 떠나기 전에 한 번은 알린다. 한밤중에 깨우지는 않는다)
     */
    public static boolean allowedAt(PushKind kind, LocalDateTime now) {
        int h = now.getHour();
        if (kind == PushKind.DYING) return h >= DYING_FROM_HOUR;
        if (kind == PushKind.SLEEPY) return h >= 20 && h < 23;
        return h >= 8 && h < 22;
    }

    /**
     * 템플릿에 조사가 이미 적혀 있을 때("{{petName}}가")의 이름: 받침이 있으면 "이"를 붙여 부르는 이름으로 만든다 ("용" → "용이", "냥냥이" → "냥냥이").
     * 그러면 "용이가", "용이의"처럼 자연스럽게 이어진다
     */
    public static String calling(String name) {
        String n = name == null || name.isBlank() ? "끄적이" : name.trim();
        char last = n.charAt(n.length() - 1);
        boolean batchim = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
        return batchim ? n + "이" : n;
    }

    /** 이름 뒤에 조사를 붙인다. 한글 마지막 글자에 받침이 있으면 "이"/"의", 없으면 "가"/"의". 한글이 아니면 받침이 없다고 본다 */
    public static String withParticle(String name, PushKind.Particle particle) {
        String n = name == null || name.isBlank() ? "끄적이" : name.trim();
        if (particle == PushKind.Particle.POSSESSIVE) return n + "의";
        char last = n.charAt(n.length() - 1);
        boolean batchim = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
        return n + (batchim ? "이" : "가");
    }
}
