package com.tamagochi.tamagochi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.domain.yard.Yard;
import com.tamagochi.tamagochi.exception.CodedException;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.yard.YardRepository;
import com.tamagochi.tamagochi.service.lunch.LunchService;
import com.tamagochi.tamagochi.service.tower.TowerService;
import com.tamagochi.tamagochi.service.user.UserService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 도시락·탑 테스트가 함께 쓰는 바탕: 실제 앱(메모리 DB)을 띄우고 시간을 움직이는 시계를 넣는다.
 * 같은 설정을 쓰는 테스트끼리는 앱을 한 번만 띄운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TowerLunchTestSupport.ClockConfig.class)
public abstract class TowerLunchTestSupport {

    public static class MutableClock extends Clock {
        public volatile Instant now = Instant.parse("2026-11-17T01:00:00Z"); // KST 10:00

        public void advanceDays(long days) { now = now.plusSeconds(days * 86400); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock towerLunchClock() {
            return new MutableClock();
        }
    }

    @MockitoBean
    @SuppressWarnings("unused")
    protected StringRedisTemplate redisTemplate; // 테스트에서는 Redis를 쓰지 않는다

    @Autowired protected MutableClock clock;
    @Autowired protected UserService userService;
    @Autowired protected TamagotchiRepository tamagotchiRepository;
    @Autowired protected YardRepository yardRepository;
    @Autowired protected LunchService lunchService;
    @Autowired protected TowerService towerService;

    /** 열매 fruit개와 포만감 satiety인 낙서 끄적이를 가진 새 유저 */
    protected User newUser(int fruit, int satiety) {
        User user = userService.createUser("toss_tl_" + System.nanoTime(), "tl");
        tamagotchiRepository.save(Tamagotchi.builder().user(user).name("끄적이")
                .evolutionStage(EvolutionStage.BABY).hunger(100 - satiety).strength(80).build());
        Yard yard = new Yard(user, clock.instant());
        yard.addFruit(fruit);
        yardRepository.save(yard);
        return user;
    }

    protected Tamagotchi pet(User user) {
        return tamagotchiRepository.findActiveByUserId(user.getId()).orElseThrow();
    }

    protected int fruit(User user) {
        return yardRepository.findByUserId(user.getId()).orElseThrow().getFruit();
    }

    /** 포만감을 가득 채운다 (테스트 준비용) */
    protected void fillSatiety(User user) {
        Tamagotchi pet = pet(user);
        pet.refill(100);
        tamagotchiRepository.save(pet);
    }

    protected static void assertCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CodedException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    /** 같은 일을 여러 스레드에서 동시에 시작한다. 결과는 성공이면 "OK", 거절이면 그 코드 */
    protected static List<String> together(int threads, Runnable work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<String> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        work.run();
                        return "OK";
                    } catch (CodedException e) {
                        return e.getCode();
                    }
                };
                futures.add(pool.submit(task));
            }
            ready.await();
            go.countDown();
            List<String> out = new ArrayList<>();
            for (Future<String> f : futures) out.add(f.get(30, TimeUnit.SECONDS));
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
