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
    private int hunger; // 0~100

    @Column(nullable = false)
    private int feedCount;

    private LocalDateTime lastFedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void feed() {
        this.hunger = Math.max(0, this.hunger - 20);
        this.feedCount++;
        this.lastFedAt = LocalDateTime.now();
    }

    public void evolve(EvolutionStage nextStage) {
        this.evolutionStage = nextStage;
    }

    public void increaseHunger(int amount) {
        this.hunger = Math.min(100, this.hunger + amount);
    }
}