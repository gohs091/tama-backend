package com.tamagochi.tamagochi.service.auth;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.ForbiddenException;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 요청이 가리키는 사용자(요청 본문·경로의 tossUserId)가 로그인한 본인인지 확인하고 사용자를 찾는다.
 * 인증이 꺼진 로컬 개발 환경에서는 확인 없이 통과한다.
 */
@Component
@RequiredArgsConstructor
public class UserIdentity {

    public static final String AUTH_USER_ATTRIBUTE = "authUserId";

    private final UserRepository userRepository;
    private final AuthSettings authSettings;

    /** 요청한 tossUserId가 로그인한 사용자와 같지 않으면 거절한다. */
    public void requireSame(String claimedTossUserId) {
        if (!authSettings.isEnabled()) return;
        Object authed = RequestContextHolder.currentRequestAttributes()
                .getAttribute(AUTH_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (authed == null || !authed.equals(claimedTossUserId)) {
            throw new ForbiddenException("본인의 데이터만 사용할 수 있어요.");
        }
    }

    public User resolve(String claimedTossUserId) {
        requireSame(claimedTossUserId);
        return userRepository.findByTossUserId(claimedTossUserId)
                .orElseThrow(() -> new IllegalStateException("유저가 존재하지 않습니다."));
    }
}
