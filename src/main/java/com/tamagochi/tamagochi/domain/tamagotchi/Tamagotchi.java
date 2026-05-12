package com.tamagochi.tamagochi.domain.tamagotchi;

import com.tamagochi.tamagochi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tamagotchi")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Tamagotchi {

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
    private int hunger; // 0~100 (100이면 사망)

    @Column(nullable = false)
    private int strength; // 0~100 (트레이닝 가능 조건)

    @Column(nullable = false)
    private int poopCount; // 0~5 (5이면 사망)

    @Column(nullable = false)
    private int feedCount;

    private LocalDateTime lastFedAt;
    private LocalDateTime lastPoopAt;
    private LocalDateTime hungryStartedAt; // 배고픔 시작 시간 (7시간 체크용)
    private LocalDateTime sleptAt; // 마지막 수면 시간 (6시간 체크용)

    @Column(nullable = false)
    private boolean sleeping;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void feed() {
        this.hunger = Math.max(0, this.hunger - 20);
        this.feedCount++;
        this.lastFedAt = LocalDateTime.now();
        this.hungryStartedAt = null; // 배고픔 해소
    }

    public void addStrength(int amount) {
        this.strength = Math.min(100, this.strength + amount);
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

    public void poop() {
        this.poopCount++;
        this.lastPoopAt = LocalDateTime.now();
    }

    public void cleanPoop() {
        this.poopCount = 0;
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
    }

    public void increaseHunger(int amount) {
        if (this.sleeping) return; // 수면 중엔 배고픔 증가 안함
        if (this.hunger == 0 && this.hungryStartedAt == null) {
            this.hungryStartedAt = LocalDateTime.now();
        }
        this.hunger = Math.min(100, this.hunger + amount);
    }

    public boolean isDead() {
        // 똥이 5개 이상이면 사망
        if (this.poopCount >= 5) return true;
        // 배고픈 상태(hunger 100)에서 7시간 지나면 사망
        if (this.hungryStartedAt != null &&
                this.hungryStartedAt.isBefore(LocalDateTime.now().minusHours(7))) return true;
        return false;
    }

    public void reset() {
        this.evolutionStage = EvolutionStage.EGG;
        this.hunger = 50;
        this.strength = 0;
        this.poopCount = 0;
        this.feedCount = 0;
        this.lastFedAt = null;
        this.lastPoopAt = null;
        this.hungryStartedAt = null;
        this.sleeping = false;
    }
}