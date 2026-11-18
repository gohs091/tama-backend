package com.tamagochi.tamagochi.service.lunch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜬 뒤 한 번, 예전 세 종류로 저장된 찬장·가방을 지금 도시락 개수로 바꾼다 (LunchService.migrateLegacyPantries).
 * 여기서 못 바꿔도 서버는 그대로 뜬다: 각자의 찬장은 읽을 때 다시 바뀐다 (LunchService.pantry).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyLunchMigration {

    private final LunchService lunchService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            int migrated = lunchService.migrateLegacyPantries();
            if (migrated > 0) log.info("예전 도시락을 지금 도시락으로 바꿨어요: 찬장 {}개", migrated);
        } catch (RuntimeException e) {
            log.warn("예전 도시락 일괄 변환을 건너뛰어요 (읽을 때 다시 바꿔요)", e);
        }
    }
}
