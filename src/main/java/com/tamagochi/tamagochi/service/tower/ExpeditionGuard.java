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

    /**
     * 새로 시작·다시 그리기: 탑에 가 있는 동안에는 할 수 없다 (포만감이 처음 값으로 돌아가 원정을 공짜로 이어 가게 된다).
     * 다만 원정을 이어 갈 끄적이가 없으면(죽었거나 없음) 남아 있는 원정 표시 때문에 새로 시작이 막히지 않게,
     * 거절하지 않고 원정을 접은 뒤 진행시킨다. 부르는 쪽의 트랜잭션 안에서 불러야 한다
     */
    public void requireHomeToRestart(User user, boolean petGone) {
        TowerProgress progress = progressRepository.findByUserId(user.getId()).orElse(null);
        if (progress == null || !progress.isOnExpedition()) return;
        if (petGone) {
            progress.endExpedition();
            return;
        }
        throw new CodedException("ON_EXPEDITION", "탑에 가 있는 동안에는 할 수 없어요. 집에 돌아온 뒤에 해 주세요.");
    }
}
