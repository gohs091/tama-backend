package com.tamagochi.tamagochi.service.tower;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 제한 시간이 지난 원정을 접는다. 앱을 열지 않은 사람도 집으로 돌아가 있게 한다
 * (앱을 연 사람은 TowerService·ExpeditionGuard가 그때 바로 접는다).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerExpeditionScheduler {

    private final TowerService towerService;

    @Scheduled(fixedDelayString = "${tower.expedition.sweep-delay-ms:60000}",
            initialDelayString = "${tower.expedition.sweep-initial-delay-ms:60000}")
    public void sweep() {
        try {
            int ended = towerService.expireTimedOut();
            if (ended > 0) log.info("원정 제한 시간 만료 - {}명 귀가", ended);
        } catch (RuntimeException e) {
            log.warn("원정 만료 처리 실패 (다음 차례에 다시 한다)", e);
        }
    }
}
