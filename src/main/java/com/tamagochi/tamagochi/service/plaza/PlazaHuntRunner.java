package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.service.plaza.PlazaService.HuntResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    /** 쌓아 둘 수 있는 일의 수. 넘치면 거절 예외가 나고, 광장 쪽(hitMob)이 그 예외를 받는다 */
    static final int QUEUE_MAX = 10_000;
    private final ExecutorService executor;

    @org.springframework.beans.factory.annotation.Autowired
    public PlazaHuntRunner(HuntService huntService, PlazaService plazaService) {
        this(huntService, plazaService, boundedExecutor("plaza-hunt", QUEUE_MAX));
    }

    PlazaHuntRunner(HuntService huntService, PlazaService plazaService, ExecutorService executor) {
        this.huntService = huntService;
        this.plazaService = plazaService;
        this.executor = executor;
    }

    /** 스레드 하나에 큐 상한이 있는 실행기 (광장 차단 · 신고 쪽도 쓴다) */
    static ExecutorService boundedExecutor(String name, int queueMax) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(queueMax), r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    /** 앱이 시작할 때 광장에 지급 쪽을 건다 */
    @PostConstruct
    void install() {
        plazaService.setHunts(this);
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
        try {
            // 쌓인 지급을 마저 하도록 잠깐 기다린다
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) log.warn("광장 사냥 지급이 5초 안에 끝나지 않음");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
