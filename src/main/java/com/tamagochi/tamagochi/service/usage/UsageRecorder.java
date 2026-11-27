package com.tamagochi.tamagochi.service.usage;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.usage.PetEvent;
import com.tamagochi.tamagochi.domain.usage.PetEventType;
import com.tamagochi.tamagochi.domain.usage.PlazaVisit;
import com.tamagochi.tamagochi.domain.usage.UserDailyVisit;
import com.tamagochi.tamagochi.repository.usage.PetEventRepository;
import com.tamagochi.tamagochi.repository.usage.PlazaVisitRepository;
import com.tamagochi.tamagochi.repository.usage.UserDailyVisitRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 사용자 행동 기록 (끄적이 사건 · 광장 방문 · 일별 접속). 수치를 내려고 남기는 기록이라 원래 동작에 끼어들지 않는다:
 * - 어느 메서드도 예외를 던지지 않는다. 저장에 실패하면 로그만 남기고 그 줄은 버린다.
 * - 저장은 스레드 하나짜리 실행기에서 한다 (PlazaHuntRunner 와 같은 방식). 부른 쪽은 일을 넣고 바로 돌아간다.
 * - 부른 쪽이 트랜잭션 안이면 그 트랜잭션이 커밋된 뒤에 넣는다 (되돌려진 부화 · 진화는 남지 않는다).
 * 시각은 부른 순간의 한국 시간이다 (저장이 밀려도 일이 일어난 때가 남는다).
 */
@Slf4j
@Service
public class UsageRecorder {

    /** 쌓아 둘 수 있는 일의 수. 넘치면 그 줄은 버리고 로그만 남긴다 */
    static final int QUEUE_MAX = 10_000;

    private final PetEventRepository petEvents;
    private final PlazaVisitRepository plazaVisits;
    private final UserDailyVisitRepository dailyVisits;
    private final Clock clock;
    private final Executor executor;

    /** 오늘 이미 적은(또는 적으려고 넣어 둔) 사람. 상태 조회는 자주 오므로 하루 첫 번째만 DB 로 간다. 날짜가 바뀌면 비운다 */
    private final Set<Long> seenToday = new HashSet<>();
    private LocalDate seenDate;

    @Autowired
    public UsageRecorder(PetEventRepository petEvents, PlazaVisitRepository plazaVisits, UserDailyVisitRepository dailyVisits, Clock clock) {
        this(petEvents, plazaVisits, dailyVisits, clock, boundedExecutor());
    }

    UsageRecorder(PetEventRepository petEvents, PlazaVisitRepository plazaVisits, UserDailyVisitRepository dailyVisits, Clock clock, Executor executor) {
        this.petEvents = petEvents;
        this.plazaVisits = plazaVisits;
        this.dailyVisits = dailyVisits;
        this.clock = clock;
        this.executor = executor;
    }

