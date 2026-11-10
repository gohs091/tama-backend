package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Entity
@Table(name = "tamagotchi", indexes = @Index(name = "idx_tamagotchi_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Tamagotchi {

    public static final int INITIAL_HUNGER = 50;
    public static final int INITIAL_STRENGTH = 50;
    public static final int FEED_AMOUNT = 3;  // 고기 1회 포만감 회복량
    public static final int PILL_AMOUNT = 3;  // 알약 1회 파워 회복량
    public static final String DEFAULT_NAME = "끄적이";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 한 유저가 여러 마리를 가질 수 있게 다대일. 지금 키우는 끄적이는 status = ACTIVE인 가장 최근 행이다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    /**
     * 밖으로 보이는 끄적이 ID (무작위 32자). S3 그림 경로·배틀 등 외부에는 DB 번호나 tossUserId 대신 이 값만 쓴다.
     * 새로 시작(reset)하면 새 끄적이로 보고 새 값을 받는다. 예전 행은 null이었다가 서버가 켜질 때 채운다 (PetIdentityMigration).
     */
    @Column(name = "public_id", length = 32, unique = true)
    @Builder.Default
    private String publicId = newPublicId();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) not null default 'ACTIVE'")
    @Builder.Default
    private PetStatus status = PetStatus.ACTIVE;

    public static String newPublicId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** 예전 행 채우기용 */
    public boolean assignPublicIdIfMissing() {
        if (this.publicId != null) return false;
        this.publicId = newPublicId();
        return true;
    }

    public void rename(String name) {
        this.name = name;
    }

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

    // 예전 진화 게이지. 레벨이 생기기 전 행의 레벨을 정할 때만 읽는다 (ensureLevel). 새로 쌓지 않는다
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int evoGauge;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int pendingEvoGain;

    // 레벨·경험치·무게·위력 (규칙은 LevelRules). 기존 행은 null이고, 처음 읽거나 바꿀 때 ensureLevel()이 채운다
    @Column(name = "level")
    private Integer level;
    @Column(name = "exp")
    private Integer exp;               // 지금 레벨에서 쌓은 경험치
    @Column(name = "stage_start_level")
    private Integer stageStartLevel;   // 지금 단계를 시작한 레벨. 진화 가능 구간은 여기서 LevelRules.zoneSpan만큼 위다
    @Column(name = "pending_exp")
    private Integer pendingExp;        // 마지막 훈련으로 받을 수 있게 된 경험치. 광고를 봐야 받는다
    @Column(name = "weight_dg")
    private Integer weightDg;          // 무게 (dg, 데이터그램)
    @Column(name = "might")
    private Integer might;             // 위력. 레벨 업마다 크게, 트레이닝마다 조금 오른다. 스파링 승패의 가장 큰 변수
    @Column(name = "spar_wins")
    private Integer sparWins;          // 스파링 승 (기존 행은 null = 0)
    @Column(name = "spar_losses")
    private Integer sparLosses;

    // 만나서 배틀(친구와 QR 맞대기) 전적. 이번 단계에서만 센다 (진화하면 0부터, 진화할 때 쓴다). 기존 행은 null = 0
    @Column(name = "battle_wins")
    private Integer battleWins;
    @Column(name = "battle_losses")
    private Integer battleLosses;

    private LocalDateTime lastFedAt;
    private LocalDateTime lastPoopAt;
    private LocalDateTime hungryStartedAt; // 포만감이 0(hunger=100)이 된 시각 (사망 판정용)
    private LocalDateTime strengthEmptySince; // 파워가 0이 된 시각 (사망 판정용)
    private LocalDateTime lastHungerTickAt; // 포만감 감소 기준 시각
    private LocalDateTime lastStrengthTickAt; // 파워 감소 기준 시각
    private LocalDateTime deadAt; // 사망 시각 (null이면 생존)
    private LocalDateTime lastVitalsTickAt; // 마지막으로 시간 경과를 반영한 시각 (서버 중단 감지용)
    private LocalDateTime sleptAt; // 마지막으로 불을 끈(잠든) 시각

    @Column(nullable = false)
    private boolean sleeping;

    // 자는 모습 이미지를 만든 단계 (비트: BABY 1, CHILD 2, ADULT 4). 하나라도 있으면 값을 낸 것이라 다른 단계는 무료다. 죽어서 새로 시작하면 비운다.
    @Column(name = "sleep_image_mask")
    private Integer sleepImageMask;

    // 자는 모습을 무료로 다시 만든 단계 (같은 비트). 이미지는 기기에만 저장되어, 만드는 도중 앱을 닫거나 저장 공간이 모자라면
    // 값은 냈는데 이미지가 없을 수 있다. 그때 단계마다 한 번 무료로 다시 만들 수 있다. 죽어서 새로 시작하면 비운다.
    @Column(name = "sleep_image_redo_mask")
    private Integer sleepImageRedoMask;

    private static int sleepBit(EvolutionStage stage) {
        return switch (stage) {
            case BABY -> 1;
            case CHILD -> 2;
            case ADULT -> 4;
            default -> 0;
        };
    }

    public boolean isSleepImageUnlocked() {
        return this.sleepImageMask != null && this.sleepImageMask != 0;
    }

    // 자는 모습을 잉크로 샀는가. null이면 예전 행: 그때는 잉크로만 살 수 있었으니 하나라도 만들었으면 잉크로 산 것이다.
    // 화가 회원으로 만들면 false로 둔다 (환불되면 거둬야 하고, 해지 뒤에는 새로 만들 수 없다)
    @Column(name = "sleep_image_ink_paid")
    private Boolean sleepImageInkPaid;

    /** 잉크로 산 자는 모습이 있다: 회원이 아니어도 다음 단계를 계속 무료로 만든다 */
    public boolean isSleepImagePaidByInk() {
        return this.sleepImageInkPaid == null ? isSleepImageUnlocked() : this.sleepImageInkPaid;
    }

    /** 화가 회원으로 자는 모습을 만들기 시작했다 (잉크로 산 적이 없을 때만 표시한다) */
    public void markSleepImageByMember() {
        if (!isSleepImagePaidByInk()) this.sleepImageInkPaid = false;
    }

    /** 화가 회원이 환불·회수되면 회원으로 만든 자는 모습 기록을 거둔다 (잉크로 산 것은 그대로) */
    public void revokeMemberSleepImages() {
        if (isSleepImagePaidByInk()) return;
        this.sleepImageMask = 0;
        this.sleepImageRedoMask = 0;
    }

    public boolean hasSleepImage(EvolutionStage stage) {
        return this.sleepImageMask != null && (this.sleepImageMask & sleepBit(stage)) != 0;
    }

    /** 이 단계의 자는 모습 만들기를 시작했다고 기록한다 (같은 단계를 두 번 만들 수 없다) */
    public void markSleepImage(EvolutionStage stage) {
        this.sleepImageMask = (this.sleepImageMask == null ? 0 : this.sleepImageMask) | sleepBit(stage);
    }

    /** 만들기에 실패했을 때 되돌린다 */
    public void clearSleepImage(EvolutionStage stage) {
        this.sleepImageMask = (this.sleepImageMask == null ? 0 : this.sleepImageMask) & ~sleepBit(stage);
    }

    public boolean hasSleepImageRedo(EvolutionStage stage) {
        return this.sleepImageRedoMask != null && (this.sleepImageRedoMask & sleepBit(stage)) != 0;
    }

    /** 이 단계의 자는 모습을 무료로 다시 만들기 시작했다고 기록한다 (단계마다 한 번) */
    public void markSleepImageRedo(EvolutionStage stage) {
        this.sleepImageRedoMask = (this.sleepImageRedoMask == null ? 0 : this.sleepImageRedoMask) | sleepBit(stage);
    }

    /** 다시 만들기에 실패했을 때 되돌린다 (다시 시도할 수 있게) */
    public void clearSleepImageRedo(EvolutionStage stage) {
        this.sleepImageRedoMask = (this.sleepImageRedoMask == null ? 0 : this.sleepImageRedoMask) & ~sleepBit(stage);
    }

    /** 무료로 다시 만들 수 있는 단계: 이미 만들었고 아직 다시 만들지 않은 단계 */
    public java.util.List<String> sleepImageRedoStages() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (EvolutionStage st : new EvolutionStage[]{EvolutionStage.BABY, EvolutionStage.CHILD, EvolutionStage.ADULT}) {
            if (hasSleepImage(st) && !hasSleepImageRedo(st)) out.add(st.name());
        }
        return out;
    }

    public java.util.List<String> sleepImageStages() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (EvolutionStage st : new EvolutionStage[]{EvolutionStage.BABY, EvolutionStage.CHILD, EvolutionStage.ADULT}) {
            if (hasSleepImage(st)) out.add(st.name());
        }
        return out;
    }

    // 졸린 시간대: 끄적이마다 밤 9시~새벽 1시 중 한 시각에 시작해 8시간. 이 동안에만 불을 끄면 잠든다. 기존 행은 null(id로 정한다).
    public static final int[] SLEEPY_START_HOURS = {21, 22, 23, 0, 1};
    public static final int SLEEPY_HOURS = 8;
    // 자는 동안 포만감·파워는 이 배만큼 느리게 줄고, 응가는 멈춘다
    public static final int SLEEP_DECAY_SLOWDOWN = 3;
    @Column(name = "sleepy_start_hour")
    private Integer sleepyStartHour;

    public int sleepyStartHour() {
        if (this.sleepyStartHour != null) return this.sleepyStartHour;
        long seed = this.id == null ? 0 : this.id;
        return SLEEPY_START_HOURS[(int) Math.floorMod(seed, (long) SLEEPY_START_HOURS.length)];
    }

    /** 지금이 이 끄적이의 졸린 시간대인가 */
    public boolean isSleepyTime(LocalDateTime now) {
        return Math.floorMod(now.getHour() - sleepyStartHour(), 24) < SLEEPY_HOURS;
    }

    /** 졸린 시간대인데 아직 깨어 있다 (불을 꺼 달라고 보챈다) */
    public boolean isSleepy(LocalDateTime now) {
        return !this.sleeping && !isDead() && this.evolutionStage != EvolutionStage.EGG && isSleepyTime(now);
    }

    public static int randomSleepyStartHour() {
        return SLEEPY_START_HOURS[ThreadLocalRandom.current().nextInt(SLEEPY_START_HOURS.length)];
    }

    // 잉크통 충전 중. 이 동안은 잠잘 때처럼 시간이 흐르지 않고 돌보기·훈련을 할 수 없다. 기존 행은 null.
    @Column(name = "bathing")
    private Boolean bathing;

    public boolean isBathing() {
        return Boolean.TRUE.equals(this.bathing);
    }

    public void setBathing(boolean bathing) {
        this.bathing = bathing;
    }

    // 끄적랜드 모험 중. 잉크통과 같이 시간이 흐르지 않고 돌보기·훈련을 할 수 없다. 기존 행은 null.
    @Column(name = "adventuring")
    private Boolean adventuring;

    public boolean isAdventuring() {
        return Boolean.TRUE.equals(this.adventuring);
    }

    public void setAdventuring(boolean adventuring) {
        this.adventuring = adventuring;
    }

    // 다음 진화에 반영할 대표 잉크 (끄적랜드 보관함에서 고른다). 모두 비어 있어도 된다. 진화하면 비운다.
    @Column(name = "ink_main", length = 40)
    private String inkMain;      // 특정 잉크 1칸: 무엇이 되는가
    @Column(name = "ink_family1", length = 40)
    private String inkFamily1;   // 계열 잉크 2칸: 어떤 느낌인가
    @Column(name = "ink_family2", length = 40)
    private String inkFamily2;

    // 전설 잉크로 진화한 적이 있으면 끄적이 둘레에 금테 + 반짝이를 바른다 (앱 연출). 한 번 켜지면 이 끄적이는 계속 켜져 있다. 기존 행은 null
    @Column(name = "legend_glow")
    private Boolean legendGlow;

    public boolean legendGlow() {
        return Boolean.TRUE.equals(legendGlow);
    }

    public void setInkLoadout(String main, String family1, String family2) {
        this.inkMain = main;
        this.inkFamily1 = family1;
        this.inkFamily2 = family2;
    }

    // 갓 태어난 끄적이가 마음에 안 들 때 다시 그리기. 하루 횟수를 센다 (reset으로 지우지 않는다). 기존 행은 null.
    public static final int REDRAWS_PER_DAY = 2;
    @Column(name = "redraw_date")
    private LocalDate redrawDate;
    @Column(name = "redraw_count")
    private Integer redrawCount;

    // 진화 후보 모습 다시 그리기. 후보는 구간에 들어설 때 한 번만 그리고, 유저가 누를 때만 다시 그린다 (이미지 비용). 하루 횟수를 센다
    public static final int CANDIDATE_REDRAWS_PER_DAY = 2;
    @Column(name = "candidate_redraw_date")
    private LocalDate candidateRedrawDate;
    @Column(name = "candidate_redraw_count")
    private Integer candidateRedrawCount;

    public int candidateRedrawsLeft(LocalDate today, int perDay) {
        int used = today.equals(this.candidateRedrawDate) && this.candidateRedrawCount != null ? this.candidateRedrawCount : 0;
        return Math.max(0, perDay - used);
    }

    /** 진화 가능 구간에서만, 하루 횟수 안에서 후보 다시 그리기를 하나 쓴다 */
    public void useCandidateRedraw(LocalDate today, int perDay) {
        if (!isInEvolutionZone()) throw new IllegalStateException("진화할 수 있을 때만 다시 그릴 수 있어요.");
        int left = candidateRedrawsLeft(today, perDay);
        if (left <= 0) throw new IllegalStateException("오늘은 더 다시 그릴 수 없어요. 내일 다시 해 주세요.");
        this.candidateRedrawCount = perDay - left + 1;
        this.candidateRedrawDate = today;
    }

    public int redrawsLeft(LocalDate today) {
        int used = today.equals(this.redrawDate) && this.redrawCount != null ? this.redrawCount : 0;
        return Math.max(0, REDRAWS_PER_DAY - used);
    }

    /** 낙서일 때만, 하루 횟수 안에서 알로 되돌린다. 그림은 클라이언트가 다시 그린다 */
    public void redraw(LocalDate today) {
        if (this.evolutionStage != EvolutionStage.BABY || isDead()) {
            throw new IllegalStateException("갓 태어난 끄적이만 다시 그릴 수 있어요.");
        }
        int left = redrawsLeft(today);
        if (left <= 0) throw new IllegalStateException("오늘은 더 다시 그릴 수 없어요. 내일 다시 해 주세요.");
        this.redrawCount = REDRAWS_PER_DAY - left + 1;
        this.redrawDate = today;
        reset();
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

    // S3에 올린 그림의 키 (CDN 주소는 서버 설정으로 붙인다). 키가 생기면 같은 단계의 base64는 지운다
    @Column(name = "baby_image_key", length = 200)
    private String babyImageKey;

    @Column(name = "child_image_key", length = 200)
    private String childImageKey;

    @Column(name = "adult_image_key", length = 200)
    private String adultImageKey;

    // 유저가 그린 원본 그림. 폰 저장소가 지워져도 이걸로 되살리고 다음 진화를 만든다
    @Column(name = "sketch_image_key", length = 200)
    private String sketchImageKey;

    /** S3에 올린 그림의 키를 저장하고, 같은 단계의 base64 원본은 DB에서 지운다 */
    public void saveStageImageKey(String stage, String key) {
        switch (stage) {
            case "baby"  -> { this.babyImageKey = key;  this.babyImageBase64 = null; }
            case "child" -> { this.childImageKey = key; this.childImageBase64 = null; }
            case "adult" -> { this.adultImageKey = key; this.adultImageBase64 = null; }
            case "sketch" -> this.sketchImageKey = key;
            default -> throw new IllegalArgumentException("알 수 없는 단계예요.");
        }
    }

    /** 현재 진화 단계 그림의 S3 키 (없으면 null) */
    // 배틀 기술: 단계마다 한 번, 그 단계 그림을 보고 AI가 정한다 (BattleSkillService). 틀은 앱의 입자 연출 18가지 중 하나
    @Column(name = "skill_template", length = 20)
    private String skillTemplate;
    @Column(name = "skill_name", length = 40)
    private String skillName;
    @Column(name = "skill_colors", length = 40)
    private String skillColors;   // "#rrggbb,#rrggbb,#rrggbb" (주색, 보조색, 밝은 강조색)
    @Column(name = "skill_particle", length = 12)
    private String skillParticle;
    @Column(name = "skill_stage", length = 10)
    private String skillStage;    // 이 기술을 정한 단계. 진화하면 새 모습으로 다시 정한다

    public boolean hasSkillForCurrentStage() {
        return skillTemplate != null && evolutionStage != null && evolutionStage.name().equals(skillStage);
    }

    public void applySkill(String template, String name, String colors, String particle) {
        this.skillTemplate = template;
        this.skillName = name;
        this.skillColors = colors;
        this.skillParticle = particle;
        this.skillStage = evolutionStage == null ? null : evolutionStage.name();
    }

    public String getCurrentImageKey() {
        return switch (this.evolutionStage) {
            case EGG, BABY -> babyImageKey;
            case CHILD     -> childImageKey;
            case ADULT     -> adultImageKey;
        };
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

    /** 포만감이 가득(100)이면 더 먹지 않는다 (잉크를 쓰기 전에 확인한다) */
    public boolean isFull() {
        return this.hunger <= 0;
    }

    public void feed() {
        ensureLevel();
        if (100 - this.hunger >= LevelRules.OVERFEED_AT) { // 배부른데 또 먹으면 살이 찐다
            this.weightDg = this.weightDg + LevelRules.FEED_WEIGHT_DG;
        }
        this.sleeping = false; // 먹이면 깬다
        this.hunger = Math.max(0, this.hunger - FEED_AMOUNT);
        this.feedCount++;
        this.lastFedAt = LocalDateTime.now();
        this.hungryStartedAt = null; // 굶주림 해소
    }

    public void addStrength(int amount) {
        this.sleeping = false; // 알약을 먹여도 깬다
        this.strength = Math.min(100, this.strength + amount);
        if (this.strength > 0) this.strengthEmptySince = null;
    }

    /** 한 판만큼 회복에 고기·알약을 몇 개씩 쓰나 (각각 최대 4개, 가득 찬 쪽은 쓰지 않는다) */
    public record Recovery(int meats, int pills) {
        public int items() { return meats + pills; }
    }

    public static final int RECOVER_MAX_ITEMS = 4;

    public Recovery planRecovery() {
        int meats = 0, pills = 0;
        for (int h = this.hunger; meats < RECOVER_MAX_ITEMS && h > 0; h -= FEED_AMOUNT) meats++;
        for (int s = this.strength; pills < RECOVER_MAX_ITEMS && s < 100; s += PILL_AMOUNT) pills++;
        return new Recovery(meats, pills);
    }

    /** 한 판만큼 회복: 고기·알약을 정해진 개수만큼 한꺼번에 먹인다 (잉크는 서비스가 받는다) */
    public void recover(Recovery plan) {
        for (int i = 0; i < plan.meats(); i++) feed();
        if (plan.pills() > 0) addStrength(PILL_AMOUNT * plan.pills());
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

    /** 불 끄기. 졸린 시간대에만 잠든다. 저절로 깨지 않고, 불을 켜거나 만지거나 먹이면 깬다 */
    public void sleep(LocalDateTime now) {
        if (this.evolutionStage == EvolutionStage.EGG) {
            throw new IllegalStateException("알은 잠을 자지 않아요.");
        }
        if (this.sleeping) return;
        if (!isSleepyTime(now)) {
            throw new IllegalStateException("아직 안 졸려요!");
        }
        this.sleeping = true;
        this.sleptAt = now;
    }

    public void wakeUp() {
        this.sleeping = false;
    }

    public void evolve(EvolutionStage nextStage) {
        ensureLevel();
        // 이번 단계에 친구와 배틀을 잘했으면 진화하며 위력을 더 받는다 (전적은 아래에서 0으로 돌린다)
        if (this.evolutionStage != EvolutionStage.EGG) {
            this.might = this.might + BattleRules.championMightBonus(championTier());
        }
        this.evolutionStage = nextStage;
        this.stageStartLevel = this.level; // 진화 가능 구간은 이 레벨부터 다시 센다
        this.evoGauge = 0;
        this.pendingEvoGain = 0;
        markLegendGlow();                // 비우기 전에: 이번 진화에 전설 잉크를 썼으면 반짝이를 켠다
        setInkLoadout(null, null, null); // 대표 잉크는 진화마다 새로 고른다
        this.battleWins = 0;             // 배틀 전적은 단계마다 새로 센다
        this.battleLosses = 0;
    }

    /** 이번 진화에 넣은 잉크(한 칸, 예전 데이터면 첫 계열 칸)가 전설이면 반짝이를 켠다 */
    private void markLegendGlow() {
        String used = inkMain != null ? inkMain : inkFamily1;
        if (used == null) return;
        boolean legend = com.tamagochi.tamagochi.domain.land.LandCatalog.ink(used)
                .map(i -> i.grade() == com.tamagochi.tamagochi.domain.land.LandCatalog.Grade.LEGEND).orElse(false);
        if (legend) this.legendGlow = true;
    }

    /** 알 → 낙서. 알 두드리기는 클라이언트 연출이고, 서버는 단계만 올린다. */
    public void hatch() {
        if (this.evolutionStage != EvolutionStage.EGG) {
            throw new IllegalStateException("알 상태가 아닙니다.");
        }
        evolve(EvolutionStage.BABY);
    }

    // ── 레벨 ──

    /** 레벨이 생기기 전 행이면 예전 진화 게이지 진행도로 레벨을 정해 둔다. 한 번 채우면 다시 하지 않는다 */
    private void ensureLevel() {
        if (this.level != null) return;
        int babySpan = LevelRules.zoneSpan(EvolutionStage.BABY);
        int start = switch (this.evolutionStage) {
            case CHILD -> LevelRules.START_LEVEL + babySpan;
            case ADULT -> LevelRules.START_LEVEL + babySpan + LevelRules.zoneSpan(EvolutionStage.CHILD);
            default -> LevelRules.START_LEVEL;
        };
        int max = EvolutionRules.gaugeMax(this.evolutionStage);
        int progress = max > 0 ? LevelRules.zoneSpan(this.evolutionStage) * Math.min(this.evoGauge, max) / max : 0;
        this.level = start + progress;
        this.exp = 0;
        this.stageStartLevel = start;
        this.pendingExp = this.pendingEvoGain * LevelRules.EXP_PER_STAR;
        this.weightDg = LevelRules.START_WEIGHT_DG;
        this.might = LevelRules.mightForLevel(this.level);
    }

    public int wins() { return this.sparWins == null ? 0 : this.sparWins; }
    public int losses() { return this.sparLosses == null ? 0 : this.sparLosses; }

    public int battleWins() { return this.battleWins == null ? 0 : this.battleWins; }
    public int battleLosses() { return this.battleLosses == null ? 0 : this.battleLosses; }

    /** 이번 단계 배틀 전적 등급 (0 없음, 1 챔피언, 2 그랜드 챔피언) */
    public int championTier() { return BattleRules.championTier(battleWins(), battleLosses()); }

    /** 만나서 배틀 한 판의 결과 (1 = 승, -1 = 패, 0 = 무승부는 세지 않는다) */
    public void recordBattle(int result) {
        if (result > 0) this.battleWins = battleWins() + 1;
        else if (result < 0) this.battleLosses = battleLosses() + 1;
    }

    /** 무료 판을 다 쓴 뒤의 배틀은 트레이닝처럼 포만감·파워를 쓴다. 모자라면 이유를, 되면 null */
    public String battleShortage(int cost) {
        if (this.hunger + cost > 100) return "배가 고파서";
        if (this.strength < cost) return "파워가 부족해서";
        return null;
    }

    public int level() { ensureLevel(); return this.level; }
    public int exp() { ensureLevel(); return this.exp; }
    public int expToNext() { return expRequired(level()); }

    /** 이 레벨에서 다음 레벨까지 드는 경험치. 진화 가능 구간은 레벨과 상관없이 고정 */
    private int expRequired(int lv) {
        int at = evolutionLevel();
        return at > 0 && lv >= at ? LevelRules.ZONE_EXP_PER_LEVEL : LevelRules.expToNext(lv);
    }
    public int pendingExp() { ensureLevel(); return this.pendingExp; }
    public int weightDg() { ensureLevel(); return this.weightDg; }
    public int might() { ensureLevel(); return this.might; }

    /** 진화 가능 구간이 시작되는 레벨 (고정: 낙서 6, 스케치 26). 진화할 수 없는 단계(알·완성작)는 0 */
    public int evolutionLevel() {
        return LevelRules.zoneStart(this.evolutionStage);
    }

    /** 진화 가능 구간의 마지막 레벨 (낙서 10, 스케치 30). 이 레벨을 넘기면 저절로 진화한다. 진화할 수 없는 단계는 0 */
    public int evolutionEndLevel() {
        return LevelRules.zoneEnd(this.evolutionStage);
    }

    /** 진화 가능 구간이다: 레벨이 오를 때마다 진화 후보 모습을 받고, 원할 때 진화할 수 있다 */
    public boolean isInEvolutionZone() {
        int at = evolutionLevel();
        return at > 0 && !isDead() && level() >= at;
    }

    /** 트레이닝으로 바로 받은 경험치(승리 보너스 포함)와 그중 승리 보너스 */
    public record TrainingGain(int exp, int winBonus) {
    }

    /**
     * 트레이닝(스파링) 결과를 반영한다. 기본 경험치(이기면 보너스 더)를 바로 받고,
     * 광고를 보면 기본 경험치만큼 더 받을 수 있게 대기시킨다(claimExp). 새 트레이닝을 하면 받지 않은 대기분은 사라진다.
     * 진화 가능 구간에서는 경험치가 줄어든다. 위력·무게·승패도 바로 바뀐다.
     */
    public TrainingGain train(int taps, boolean win) {
        ensureLevel();
        int base = LevelRules.expForTaps(taps, isInEvolutionZone());
        int bonus = win ? Math.round(base * BattleRules.WIN_EXP_BONUS_PERCENT / 100f) : 0;
        this.might = this.might + LevelRules.MIGHT_PER_TRAINING;
        this.weightDg = Math.max(LevelRules.MIN_WEIGHT_DG, this.weightDg - LevelRules.TRAINING_WEIGHT_DG);
        if (win) this.sparWins = wins() + 1; else this.sparLosses = losses() + 1;
        addExp(base + bonus);
        this.pendingExp = base;
        return new TrainingGain(base + bonus, bonus);
    }

    /** 대기 중인 경험치를 받는다. 오른 레벨 수를 돌려준다 */
    public int claimExp() {
        ensureLevel();
        int before = this.level;
        addExp(this.pendingExp);
        this.pendingExp = 0;
        return this.level - before;
    }

    /** 경험치를 더하고, 넘치면 레벨을 올린다. 진화 가능 구간 끝을 넘기면 저절로 다음 단계로 진화한다 */
    public void addExp(int amount) {
        ensureLevel();
        this.exp = this.exp + Math.max(0, amount);
        while (this.exp >= expRequired(this.level)) {
            this.exp -= expRequired(this.level);
            this.level++;
            this.might = this.might + LevelRules.MIGHT_PER_LEVEL;
            evolveIfOverdue();
        }
    }

    /** 진화 가능 구간 끝(Lv.10·30)을 넘겼는데 아직 진화하지 않았으면 다음 단계로 올린다. 진화했으면 true */
    public boolean evolveIfOverdue() {
        int end = evolutionEndLevel();
        if (end == 0 || isDead() || level() <= end) return false;
        evolve(EvolutionRules.next(this.evolutionStage));
        return true;
    }

    // ── 업데이트 전 앱(진화 게이지 화면)이 읽는 값 ──

    /** 진화 가능 구간까지의 진행도를 예전 게이지 칸 수로 바꾼 값. 구간에 들어서면 가득 */
    public int legacyEvoGauge() {
        int max = EvolutionRules.gaugeMax(this.evolutionStage);
        int at = evolutionLevel();
        if (max == 0 || at == 0) return 0;
        ensureLevel();
        int span = at - this.stageStartLevel;
        if (span <= 0) return max;
        return Math.min(max, max * Math.max(0, level() - this.stageStartLevel) / span);
    }

    public int legacyPendingEvoGain() {
        int pending = pendingExp();
        return pending > 0 ? Math.max(1, pending / LevelRules.EXP_PER_STAR) : 0;
    }

    /** 진화 가능 구간이면 진화할 수 있다. 컨디션 조건은 없다 (2026-11-07) */
    public boolean isEvolutionReady() {
        return isInEvolutionZone();
    }

    /** 진화 가능 구간이면 다음 단계로 올린다. */
    public void evolveIfReady() {
        if (!isInEvolutionZone()) {
            throw new IllegalStateException("Lv." + evolutionLevel() + "부터 진화할 수 있어요.");
        }
        evolve(EvolutionRules.next(this.evolutionStage));
    }

    /**
     * 시간 경과 반영: 포만감·파워 감소, 똥 증가, 사망 판정.
     * 알은 대상이 아니고, 잉크통·모험 중에는 기준 시각만 앞으로 당긴다(소진 없음).
     * 자는 동안에는 응가가 멈추고 포만감·파워는 SLEEP_DECAY_SLOWDOWN배 느리게 준다.
     * 포만감/파워가 0인 채 deathAfterEmpty가 지나거나 똥이 maxPoop 이상이면 사망한다.
     */
    public void applyTime(LocalDateTime now, VitalsRule rule) {
        if (isDead() || this.evolutionStage == EvolutionStage.EGG) return;

        absorbDowntime(now, rule);

        if (isBathing() || isAdventuring()) {
            this.lastHungerTickAt = now;
            this.lastStrengthTickAt = now;
            this.lastPoopAt = now;
        } else if (this.sleeping) {
            decayHunger(now, rule.satietyMinutesPerPoint() * SLEEP_DECAY_SLOWDOWN);
            decayStrength(now, rule.strengthMinutesPerPoint() * SLEEP_DECAY_SLOWDOWN);
            this.lastPoopAt = now;
        } else {
            decayHunger(now, rule.satietyMinutesPerPoint());
            decayStrength(now, rule.strengthMinutesPerPoint());
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

    private void decayHunger(LocalDateTime now, int minutesPerPoint) {
        if (this.lastHungerTickAt == null) {
            this.lastHungerTickAt = now;
            return;
        }
        long points = Duration.between(this.lastHungerTickAt, now).toMinutes() / minutesPerPoint;
        if (points <= 0) return;
        this.lastHungerTickAt = this.lastHungerTickAt.plusMinutes(points * minutesPerPoint);
        this.hunger = (int) Math.min(100, this.hunger + points);
    }

    private void decayStrength(LocalDateTime now, int minutesPerPoint) {
        if (this.lastStrengthTickAt == null) {
            this.lastStrengthTickAt = now;
            return;
        }
        long points = Duration.between(this.lastStrengthTickAt, now).toMinutes() / minutesPerPoint;
        if (points <= 0) return;
        this.lastStrengthTickAt = this.lastStrengthTickAt.plusMinutes(points * minutesPerPoint);
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
        // 새로 그린 그림은 새 끄적이다: 밖으로 보이는 ID와 이름을 새로 받는다 (예전 그림 경로와 섞이지 않게)
        this.publicId = newPublicId();
        this.name = DEFAULT_NAME;
        this.status = PetStatus.ACTIVE;
        this.evolutionStage = EvolutionStage.EGG;
        this.hunger = 50;
        this.strength = INITIAL_STRENGTH;
        this.poopCount = 0;
        this.feedCount = 0;
        this.evoGauge = 0;
        this.pendingEvoGain = 0;
        this.level = LevelRules.START_LEVEL;
        this.exp = 0;
        this.stageStartLevel = LevelRules.START_LEVEL;
        this.pendingExp = 0;
        this.weightDg = LevelRules.START_WEIGHT_DG;
        this.might = 0;
        this.sparWins = 0;
        this.sparLosses = 0;
        this.battleWins = 0;
        this.battleLosses = 0;
        this.lastFedAt = null;
        this.lastPoopAt = null;
        this.hungryStartedAt = null;
        this.strengthEmptySince = null;
        this.lastHungerTickAt = null;
        this.lastStrengthTickAt = null;
        this.lastVitalsTickAt = null;
        this.deadAt = null;
        this.sleeping = false;
        this.sleepImageMask = 0;
        this.sleepImageRedoMask = 0;
        this.sleepImageInkPaid = null;
        this.bathing = false;
        this.adventuring = false;
        setInkLoadout(null, null, null);
        this.sleptAt = null;
        this.sleepyStartHour = randomSleepyStartHour(); // 새 끄적이마다 졸린 시간이 다르다
        // 이전 캐릭터 이미지는 새 그림으로 교체되므로 비운다
        this.babyImageBase64 = null;
        this.childImageBase64 = null;
        this.adultImageBase64 = null;
        // S3의 예전 그림은 지우지 않는다 (공개 ID가 바뀌어 새 그림과 섞이지 않는다)
        this.babyImageKey = null;
        this.childImageKey = null;
        this.adultImageKey = null;
        this.sketchImageKey = null;
    }
}
