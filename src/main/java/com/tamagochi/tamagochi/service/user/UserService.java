package com.tamagochi.tamagochi.service.user;

import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PointWalletRepository pointWalletRepository;

    @Transactional
    public User createUser(String tossUserId, String nickname) {

        // 이미 존재하는 유저면 그냥 반환
        return userRepository.findByTossUserId(tossUserId)
                .orElseGet(() -> {
                    User user = User.builder()
                            .tossUserId(tossUserId)
                            .nickname(nickname)
                            .build();
                    userRepository.save(user);

                    // 지갑 자동 생성
                    PointWallet wallet = PointWallet.builder()
                            .user(user)
                            .balance(0)
                            .build();
                    pointWalletRepository.save(wallet);

                    log.info("신규 유저 생성 - tossUserId: {}", tossUserId);
                    return user;
                });
    }
}