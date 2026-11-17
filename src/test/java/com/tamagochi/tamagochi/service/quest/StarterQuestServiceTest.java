package com.tamagochi.tamagochi.service.quest;

import com.tamagochi.tamagochi.domain.battle.MeetBattle;
import com.tamagochi.tamagochi.domain.farm.Farm;
import com.tamagochi.tamagochi.domain.quest.StarterQuest;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.domain.yard.Yard;
import com.tamagochi.tamagochi.repository.battle.MeetBattleRepository;
import com.tamagochi.tamagochi.repository.farm.FarmRepository;
import com.tamagochi.tamagochi.repository.point.PointTransactionRepository;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import com.tamagochi.tamagochi.repository.quest.StarterQuestProgressRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.yard.YardRepository;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.battle.MeetBattleRecorder;
import com.tamagochi.tamagochi.service.farm.FarmService;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import com.tamagochi.tamagochi.service.user.UserService;
import com.tamagochi.tamagochi.service.yard.YardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 앱(메모리 DB)에서 첫걸음 퀘스트의 완료 판정·보상 받기·기존 유저 채우기를 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StarterQuestServiceTest.ClockConfig.class)
class StarterQuestServiceTest {

    /** 트레이닝 제출까지 5초를 실제로 기다리지 않기 위한 시계 */
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-17T03:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock starterQuestTestClock() {
            return new MutableClock();
        }
    }

    @MockitoBean
    @SuppressWarnings("unused")
    StringRedisTemplate redisTemplate; // Redis의 1차 중복 방지는 끈다: 한 번만 지급되는 것은 DB가 보장해야 한다

    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired AuthTokenService tokens;
    @Autowired UserService userService;
    @Autowired StarterQuestService quests;
    @Autowired StarterQuestProgressRepository progressRepository;
    @Autowired TamagotchiService tamagotchiService;
    @Autowired FarmService farmService;
    @Autowired YardService yardService;
    @Autowired MeetBattleRecorder meetBattleRecorder;
    @Autowired TamagotchiRepository tamagotchiRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired YardRepository yardRepository;
    @Autowired MeetBattleRepository meetBattleRepository;
    @Autowired PointWalletRepository walletRepository;
    @Autowired PointTransactionRepository transactionRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
    }

    private User newUser() {
        return userService.createUser("toss_sq_" + System.nanoTime(), "첫걸음");
    }

    /** 갓 태어난 낙서 (아무것도 안 해 봤다) */
    private Tamagotchi.TamagotchiBuilder baby(User user) {
        return Tamagotchi.builder().user(user).name("끄적이").evolutionStage(EvolutionStage.BABY).hunger(40).strength(60);
    }

    private User newUserWithBaby() {
        User user = newUser();
        tamagotchiRepository.save(baby(user).poopCount(2).build());
        return user;
    }

    private StarterQuestService.QuestView quest(StarterQuestService.View view, StarterQuest q) {
        return view.quests().stream().filter(v -> v.id().equals(q.id)).findFirst().orElseThrow();
    }

    private int balance(User user) {
        return walletRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    private static String key() {
        return "sq-test-" + System.nanoTime();
    }

    @Test
    @DisplayName("목록은 앱과 같은 순서·이름이고 보상 합계는 300이다")
    void catalog() {
        StarterQuestService.View view = quests.get(newUserWithBaby());

        assertThat(view.quests()).extracting(StarterQuestService.QuestView::id)
                .containsExactly("care", "train", "clean", "sleep", "bag", "farm", "room", "evolve", "meet");
        assertThat(view.quests()).extracting(StarterQuestService.QuestView::order).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(view.quests().stream().mapToInt(StarterQuestService.QuestView::reward).sum()).isEqualTo(300);
        assertThat(view.quests()).noneMatch(StarterQuestService.QuestView::done);
        assertThat(view.doneCount()).isZero();
        assertThat(view.total()).isEqualTo(9);
        assertThat(view.balance()).isEqualTo(UserService.WELCOME_INK);
    }

    @Test
    @DisplayName("완료 판정: 돌보기·청소·재우기·트레이닝은 서비스가 성공한 순간에 끝난 것으로 적힌다")
    void careActionsMarkDone() {
        User user = newUserWithBaby();
        assertThat(quests.get(user).doneCount()).isZero();

        tamagotchiService.feed(user, key());
        assertThat(quest(quests.get(user), StarterQuest.CARE).done()).isTrue();
        tamagotchiService.clean(user, key());
        assertThat(quest(quests.get(user), StarterQuest.CLEAN).done()).isTrue();

        TrainingSession session = tamagotchiService.startTraining(user, null);
        clock.now = clock.now.plusSeconds(11);
        tamagotchiService.training(user, session.getId(), 20, key());
        assertThat(quest(quests.get(user), StarterQuest.TRAIN).done()).isTrue();

        tamagotchiService.sleep(user);
        StarterQuestService.View view = quests.get(user);
        assertThat(quest(view, StarterQuest.SLEEP).done()).isTrue();

        assertThat(view.doneCount()).isEqualTo(4);
        assertThat(view.quests()).filteredOn(StarterQuestService.QuestView::done)
                .allMatch(v -> v.claimable() && !v.claimed() && v.reward() > 0);
        // 다시 해도 줄은 하나다
        tamagotchiService.wakeUp(user);
        tamagotchiService.feed(user, key());
        assertThat(progressRepository.findByUserId(user.getId())).hasSize(9);
    }

    @Test
    @DisplayName("완료 판정: 알약만 먹여도 돌보기가 끝난다")
    void pillMarksCare() {
        User user = newUserWithBaby();
        tamagotchiService.eatPill(user, key());
        assertThat(quest(quests.get(user), StarterQuest.CARE).done()).isTrue();
    }

    @Test
    @DisplayName("완료 판정: 씨앗 심기, 방에 가구 놓기(마당 소품은 아님), 친구 배틀")
    void farmRoomMeetMarkDone() {
        User user = newUserWithBaby();
        User friend = newUserWithBaby();

        farmService.plant(user, 0, "strawberry");
        assertThat(quest(quests.get(user), StarterQuest.FARM).done()).isTrue();

        yardService.devAddFruit(user, 50);
        yardService.buyProp(user, "bush", "s0", null, null); // 마당 소품
        assertThat(quest(quests.get(user), StarterQuest.ROOM).done()).isFalse();
        yardService.buyProp(user, "plant", "r-s0", 500, 800); // 방 가구
        assertThat(quest(quests.get(user), StarterQuest.ROOM).done()).isTrue();

        meetBattleRecorder.record(user.getId(), friend.getId(), 1, null, 30, 10);
        assertThat(quest(quests.get(user), StarterQuest.MEET).done()).isTrue();
        assertThat(quest(quests.get(friend), StarterQuest.MEET).done()).isTrue();
        assertThat(quest(quests.get(friend), StarterQuest.FARM).done()).isFalse();
    }

    @Test
    @DisplayName("완료 판정: 진화하면 끝나고, 보관함은 앱이 알릴 때만 끝난다 (다른 항목은 앱이 알릴 수 없다)")
    void evolveAndClientReportOverHttp() throws Exception {
        User user = newUserWithBaby();
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);
        String body = "{\"tossUserId\":\"" + id + "\"}";
        while (!tamagotchiService.getByUserId(user.getId()).isInEvolutionZone()) tamagotchiService.devLevelUp(user);

        mvc.perform(post("/api/tamagotchi/evolve").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/api/quests/starter").param("tossUserId", id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quests[7].id").value("evolve"))
                .andExpect(jsonPath("$.quests[7].done").value(true))
                .andExpect(jsonPath("$.quests[7].claimable").value(true))
                .andExpect(jsonPath("$.quests[7].reward").value(100))
                .andExpect(jsonPath("$.quests[4].id").value("bag"))
                .andExpect(jsonPath("$.quests[4].done").value(false))
                .andExpect(jsonPath("$.quests[4].clientReported").value(true));

        mvc.perform(post("/api/quests/starter/care/done").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("앱에서 알릴 수 없는 퀘스트예요."));
        mvc.perform(post("/api/quests/starter/bag/done").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quests[4].done").value(true))
                .andExpect(jsonPath("$.granted").value(0));

        mvc.perform(post("/api/quests/starter/bag/claim").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quests[4].claimed").value(true))
                .andExpect(jsonPath("$.quests[4].claimable").value(false))
                .andExpect(jsonPath("$.granted").value(20))
                .andExpect(jsonPath("$.balance").value(UserService.WELCOME_INK + 20));
        mvc.perform(post("/api/quests/starter/bag/claim").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 받은 보상이에요."));
        mvc.perform(post("/api/quests/starter/nope/claim").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("없는 퀘스트예요."));

        // 남의 퀘스트는 보거나 받을 수 없다
        String other = newUserWithBaby().getTossUserId();
        mvc.perform(get("/api/quests/starter").param("tossUserId", other).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/quests/starter/bag/claim").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tossUserId\":\"" + other + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("끝낸 퀘스트는 고정 잉크를 한 번만 받는다 (중복 수령 불가)")
    void claimOnce() {
        User user = newUserWithBaby();
        tamagotchiService.feed(user, key());
        int before = balance(user);

        StarterQuestService.View view = quests.claim(user, "care");
        assertThat(view.granted()).isEqualTo(StarterQuest.CARE.reward);
        assertThat(view.balance()).isEqualTo(before + StarterQuest.CARE.reward);
        assertThat(quest(view, StarterQuest.CARE).claimed()).isTrue();
        assertThat(quest(view, StarterQuest.CARE).claimable()).isFalse();

        assertThatThrownBy(() -> quests.claim(user, "care"))
                .isInstanceOf(IllegalStateException.class).hasMessage("이미 받은 보상이에요.");
        assertThat(balance(user)).isEqualTo(before + StarterQuest.CARE.reward);
    }

    @Test
    @DisplayName("끝내지 않은 퀘스트는 받을 수 없다 (조회 전·후 모두)")
    void cannotClaimBeforeDone() {
        User user = newUserWithBaby();
        assertThatThrownBy(() -> quests.claim(user, "train"))
                .isInstanceOf(IllegalStateException.class).hasMessage("아직 끝내지 않은 퀘스트예요.");
        quests.get(user); // 빈 줄이 생긴 뒤에도
        assertThatThrownBy(() -> quests.claim(user, "train"))
                .isInstanceOf(IllegalStateException.class).hasMessage("아직 끝내지 않은 퀘스트예요.");
        assertThatThrownBy(() -> quests.claim(user, "없는퀘스트"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(balance(user)).isEqualTo(UserService.WELCOME_INK);
    }

    @Test
    @DisplayName("같은 보상을 동시에 여러 번 요청해도 한 번만 지급된다")
    void concurrentClaimPaysOnce() throws Exception {
        User user = newUserWithBaby();
        quests.markDone(user, StarterQuest.EVOLVE);
        int before = balance(user);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> attempt = () -> {
                ready.countDown();
                go.await();
                try {
                    quests.claim(user, "evolve");
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            };
            results.add(pool.submit(attempt));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        int success = 0;
        for (Future<Boolean> f : results) if (f.get(30, TimeUnit.SECONDS)) success++;
        pool.shutdown();

        assertThat(success).isEqualTo(1);
        assertThat(balance(user)).isEqualTo(before + StarterQuest.EVOLVE.reward);
        assertThat(transactionRepository.findByIdempotencyKey("starter-quest-" + user.getId() + "-evolve")).isPresent();
        assertThat(progressRepository.findByUserIdAndQuest(user.getId(), "evolve").orElseThrow().isClaimed()).isTrue();
    }

    @Test
    @DisplayName("같은 항목을 동시에 처음 끝내도 줄은 하나만 생긴다")
    void concurrentMarkDoneKeepsOneRow() throws Exception {
        User user = newUserWithBaby();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                quests.markDone(user, StarterQuest.BAG);
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : results) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(progressRepository.findByUserId(user.getId())).hasSize(1);
        assertThat(quest(quests.get(user), StarterQuest.BAG).claimable()).isTrue();
    }

    /** 이 기능이 생기기 전부터 키우던 유저: 스케치까지 진화했고, 먹이고 재워 봤고, 밭에 심었고, 방에 가구를 놓았고, 친구와 배틀했다 */
    private User veteran() {
        User user = newUser();
        tamagotchiRepository.save(Tamagotchi.builder().user(user).name("끄적이").evolutionStage(EvolutionStage.CHILD)
                .hunger(40).strength(60).feedCount(12).sleptAt(LocalDateTime.of(2026, 11, 1, 22, 0)).build());
        Farm farm = new Farm(user, clock.instant());
        farm.plant(0, "strawberry", clock.instant());
        farmRepository.save(farm);
        Yard yard = new Yard(user, clock.instant());
        yard.addFruit(50);
        yard.buyProp("plant");
        yard.place("r-s0", "plant");
        yardRepository.save(yard);
        meetBattleRepository.save(MeetBattle.builder().hostUserId(user.getId()).guestUserId(-1L).winnerUserId(user.getId()).build());
        return user;
    }

    @Test
    @DisplayName("기존 유저: 처음 조회할 때 이미 해 본 게 확실한 항목은 보상 없이 끝난 것으로 채운다")
    void backfillOnFirstGet() {
        User user = veteran();

        StarterQuestService.View view = quests.get(user);

        for (StarterQuest q : List.of(StarterQuest.CARE, StarterQuest.TRAIN, StarterQuest.SLEEP, StarterQuest.FARM,
                StarterQuest.ROOM, StarterQuest.EVOLVE, StarterQuest.MEET)) {
            StarterQuestService.QuestView v = quest(view, q);
            assertThat(v.done()).as(q.id).isTrue();
            assertThat(v.claimed()).as(q.id).isTrue();
            assertThat(v.claimable()).as(q.id).isFalse();
            assertThat(v.reward()).as(q.id).isZero();
        }
        // 서버가 알 수 없는 것(청소, 보관함)은 그대로 남고 보상도 그대로다
        for (StarterQuest q : List.of(StarterQuest.CLEAN, StarterQuest.BAG)) {
            assertThat(quest(view, q).done()).as(q.id).isFalse();
            assertThat(quest(view, q).reward()).as(q.id).isEqualTo(q.reward);
        }
        assertThat(view.doneCount()).isEqualTo(7);
        assertThat(view.balance()).isEqualTo(UserService.WELCOME_INK); // 소급 보상 없음
        assertThatThrownBy(() -> quests.claim(user, "evolve"))
                .isInstanceOf(IllegalStateException.class).hasMessage("이미 받은 보상이에요.");
        assertThat(balance(user)).isEqualTo(UserService.WELCOME_INK);

        // 남은 항목은 새로 하면 보상을 받는다
        quests.reportDone(user, "bag");
        assertThat(quests.claim(user, "bag").granted()).isEqualTo(StarterQuest.BAG.reward);
        assertThat(quests.get(user).doneCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("기능이 생긴 뒤 직접 끝낸 항목은 처음 조회해도 보상 없이 닫히지 않는다")
    void doneBeforeFirstGetStaysClaimable() {
        User user = newUserWithBaby();
        tamagotchiService.feed(user, key()); // feedCount가 올라 '이미 해 본 항목'처럼 보이지만 먼저 적힌 줄이 우선이다

        StarterQuestService.QuestView care = quest(quests.get(user), StarterQuest.CARE);
        assertThat(care.done()).isTrue();
        assertThat(care.claimable()).isTrue();
        assertThat(care.reward()).isEqualTo(StarterQuest.CARE.reward);
    }

    @Test
    @DisplayName("소급 지급을 켜면 기존 유저도 이미 해 본 항목의 보상을 직접 받는다")
    void retroRewardSwitch() {
        assertThat(StarterQuestService.RETRO_REWARD).isFalse(); // 기본은 꺼짐 (결정 전)
        User user = veteran();
        quests.setRetroReward(true);
        try {
            StarterQuestService.QuestView evolve = quest(quests.get(user), StarterQuest.EVOLVE);
            assertThat(evolve.done()).isTrue();
            assertThat(evolve.claimable()).isTrue();
            assertThat(evolve.reward()).isEqualTo(StarterQuest.EVOLVE.reward);
            assertThat(quests.claim(user, "evolve").balance()).isEqualTo(UserService.WELCOME_INK + StarterQuest.EVOLVE.reward);
        } finally {
            quests.setRetroReward(false);
        }
    }
}
