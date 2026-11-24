package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.service.plaza.PlazaService.HuntResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 광장 서비스는 잠금 하나로 도는 메모리 서비스라 DB 를 부르지 않는다. 사냥 지급(DB)은 스레드 하나짜리 실행기에서 하고,
 * 끝나면 done(→ PlazaService.onLoot)을 부른다. 한 스레드라 같은 사람의 지급이 순서대로 쌓인다.
 */
@Slf4j
@Component
public class PlazaHuntRunner implements PlazaService.Hunts {

    private final HuntService huntService;
    private final PlazaService plazaService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "plaza-hunt");
        t.setDaemon(true);
        return t;
    });

    public PlazaHuntRunner(HuntService huntService, PlazaService plazaService) {
        this.huntService = huntService;
        this.plazaService = plazaService;
    }

    /** 앱이 시작할 때 광장에 지급 쪽을 건다 */
    @PostConstruct
    void install() {
        plazaService.setHunts(this);
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }

    /** 광장 잠금 안에서 불리므로 일을 넣고 바로 돌아온다. 예외는 로그만 남기고 done 은 부르지 않는다 */
    @Override
    public void kill(long userId, String enemyKey, boolean questTick, Consumer<HuntResult> done) {
        executor.execute(() -> {
            try {
                HuntResult r = huntService.hunt(userId, enemyKey, questTick);
                if (r != null) done.accept(r);
            } catch (Exception e) {
                log.warn("광장 사냥 지급 실패 - user: {}, enemy: {}", userId, enemyKey, e);
            }
        });
    }
}
