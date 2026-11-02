package com.tamagochi.tamagochi.service.user;

import com.tamagochi.tamagochi.domain.user.NicknameHistory;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.user.NicknameHistoryRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 유저 코드(바뀌지 않음)와 닉네임(바뀔 수 있고 중복 가능). 닉네임이 정해지거나 바뀔 때마다 이력을 남긴다.
 * 로그에는 유저 번호·유저 코드만 남기고 닉네임은 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private static final int CODE_RETRIES = 10;

    private final UserRepository userRepository;
    private final NicknameHistoryRepository historyRepository;

    /** 유저 코드가 없으면 만들고, 닉네임이 아직 tossUserId이면 무작위 닉네임을 준다. 로그인할 때마다 불러도 된다 */
    @Transactional
    public User ensureProfile(User user) {
        if (user.getUserCode() == null) {
            user.assignUserCode(newUniqueCode());
            log.info("유저 코드 부여 - user={}, code={}", user.getId(), user.getUserCode());
        }
        if (NicknameRules.needsNickname(user.getNickname(), user.getTossUserId())) {
            setNickname(user, NicknameRules.randomNickname());
        }
        return userRepository.save(user);
    }

    @Transactional
    public User changeNickname(User user, String raw) {
        String nickname = NicknameRules.clean(raw);
        if (!nickname.equals(user.getNickname())) {
            setNickname(user, nickname);
            log.info("닉네임 변경 - user={}, code={}", user.getId(), user.getUserCode());
        }
        return userRepository.save(user);
    }

    private void setNickname(User user, String nickname) {
        user.changeNickname(nickname);
        historyRepository.save(new NicknameHistory(user.getId(), nickname));
    }

    private String newUniqueCode() {
        for (int i = 0; i < CODE_RETRIES; i++) {
            String code = NicknameRules.randomUserCode();
            if (!userRepository.existsByUserCode(code)) return code;
        }
        throw new IllegalStateException("유저 코드를 만들지 못했어요.");
    }
}