    private static ExecutorService boundedExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_MAX), r -> {
            Thread t = new Thread(r, "usage-record");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void stop() {
        if (!(executor instanceof ExecutorService service)) return;
        service.shutdown();
        try {
            // 쌓인 기록을 마저 쓰도록 잠깐 기다린다
            if (!service.awaitTermination(5, TimeUnit.SECONDS)) log.warn("사용 기록 저장이 5초 안에 끝나지 않음");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── 끄적이 사건

    /** 끄적이 사건 한 줄. stage 는 PetEvent 의 설명대로 (부화 · 진화는 새 단계, 나머지는 그때의 단계) */
    public void petEvent(Long userId, String petPublicId, PetEventType type, EvolutionStage stage, String cause) {
        if (userId == null || type == null) return;
        try {
            LocalDateTime at = LocalDateTime.now(clock);
            String stageName = stage == null ? null : stage.name();
            afterCommit("끄적이 사건 " + type + " user=" + userId,
                    () -> petEvents.save(new PetEvent(userId, petPublicId, type, stageName, cause, at)));
        } catch (RuntimeException e) {
            log.warn("사용 기록을 넣지 못함 - 끄적이 사건 {} user={}", type, userId, e);
        }
    }

    /**
     * 단계가 before 에서 달라졌으면 진화(알에서 올라왔으면 부화) 한 줄을 남긴다. 그대로면 아무것도 하지 않는다.
     * 경험치를 받다가 구간 끝을 넘겨 저절로 진화하는 길(트레이닝 · 경험치 받기 · 광장 사냥)이 쓴다
     */
    public void stageChanged(Long userId, Tamagotchi pet, EvolutionStage before) {
        if (pet == null || pet.getEvolutionStage() == before) return;
        petEvent(userId, pet.getPublicId(), before == EvolutionStage.EGG ? PetEventType.HATCH : PetEventType.EVOLVE, pet.getEvolutionStage(), null);
    }

    // ── 광장 방문

    /** 광장 방문 한 번 (나갈 때). 시각은 광장 시계의 epoch ms */
    public void plazaVisit(long userId, String kind, long enteredAtMs, long leftAtMs, int kills, int chats) {
        try {
            LocalDateTime entered = LocalDateTime.ofInstant(Instant.ofEpochMilli(enteredAtMs), clock.getZone());
            LocalDateTime left = LocalDateTime.ofInstant(Instant.ofEpochMilli(leftAtMs), clock.getZone());
            submit("광장 방문 user=" + userId, () -> plazaVisits.save(new PlazaVisit(userId, kind, entered, left, kills, chats)));
        } catch (RuntimeException e) {
            log.warn("사용 기록을 넣지 못함 - 광장 방문 user={}", userId, e);
        }
    }

    // ── 일별 접속

    /** 오늘(한국 날짜) 이 사람이 왔다. 하루에 한 줄만 남는다 */
    public void visited(Long userId) {
        if (userId == null) return;
        try {
            LocalDate today = LocalDate.now(clock);
            if (!markSeen(userId, today)) return;   // 오늘 이미 적었다: DB 를 부르지 않는다
            LocalDateTime at = LocalDateTime.now(clock);
            boolean queued = submit("접속 user=" + userId, () -> {
                try {
                    saveDailyVisit(userId, today, at);
                } catch (RuntimeException e) {
                    forget(userId, today);   // 못 적었으면 다음 조회 때 다시 해 본다
                    throw e;
                }
            });
            if (!queued) forget(userId, today);
        } catch (RuntimeException e) {
            log.warn("사용 기록을 넣지 못함 - 접속 user={}", userId, e);
        }
    }

    /** 서버가 다시 켜진 뒤에는 메모리에 표시가 없으므로 DB 에 있는지 먼저 본다. 유니크 제약이 마지막 방어다 */
    private void saveDailyVisit(Long userId, LocalDate day, LocalDateTime at) {
        if (dailyVisits.existsByUserIdAndVisitDate(userId, day)) return;
        try {
            dailyVisits.saveAndFlush(new UserDailyVisit(userId, day, at));
        } catch (DataIntegrityViolationException e) {
            // 다른 곳에서 먼저 넣었다: 이미 있으니 됐다
        }
    }

    private synchronized boolean markSeen(Long userId, LocalDate today) {
        if (!today.equals(seenDate)) {
            seenDate = today;
            seenToday.clear();
        }
        return seenToday.add(userId);
    }

    private synchronized void forget(Long userId, LocalDate day) {
        if (day.equals(seenDate)) seenToday.remove(userId);
    }

    // ── 저장

    /** 트랜잭션 안에서 불렸으면 커밋된 뒤에, 아니면 바로 실행기에 넣는다 */
    private void afterCommit(String what, Runnable write) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(what, write);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(what, write);
            }
        });
    }

    /** 실행기에 넣는다. 넣었으면 true. 큐가 가득 찼거나 실행기가 닫혔으면 버리고 false */
    private boolean submit(String what, Runnable write) {
        try {
            executor.execute(() -> {
                try {
                    write.run();
                } catch (Exception e) {
                    log.warn("사용 기록 저장 실패 - {}", what, e);
                }
            });
            return true;
        } catch (RuntimeException e) {
            log.warn("사용 기록이 밀려 버림 - {}: {}", what, e.toString());
            return false;
        }
    }
}
