package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.domain.tower.TowerProgress;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.tower.TowerProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지금 탑 원정 중인지 묻는 한 곳. 다른 서비스(집의 먹이기, 도시락 채비)가 원정 여부로 동작을 막을 때 여기만 쓴다.
 * 리포지토리만 읽으므로 어느 서비스가 가져다 써도 순환 의존이 생기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ExpeditionGuard {

    private final TowerProgressRepository progressRepository;

    public boolean onExpedition(User user) {
        return progressRepository.findByUserId(user.getId()).map(TowerProgress::isOnExpedition).orElse(false);
    }

    /** 집에서 포만감을 채우는 먹이기: 탑에 가 있는 동안에는 가방에 챙긴 도시락만 먹을 수 있다 */
    public void requireHomeToFeed(User user) {
        if (onExpedition(user)) {
            throw new CodedException("ON_EXPEDITION", "탑에 가 있는 동안에는 싸 간 도시락만 먹을 수 있어요.");
        }
    }
}
