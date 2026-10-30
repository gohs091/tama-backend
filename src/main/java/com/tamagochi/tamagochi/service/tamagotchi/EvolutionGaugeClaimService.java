package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 훈련으로 받을 수 있게 된 경험치를, 광고를 본 뒤에 더한다 (넘치면 레벨이 오른다).
 * 양은 훈련 때 서버가 탭 수로 정해 둔 대기 값(pendingExp)이고, 광고를 봤다고 더 얹어 주지 않는다.
 * 광고 시청 자체는 서버가 검증할 수 없지만, 대기 값은 실제 훈련 한 번마다 한 번만 생기고 한 번 받으면 사라지므로
 * 요청을 마음대로 반복해서 게이지를 늘릴 수는 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionGaugeClaimService {

    private final TamagotchiRepository tamagotchiRepository;

    @Transactional
    public Tamagotchi claim(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        int pending = tamagotchi.pendingExp();
        if (pending <= 0) {
            throw new IllegalStateException("받을 경험치가 없어요.");
        }
        int levelsUp = tamagotchi.claimExp();
        log.info("경험치 받기 - userId: {}, exp: +{}, levelUp: {}, level: {}", user.getId(), pending, levelsUp, tamagotchi.level());
        return tamagotchi;
    }
}
