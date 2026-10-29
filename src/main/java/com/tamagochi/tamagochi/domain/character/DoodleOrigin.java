package com.tamagochi.tamagochi.domain.character;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유저가 처음 그린 그림의 정체(분석 결과). 낙서를 만들 때 한 번 분석해 저장해 두고,
 * 스케치·완성작 진화에서 같은 분석을 다시 쓴다(비전 호출을 또 하지 않고, 단계마다 해석이 달라지는 것을 막는다).
 * 다시 그리면 새 분석으로 덮어쓴다.
 */
@Entity
@Table(name = "doodle_origin")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoodleOrigin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "toss_user_id", nullable = false, unique = true)
    private String tossUserId;

    @Column(length = 200)
    private String subject;
    @Column(length = 40)
    private String kind;
    @Column(name = "subject_ko", length = 80)
    private String subjectKo;
    @Column(length = 600)
    private String parts;
    @Column(length = 600)
    private String traits;
    @Column(name = "body_plan", length = 40)
    private String bodyPlan;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DoodleOrigin(String tossUserId) {
        this.tossUserId = tossUserId;
    }

    public void update(String subject, String kind, String subjectKo, String parts, String traits, String bodyPlan) {
        this.subject = cut(subject, 200);
        this.kind = cut(kind, 40);
        this.subjectKo = cut(subjectKo, 80);
        this.parts = cut(parts, 600);
        this.traits = cut(traits, 600);
        this.bodyPlan = cut(bodyPlan, 40);
        this.updatedAt = LocalDateTime.now();
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
