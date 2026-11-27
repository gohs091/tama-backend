package com.tamagochi.tamagochi.service.usage;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.LevelRules;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.TamagotchiScheduler;
import com.tamagochi.tamagochi.domain.usage.PetEvent;
import com.tamagochi.tamagochi.domain.usage.PetEventType;
import com.tamagochi.tamagochi.domain.usage.PlazaVisit;
import com.tamagochi.tamagochi.domain.usage.UserDailyVisit;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.usage.PetEventRepository;
import com.tamagochi.tamagochi.repository.usage.PlazaVisitRepository;
import com.tamagochi.tamagochi.repository.usage.UserDailyVisitRepository;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.battle.MeetBattleService.Peer;
import com.tamagochi.tamagochi.service.plaza.HuntService;
import com.tamagochi.tamagochi.service.plaza.PlazaService;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import com.tamagochi.tamagochi.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 앱(메모리 DB)에서 세 기록이 테이블에 남는지 확인한다: 요청 → 커밋 → 기록용 실행기 → 테이블.
 * 저장은 다른 스레드에서 하므로 줄이 나타날 때까지 잠깐 기다린다. Redis 는 흉내.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsageRecordIntegrationTest {

    @MockitoBean StringRedisTemplate redisTemplate;

    @Autowired MockMvc mvc;
    @Autowired AuthTokenService tokens;
    @Autowired UserService userService;
    @Autowired TamagotchiRepository tamagotchiRepository;
    @Autowired TamagotchiService tamagotchiService;
    @Autowired TamagotchiScheduler scheduler;
    @Autowired HuntService huntService;
    @Autowired PlazaService plazaService;
    @Autowired UsageRecorder usageRecorder;
    @Autowired PetEventRepository petEvents;
    @Autowired PlazaVisitRepository plazaVisits;
    @Autowired UserDailyVisitRepository dailyVisits;
    @Autowired PlatformTransactionManager txManager;

    private String tossUserId;
    private String bearer;
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        tossUserId = "toss_usage_" + System.nanoTime();
        user = userService.createUser(tossUserId, "기록");
        bearer = "Bearer " + tokens.issue(tossUserId);
    }

    private Tamagotchi save(Tamagotchi.TamagotchiBuilder pet) {
        return tamagotchiRepository.save(pet.user(user).name("끄적이").build());
    }

    private static Tamagotchi.TamagotchiBuilder pet(EvolutionStage stage) {
        return Tamagotchi.builder().evolutionStage(stage).hunger(40).strength(60);
    }

    /** 진화 가능 구간의 마지막 레벨에서, 경험치 1만 더 받으면 저절로 진화하는 낙서 */
    private static Tamagotchi.TamagotchiBuilder babyAboutToEvolve() {
        return pet(EvolutionStage.BABY).level(LevelRules.zoneEnd(EvolutionStage.BABY)).exp(LevelRules.ZONE_EXP_PER_LEVEL - 1)
                .stageStartLevel(LevelRules.START_LEVEL).pendingExp(0).weightDg(LevelRules.START_WEIGHT_DG).might(0);
    }

    private List<PetEvent> events() { return petEvents.findByUserIdOrderByIdAsc(user.getId()); }

    /** 이 사용자의 사건이 n 줄이 될 때까지 기다렸다가 돌려준다 */
    private List<PetEvent> awaitEvents(int n) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(events()).hasSize(n));
        return events();
    }

    /**
     * 지금까지 넣은 기록이 모두 저장될 때까지 기다린다: 실행기는 스레드 하나라 순서대로 쓰므로,
     * 다른 사용자 번호로 표시 줄을 하나 넣고 그것이 나타나면 앞의 것은 다 끝난 것이다
     */
    private void drain() {
        long marker = -System.nanoTime();
        usageRecorder.petEvent(marker, "marker", PetEventType.HATCH, EvolutionStage.BABY, null);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(petEvents.findByUserIdOrderByIdAsc(marker)).hasSize(1));
    }

    private void postJson(String path, String body) throws Exception {
        mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("알 깨우기 → 다시 시작: 부화와 다시 시작이 한 줄씩, 같은 끄적이 ID 로 남는다")
    void hatchThenRestart() throws Exception {
        String petId = save(pet(EvolutionStage.EGG)).getPublicId();

        postJson("/api/tamagotchi/hatch", "{\"tossUserId\":\"" + tossUserId + "\",\"idempotencyKey\":\"k1\",\"redInk\":true}");
        PetEvent hatch = awaitEvents(1).get(0);
        assertThat(hatch.getType()).isEqualTo("HATCH");
        assertThat(hatch.getStage()).isEqualTo("BABY");
        assertThat(hatch.getPetPublicId()).isEqualTo(petId);
        assertThat(hatch.getCause()).isNull();
        assertThat(hatch.getCreatedAt()).isNotNull();

        postJson("/api/tamagotchi/reset", "{\"tossUserId\":\"" + tossUserId + "\"}");
        PetEvent restart = awaitEvents(2).get(1);
        assertThat(restart.getType()).isEqualTo("RESTART");
        assertThat(restart.getStage()).isEqualTo("BABY");
        assertThat(restart.getPetPublicId()).isEqualTo(petId);   // 그만둔 끄적이. 새 알은 다른 ID 를 받았다
        assertThat(tamagotchiRepository.findActiveByUserId(user.getId()).orElseThrow().getPublicId()).isNotEqualTo(petId);
    }

    @Test
    @DisplayName("스케줄러가 떠남을 판정하면 그 까닭과 함께 남는다")
    void leaveWithCause() {
        LocalDateTime now = LocalDateTime.now();
        Tamagotchi pet = save(pet(EvolutionStage.CHILD).hunger(100).hungryStartedAt(now.minusHours(7)));

        scheduler.tick();   // 운영 규칙: 포만감 0 인 채 6시간이면 떠난다

        // 뒤에서 도는 1분 틱과 겹칠 수 있어 줄 수는 세지 않는다 (한 번만 남기는 것은 TamagotchiLeaveRecordTest 가 본다)
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(events()).isNotEmpty());
        PetEvent leave = events().get(0);
        assertThat(leave.getType()).isEqualTo("LEAVE");
        assertThat(leave.getStage()).isEqualTo("CHILD");
        assertThat(leave.getCause()).isEqualTo("STARVE");
        assertThat(leave.getPetPublicId()).isEqualTo(pet.getPublicId());
        assertThat(tamagotchiRepository.findActiveByUserId(user.getId()).orElseThrow().isDead()).isTrue();
    }

    @Test
    @DisplayName("광장 사냥 경험치로 저절로 진화해도 진화가 남는다")
    void huntAutoEvolve() {
        save(babyAboutToEvolve());

        PlazaService.HuntResult r = huntService.hunt(user.getId(), com.tamagochi.tamagochi.domain.spar.SparRoster.all().get(0).id(), false);

        assertThat(r.ok()).isTrue();
        assertThat(r.stage()).isEqualTo("CHILD");
        PetEvent evolve = awaitEvents(1).get(0);
        assertThat(evolve.getType()).isEqualTo("EVOLVE");
        assertThat(evolve.getStage()).isEqualTo("CHILD");
    }

    @Test
    @DisplayName("단계가 그대로인 사냥은 사건을 남기지 않는다")
    void huntWithoutEvolve_recordsNothing() {
        save(pet(EvolutionStage.BABY));

        huntService.hunt(user.getId(), com.tamagochi.tamagochi.domain.spar.SparRoster.all().get(0).id(), false);

        drain();
        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("되돌려진 트랜잭션의 부화는 남지 않는다")
    void rolledBackHatch_recordsNothing() {
        save(pet(EvolutionStage.EGG));

        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            tamagotchiService.hatch(user, "k", true);
            tx.setRollbackOnly();
        });

        drain();
        assertThat(events()).isEmpty();
        assertThat(tamagotchiRepository.findActiveByUserId(user.getId()).orElseThrow().getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
    }

    @Test
    @DisplayName("상태 조회를 여러 번 해도 일별 접속은 하루에 한 줄이고, 조회 응답은 그대로다")
    void dailyVisit_oneRowPerDay() throws Exception {
        save(pet(EvolutionStage.BABY));

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/tamagotchi/" + tossUserId).header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.evolutionStage").value("BABY"));
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(dailyVisits.findByUserId(user.getId())).hasSize(1));
        drain();
        List<UserDailyVisit> rows = dailyVisits.findByUserId(user.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getVisitDate()).isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("테이블의 유니크 제약: 같은 사용자 · 같은 날짜는 두 줄이 될 수 없다")
    void dailyVisit_uniquePerUserAndDate() {
        LocalDate day = LocalDate.of(2026, 1, 2);
        dailyVisits.saveAndFlush(new UserDailyVisit(user.getId(), day, LocalDateTime.now()));

        assertThatThrownBy(() -> dailyVisits.saveAndFlush(new UserDailyVisit(user.getId(), day, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(dailyVisits.existsByUserIdAndVisitDate(user.getId(), day)).isTrue();
    }

    /** 받은 것을 버리는 가짜 연결 */
    private static Peer peer(String id) {
        return new Peer() {
            @Override public String id() { return id; }
            @Override public void send(String json) { }
            @Override public void close() { }
        };
    }

    @Test
    @DisplayName("광장에 들어왔다 나가면 방문이 한 줄 남는다 (한 마리도 안 잡았어도, 3D 방은 3d 로)")
    void plazaVisit() {
        Peer peer = peer("usage-" + System.nanoTime());
        plazaService.connect(peer, new PlazaService.Profile(user.getId(), "기록", "끄적이", 3, "CHILD", "https://cdn.example/pet.png", "pub", null),
                null, null, new PlazaService.Extras(null, null, true, null, Set.of(), 0));
        plazaService.onMessage(peer, "{\"t\":\"hello\",\"v\":3}");

        plazaService.onMessage(peer, "{\"t\":\"leave\"}");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(plazaVisits.findByUserIdOrderByIdAsc(user.getId())).hasSize(1));
        PlazaVisit visit = plazaVisits.findByUserIdOrderByIdAsc(user.getId()).get(0);
        assertThat(visit.getKind()).isEqualTo("3d");
        assertThat(visit.getKills()).isZero();
        assertThat(visit.getChats()).isZero();
        assertThat(visit.getLeftAt()).isAfterOrEqualTo(visit.getEnteredAt());
    }
}
