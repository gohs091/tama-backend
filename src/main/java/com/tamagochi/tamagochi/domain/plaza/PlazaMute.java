package com.tamagochi.tamagochi.domain.plaza;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 운영자가 말을 못 하게 한 사람. until 이 지나기 전까지 광장에서 말할 수 없다 (넣고 빼는 것은 운영자가 DB 에서 한다) */
@Entity
@Table(name = "plaza_mute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlazaMute {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** UTC 로 저장된다. 운영자가 DB 에 직접 넣을 때 UTC 로 넣는다. 넣은 뒤 그 사람이 다시 접속해야 적용된다 */
    @Column(name = "muted_until", nullable = false)
    private Instant until;

    public PlazaMute(Long userId, Instant until) {
        this.userId = userId;
        this.until = until;
    }
}
