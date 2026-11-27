package com.tamagochi.tamagochi.domain.usage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 끄적이에게 일어난 일 한 줄 (부화 · 진화 · 떠남 · 다시 시작 · 다시 그리기). 사용자 수치를 내려고 남긴다 (UsageRecorder).
 * tamagotchi 는 지금 상태만 갖고, 다시 시작하면 같은 행을 덮어써서 지난 일이 남지 않는다.
 * 끄적이는 밖으로 보이는 ID(pet_public_id)로 가리킨다: 다시 시작 · 다시 그리기는 그 일이 있기 전의 ID 다.
 * stage: 부화 · 진화는 새로 된 단계, 떠남 · 다시 시작 · 다시 그리기는 그때의 단계.
 * 종류와 단계는 글자로 둔다 (값이 늘어도 열을 고치지 않게).
 */
@Entity
@Table(name = "pet_event", indexes = {
        @Index(name = "idx_pet_event_user", columnList = "user_id, created_at"),
        @Index(name = "idx_pet_event_pet", columnList = "pet_public_id"),
        @Index(name = "idx_pet_event_type", columnList = "type, created_at"),
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pet_public_id", length = 32)
    private String petPublicId;

    /** HATCH | EVOLVE | LEAVE | RESTART | REDRAW (PetEventType) */
    @Column(name = "type", nullable = false, length = 16)
    private String type;

    /** EGG | BABY | CHILD | ADULT (EvolutionStage) */
    @Column(name = "stage", length = 10)
    private String stage;

    /**
     * 떠남(LEAVE)의 까닭: STARVE(굶주림) | EXHAUST(파워 0) | POOP(응가). 서버가 떠남을 판정한 조건 그대로이고,
     * 한 번에 둘 이상이 맞았으면 +로 잇는다 (예: STARVE+POOP). 다른 종류는 null
     */
    @Column(name = "cause", length = 32)
    private String cause;

    /** 일이 일어난 때 (한국 시간) */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PetEvent(Long userId, String petPublicId, PetEventType type, String stage, String cause, LocalDateTime createdAt) {
        this.userId = userId;
        this.petPublicId = petPublicId;
        this.type = type.name();
        this.stage = stage;
        this.cause = cause;
        this.createdAt = createdAt;
    }
}
