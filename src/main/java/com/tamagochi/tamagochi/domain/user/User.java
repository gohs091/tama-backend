package com.tamagochi.tamagochi.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tossUserId;

    /** 화면에 보이는 이름. 유저가 바꿀 수 있고 다른 사람과 같아도 된다 (사람을 찾을 때는 userCode를 쓴다) */
    @Column(nullable = false)
    private String nickname;

    /**
     * 바뀌지 않는 유저 코드 (8자, 화면에는 4-4로 끊어 보여준다). 문의가 오면 이 코드로 유저와 로그를 찾는다.
     * 기존 행은 null이고 로그인할 때 채운다 (UserProfileService.ensureProfile).
     */
    @Column(name = "user_code", length = 8, unique = true)
    private String userCode;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** 끄적이 알림(푸시)에 동의했는지. 앱의 토스 알림 동의 창 결과를 받아 둔다. null이면 아직 묻지 않았다 */
    @Column(name = "push_agreed")
    private Boolean pushAgreed;

    public void changePushAgreed(boolean agreed) {
        this.pushAgreed = agreed;
    }

    public boolean isPushAgreed() {
        return Boolean.TRUE.equals(this.pushAgreed);
    }

    public void assignUserCode(String code) {
        if (this.userCode != null) throw new IllegalStateException("유저 코드는 바꿀 수 없어요.");
        this.userCode = code;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}
