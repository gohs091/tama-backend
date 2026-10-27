package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "tamagotchi")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Tamagotchi {

    public static final int INITIAL_HUNGER = 50;
    public static final int INITIAL_STRENGTH = 50;
    public static final int FEED_AMOUNT = 1;  // 고기 1회 포만감 회복량
    public static final int PILL_AMOUNT = 1;  // 알약 1회 파워 회복량

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvolutionStage evolutionStage;

    @Column(nullable = false)
    private int hunger; // 0~100 (0=포만감 최대, 100=굶주림. 포만감 = 100 - hunger)

    @Column(nullable = false)
    private int strength; // 0~100 (트레이닝 가능 조건)

    @Column(nullable = false)
    private int poopCount; // VitalsRule.maxPoop 이상이면 사망

    @Column(nullable = false)
    private int feedCount;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int evoGauge; // 다음 진화를 향해 쌓은 게이지. 가득 찬 값은 EvolutionRules.gaugeMax(단계)

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int pendingEvoGain; // 마지막 훈련으로 받을 수 있게 된 진화 게이지. 광고를 봐야 evoGauge에 더해진다

    private LocalDateTime lastFedAt;
    private LocalDateTime lastPoopAt;
    private LocalDateTime hungryStartedAt; // 포만감이 0(hunger=100)이 된 시각 (사망 판정용)
    private LocalDateTime strengthEmptySince; // 파워가 0이 된 시각 (사망 판정용)
    private LocalDateTime lastHungerTickAt; // 포만감 감소 기준 시각
    private LocalDateTime lastStrengthTickAt; // 파워 감소 기준 시각
    private LocalDateTime deadAt; // 사망 시각 (null이면 생존)
    private LocalDateTime lastVitalsTickAt; // 마지막으로 시간 경과를 반영한 시각 (서버 중단 감지용)
    private LocalDateTime sleptAt; // 마지막 수면 시간 (6시간 체크용)

    @Column(nullable = false)
    private boolean sleeping;

    // 잉크통 충전 중. 이 동안은 잠잘 때처럼 시간이 흐르지 않고 돌보기·훈련을 할 수 없다. 기존 행은 null.
    @Column(name = "bathing")
    private Boolean bathing;

    public boolean isBathing() {
        return Boolean.TRUE.equals(this.bathing);
    }

    public void setBathing(boolean bathing) {
        this.bathing = bathing;
    }

    @Column(length = 50)
    private String faction;

    @Column(columnDefinition = "LONGTEXT")
    private String babyImageBase64;

    @Column(columnDefinition = "LONGTEXT")
    private String childImageBase64;

    @Column(columnDefinition = "LONGTEXT")
    private String adultImageBase64;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void saveFaction(String faction) {
        this.faction = faction;
    }

    public void saveStageImage(String stage, String imageBase64) {
        switch (stage) {
            case "baby"  -> this.babyImageBase64  = imageBase64;
            case "child" -> this.childImageBase64 = imageBase64;
            case "adult" -> this.adultImageBase64 = imageBase64;
        }
    }

    /** 현재 진화 단계에 해당하는 이미지 반환 */
    public String getCurrentImageBase64() {
        return switch (this.evolutionStage) {
            case EGG, BABY -> babyImageBase64;
            case CHILD     -> childImageBase64;
            case ADULT     -> adultImageBase64;
        };
    }

    public boolean isDead() {
        return this.deadAt != null;
    }

    public void feed() {
        this.hunger = Math.max(0, this.hunger - FEED_AMOUNT);
        this.feedCount++;
        this.lastFedAt = LocalDateTime.now();
        this.hungryStartedAt = null; // 굶주림 해소
    }

    public void addStrength(int amount) {
        this.strength = Math.min(100, this.strength + amount);
        if (this.strength > 0) this.strengthEmptySince = null;
    }

    public void consumeForTraining(int hungerCost, int strengthCost) {
        if (this.hunger + hungerCost > 100) {
            throw new IllegalStateException("배가 너무 고파서 트레이닝할 수 없어요.");
        }
        if (this.strength < strengthCost) {
            throw new IllegalStateException("스트렝스가 부족해서 트레이닝할 수 없어요.");
        }
        this.hunger = Math.min(100, this.hunger + hungerCost);
        this.strength = Math.max(0, this.strength - strengthCost);
    }

    public void cleanPoop() {
        this.poopCount = 0;
        this.lastPoopAt = LocalDateTime.now(); // 청소 직후 바로 또 싸지 않도록 간격 재시작
    }

    public void sleep() {
        if (this.sleptAt != null &&
                this.sleptAt.isAfter(LocalDateTime.now().minusHours(6))) {
            throw new IllegalStateException("일어난 지 6시간이 안 됐어요. 싫어요! 😤");
        }
        this.sleeping = true;
        this.sleptAt = LocalDateTime.now();
    }

    public void wakeUp() {
        this.sleeping = false;
    }

    public void evolve(EvolutionStage nextStage) {
        this.evolutionStage = nextStage;
        this.evoGauge = 0;
        this.pendingEvoGain = 0;
    }

    /** 알 → 유체. 알 두드리기는 클라이언트 연출이고, 서버는 단계만 올린다. */
    public void hatch() {
        if (this.evolutionStage != EvolutionStage.EGG) {
            throw new IllegalStateException("알 상태가 아닙니다.");
        }
        evolve(EvolutionStage.BABY);
    }

    /**
     * 훈련 결과로 받을 수 있는 진화 게이지를 대기시킨다. 게이지는 바로 오르지 않고 광고를 본 뒤 claimEvoGain()으로 오른다.
     * 새 훈련을 하면 아직 받지 않은 이전 대기분은 사라진다(훈련 1번 = 광고 1번).
     * 진화할 수 없는 단계(알·완전체)이거나 게이지가 이미 가득 차 있으면 받을 것이 없다.
     */
    public void offerEvoGain(int gain) {
        int max = EvolutionRules.gaugeMax(this.evolutionStage);
        this.pendingEvoGain = (max == 0 || this.evoGauge >= max) ? 0 : gain;
    }

    /** 대기 중인 진화 게이지를 게이지에 더한다(최대 칸 수까지). 실제로 오른 칸 수를 돌려준다. */
    public int claimEvoGain() {
        int before = this.evoGauge;
        addEvoGauge(this.pendingEvoGain);
        this.pendingEvoGain = 0;
        return this.evoGauge - before;
    }

    /** 게이지를 직접 올린다. 진화할 수 없는 단계(알·완전체)에서는 무시한다. */
    public void addEvoGauge(int gain) {
        int max = EvolutionRules.gaugeMax(this.evolutionStage);
        if (max == 0) return;
        this.evoGauge = Math.min(max, this.evoGauge + gain);
    }

    /** 게이지가 가득 차고 컨디션(포만감·파워·응가) 조건을 모두 만족하면 진화할 수 있다. */
    public boolean isEvolutionReady() {
        int max = EvolutionRules.gaugeMax(this.evolutionStage);
        if (max == 0 || isDead()) return false;
        return this.evoGauge >= max
                && (100 - this.hunger) >= EvolutionRules.MIN_SATIATION
                && this.strength >= EvolutionRules.MIN_STRENGTH
                && this.poopCount <= EvolutionRules.MAX_POOP;
    }

    /** 진화 조건을 만족했을 때만 다음 단계로 올린다. */
    public void evolveIfReady() {
        if (!isEvolutionReady()) {
            throw new IllegalStateException("진화 조건을 만족하지 않았어요.");
        }
        evolve(EvolutionRules.next(this.evolutionStage));
    }

    /**
     * 시간 경과 반영: 포만감·파워 감소, 똥 증가, 사망 판정.
     * 알은 대상이 아니고, 수면 중에는 감소/증가 기준 시각만 앞으로 당긴다(수면 중 소진 없음).
     * 포만감/파워가 0인 채 deathAfterEmpty가 지나거나 똥이 maxPoop 이상이면 사망한다.
     */
    public void applyTime(LocalDateTime now, VitalsRule rule) {
        if (isDead() || this.evolutionStage == EvolutionStage.EGG) return;

        absorbDowntime(now, rule);

        if (this.sleeping || isBathing()) {
            this.lastHungerTickAt = now;
            this.lastStrengthTickAt = now;
            this.lastPoopAt = now;
        } else {
            decayHunger(now, rule);
            decayStrength(now, rule);
            growPoop(now, rule);
        }
        syncEmptyMarkers(now);
        checkDeath(now, rule);
        this.lastVitalsTickAt = now;
    }

    /**
     * 서버가 꺼져 있던 시간은 다마고치에게 흐르지 않은 것으로 본다.
     * 틱 간격이 maxTickGap을 넘으면 모든 기준 시각과 사망 타이머를 그 간격만큼 뒤로 민다.
     * (재시작 직후 밀린 시간이 한꺼번에 반영되어 죽는 일을 막는다)
     */
    private void absorbDowntime(LocalDateTime now, VitalsRule rule) {
        if (this.lastVitalsTickAt == null) return;
        Duration gap = Duration.between(this.lastVitalsTickAt, now);
        if (gap.compareTo(rule.maxTickGap()) <= 0) return;

        this.lastHungerTickAt = shift(this.lastHungerTickAt, gap);
        this.lastStrengthTickAt = shift(this.lastStrengthTickAt, gap);
        this.lastPoopAt = shift(this.lastPoopAt, gap);
        this.hungryStartedAt = shift(this.hungryStartedAt, gap);
        this.strengthEmptySince = shift(this.strengthEmptySince, gap);
    }

    private static LocalDateTime shift(LocalDateTime t, Duration d) {
        return t == null ? null : t.plus(d);
    }

    private void decayHunger(LocalDateTime now, VitalsRule rule) {
        if (this.lastHungerTickAt == null) {
            this.lastHungerTickAt = now;
            return;
        }
        long points = Duration.between(this.lastHungerTickAt, now).toMinutes() / rule.satietyMinutesPerPoint();
        if (points <= 0) return;
        this.lastHungerTickAt = this.lastHungerTickAt.plusMinutes(points * rule.satietyMinutesPerPoint());
        this.hunger = (int) Math.min(100, this.hunger + points);
    }

    private void decayStrength(LocalDateTime now, VitalsRule rule) {
        if (this.lastStrengthTickAt == null) {
            this.lastStrengthTickAt = now;
            return;
        }
        long points = Duration.between(this.lastStrengthTickAt, now).toMinutes() / rule.strengthMinutesPerPoint();
        if (points <= 0) return;
        this.lastStrengthTickAt = this.lastStrengthTickAt.plusMinutes(points * rule.strengthMinutesPerPoint());
        this.strength = (int) Math.max(0, this.strength - points);
    }

    private void growPoop(LocalDateTime now, VitalsRule rule) {
        if (this.lastPoopAt == null) {
            this.lastPoopAt = now;
            return;
        }
        long count = Duration.between(this.lastPoopAt, now).toMinutes() / rule.poopIntervalMinutes();
        if (count <= 0) return;
        this.lastPoopAt = this.lastPoopAt.plusMinutes(count * rule.poopIntervalMinutes());
        this.poopCount = (int) Math.min(rule.maxPoop(), this.poopCount + count);
    }

    /** 0 상태가 시작된 시각을 기록/해제 */
    private void syncEmptyMarkers(LocalDateTime now) {
        if (this.hunger >= 100) {
            if (this.hungryStartedAt == null) this.hungryStartedAt = now;
        } else {
            this.hungryStartedAt = null;
        }
        if (this.strength <= 0) {
            if (this.strengthEmptySince == null) this.strengthEmptySince = now;
        } else {
            this.strengthEmptySince = null;
        }
    }

    private void checkDeath(LocalDateTime now, VitalsRule rule) {
        LocalDateTime limit = now.minus(rule.deathAfterEmpty());
        boolean starved = this.hungryStartedAt != null && !this.hungryStartedAt.isAfter(limit);
        boolean exhausted = this.strengthEmptySince != null && !this.strengthEmptySince.isAfter(limit);
        boolean poopFull = this.poopCount >= rule.maxPoop();
        if (starved || exhausted || poopFull) {
            this.deadAt = now;
        }
    }

    public void reset() {
        this.evolutionStage = EvolutionStage.EGG;
        this.hunger = 50;
        this.strength = INITIAL_STRENGTH;
        this.poopCount = 0;
        this.feedCount = 0;
        this.evoGauge = 0;
        this.pendingEvoGain = 0;
        this.lastFedAt = null;
        this.lastPoopAt = null;
        this.hungryStartedAt = null;
        this.strengthEmptySince = null;
        this.lastHungerTickAt = null;
        this.lastStrengthTickAt = null;
        this.lastVitalsTickAt = null;
        this.deadAt = null;
        this.sleeping = false;
        this.bathing = false;
        this.sleptAt = null;
        // 이전 캐릭터 이미지는 새 그림으로 교체되므로 비운다
        this.babyImageBase64 = null;
        this.childImageBase64 = null;
        this.adultImageBase64 = null;
    }
}
