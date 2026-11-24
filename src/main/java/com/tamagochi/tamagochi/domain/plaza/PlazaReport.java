package com.tamagochi.tamagochi.domain.plaza;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 광장 신고. 운영자가 훑어볼 수 있게 신고당한 사람의 최근 말(줄바꿈으로 이은 열 줄)과 방 열쇠를 같이 남긴다 */
@Entity
@Table(name = "plaza_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlazaReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** chat | name | etc */
    @Column(nullable = false, length = 16)
    private String why;

    @Column(columnDefinition = "TEXT")
    private String lines;

    @Column(name = "room_key", length = 16)
    private String roomKey;

    @Column(nullable = false)
    private Instant createdAt;

    public PlazaReport(Long reporterId, Long targetId, String why, String lines, String roomKey) {
        this.reporterId = reporterId;
        this.targetId = targetId;
        this.why = why;
        this.lines = lines;
        this.roomKey = roomKey;
        this.createdAt = Instant.now();
    }
}
