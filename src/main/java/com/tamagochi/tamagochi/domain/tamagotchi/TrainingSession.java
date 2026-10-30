package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 5초 트레이닝(스파링) 한 판. 시작할 때 서버가 발급하고, 결과 제출 때 한 번만 쓸 수 있다.
 * 서버가 시작·제출 시각을 직접 재서 "5초가 지나지 않은 제출"이나 "시작 없이 보낸 결과"를 거절한다.
 * 스파링 상대와 양쪽의 운도 시작할 때 서버가 정해 두고, 승패는 제출할 때 서버가 판정한다.
 */
@Entity
@Table(name = "training_session", indexes = @Index(name = "idx_training_session_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingSession {

    /** 시작 후 이 시간이 지나기 전의 제출은 거절한다 (카운트다운 3초 + 트레이닝 5초 중 트레이닝 시간). */
    public static final Duration MIN_ELAPSED = Duration.ofSeconds(5);
    /** 시작 후 이 시간이 지나면 만료된다 (네트워크 지연·앱 전환 여유). */
    public static final Duration MAX_ELAPSED = Duration.ofMinutes(2);

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime usedAt;

    // 스파링 상대 (BattleRules.randomEnemy). 기존 행은 null
    @Column(name = "enemy_key", length = 20)
    private String enemyKey;
    @Column(name = "enemy_level")
    private Integer enemyLevel;
    @Column(name = "enemy_might")
    private Integer enemyMight;
    @Column(name = "enemy_taps")
    private Integer enemyTaps;
    @Column(name = "enemy_condition")
    private Integer enemyCondition;
    @Column(name = "enemy_luck")
    private Integer enemyLuck;  // 천분율 900~1100
    @Column(name = "my_luck")
    private Integer myLuck;     // 천분율 900~1100

    public boolean hasEnemy() {
        return enemyKey != null;
    }

    /** 새 상대를 정한다. 내 운도 새로 굴린다 */
    public void setEnemy(BattleRules.Enemy enemy) {
        this.enemyKey = enemy.key();
        this.enemyLevel = enemy.level();
        this.enemyMight = enemy.might();
        this.enemyTaps = enemy.taps();
        this.enemyCondition = enemy.condition();
        this.enemyLuck = enemy.luckPermille();
        this.myLuck = BattleRules.randomLuckPermille();
    }

    /** 설욕전: 지난 판의 상대를 그대로 데려온다. 운은 양쪽 모두 새로 굴린다 */
    public void rematch(TrainingSession previous) {
        setEnemy(new BattleRules.Enemy(previous.enemyKey, previous.enemyLevel, previous.enemyMight,
                previous.enemyTaps, previous.enemyCondition, BattleRules.randomLuckPermille()));
    }

    public TrainingSession(String id, User user, LocalDateTime startedAt) {
        this.id = id;
        this.user = user;
        this.startedAt = startedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return Duration.between(startedAt, now).compareTo(MAX_ELAPSED) > 0;
    }

    /** 제출 시각이 너무 이르거나 늦거나, 이미 썼으면 예외. 통과하면 사용 처리한다. */
    public void use(LocalDateTime now) {
        if (isUsed()) {
            throw new IllegalStateException("이미 결과를 제출한 트레이닝이에요.");
        }
        Duration elapsed = Duration.between(startedAt, now);
        if (elapsed.compareTo(MIN_ELAPSED) < 0) {
            throw new IllegalStateException("트레이닝이 아직 끝나지 않았어요.");
        }
        if (elapsed.compareTo(MAX_ELAPSED) > 0) {
            throw new IllegalStateException("트레이닝 시간이 지났어요. 다시 시작해 주세요.");
        }
        this.usedAt = now;
    }
}
