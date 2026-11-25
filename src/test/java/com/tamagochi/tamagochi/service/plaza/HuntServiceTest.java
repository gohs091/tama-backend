package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.domain.farm.FarmCatalog;
import com.tamagochi.tamagochi.domain.spar.SparDrop;
import com.tamagochi.tamagochi.domain.spar.SparRoster;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.LevelRules;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.farm.FarmService;
import com.tamagochi.tamagochi.service.membership.PainterMembershipService;
import com.tamagochi.tamagochi.service.plaza.PlazaService.HuntResult;
import com.tamagochi.tamagochi.service.spar.SparDropService;
import com.tamagochi.tamagochi.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 광장 사냥 한 마리의 비용과 보상 (스파링의 10분의 1). TrainingFlowIntegrationTest 와 같은 틀: 메모리 DB, Redis 는 흉내 */
@SpringBootTest
@ActiveProfiles("test")
class HuntServiceTest {

    @MockitoBean StringRedisTemplate redisTemplate;
    @MockitoBean FarmService farmService;
    @MockitoBean PainterMembershipService membershipService;

    @Autowired HuntService huntService;
    @Autowired SparDropService sparDropService;
    @Autowired UserService userService;
    @Autowired TamagotchiRepository tamagotchiRepository;
    @Autowired PointWalletRepository walletRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private final String enemy = SparRoster.all().get(0).id();
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        reset(farmService, membershipService);
        user = userService.createUser("toss_hunt_" + System.nanoTime(), "사냥");
    }

    /** satiety: 포만감(100 - hunger). level 이 null 이면 처음 상태 */
    private Tamagotchi pet(EvolutionStage stage, int satiety, int strength, int poop, boolean sleeping, Integer level, Integer exp) {
        Tamagotchi.TamagotchiBuilder b = Tamagotchi.builder().user(user).name("끄적이").evolutionStage(stage)
                .hunger(100 - satiety).strength(strength).poopCount(poop).sleeping(sleeping);
        if (level != null) {
            b.level(level).exp(exp).stageStartLevel(LevelRules.START_LEVEL).pendingExp(0)
                    .weightDg(LevelRules.START_WEIGHT_DG).might(0);
        }
        return tamagotchiRepository.save(b.build());
    }

    private Tamagotchi pet() { return pet(EvolutionStage.BABY, 60, 60, 0, false, null, null); }

    private Tamagotchi reload() { return tamagotchiRepository.findActiveByUserId(user.getId()).orElseThrow(); }

    private int ink() { return walletRepository.findByUserId(user.getId()).orElseThrow().getBalance(); }

    @Test
    void 한_마리에_포만감_파워_피로가_하나씩_들고_경험치_둘_잉크_하나를_받는다() {
        pet();
        int inkBefore = ink();
        HuntResult r = huntService.hunt(user.getId(), enemy, false);
        Tamagotchi t = reload();
        assertThat(r.ok()).isTrue();
        assertThat(r.why()).isNull();
        assertThat(r.exp()).isEqualTo(2);
        assertThat(r.ink()).isEqualTo(1);
        assertThat(r.satiety()).isEqualTo(59);
        assertThat(r.power()).isEqualTo(59);
        assertThat(t.getHunger()).isEqualTo(41);
        assertThat(t.getStrength()).isEqualTo(59);
        assertThat(t.fatigue()).isEqualTo(1);
        assertThat(t.exp()).isEqualTo(2);
        assertThat(t.getLastTrainedAt()).isNotNull();
        assertThat(ink() - inkBefore).isEqualTo(1);
    }

    @Test
    void 읽어_둔_사이_다른_트랜잭션이_올린_경험치는_다른_열만_저장해도_남는다() throws Exception {
        pet();
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            Tamagotchi stale = tamagotchiRepository.findActiveByUserId(user.getId()).orElseThrow();   // 스케줄러처럼 읽어 둔다
            Thread other = new Thread(() -> huntService.hunt(user.getId(), enemy, false));   // 그 사이 사냥이 커밋된다
            other.start();
            try { other.join(10_000); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            stale.rename("바뀐이름");   // 스케줄러가 포만감 같은 다른 열만 바꾸는 것과 같다
        });
        Tamagotchi t = reload();
        assertThat(t.getName()).isEqualTo("바뀐이름");
        assertThat(t.exp()).isEqualTo(2);   // 옛 값(0)으로 덮이지 않는다
    }

    @Test
    void 진화_가능_구간에서는_경험치가_하나다() {
        pet(EvolutionStage.BABY, 60, 60, 0, false, 6, 0);   // 낙서의 진화 가능 구간은 Lv.6
        HuntResult r = huntService.hunt(user.getId(), enemy, false);
        assertThat(r.exp()).isEqualTo(1);
        assertThat(reload().exp()).isEqualTo(1);
    }

    @Test
    void 지저분하면_경험치가_절반이다() {
        pet(EvolutionStage.BABY, 60, 60, 5, false, null, null);
        assertThat(huntService.hunt(user.getId(), enemy, false).exp()).isEqualTo(1);
    }

    @Test
    void 화가_회원은_경험치와_잉크가_두_배다() {
        when(membershipService.isPainter(user.getId())).thenReturn(true);
        pet();
        int inkBefore = ink();
        HuntResult r = huntService.hunt(user.getId(), enemy, false);
        assertThat(r.exp()).isEqualTo(4);
        assertThat(r.ink()).isEqualTo(2);
        assertThat(reload().exp()).isEqualTo(4);
        assertThat(ink() - inkBefore).isEqualTo(2);
    }

    @Test
    void 위력_무게_승패_광고_몫은_그대로다() {
        pet();
        Tamagotchi before = reload();
        int might = before.might(), weight = before.weightDg(), wins = before.wins(), losses = before.losses();
        huntService.hunt(user.getId(), enemy, false);
        Tamagotchi t = reload();
        assertThat(t.might()).isEqualTo(might);
        assertThat(t.weightDg()).isEqualTo(weight);
        assertThat(t.wins()).isEqualTo(wins);
        assertThat(t.losses()).isEqualTo(losses);
        assertThat(t.pendingExp()).isZero();
    }

    @Test
    void 포만감이_스물_이하면_못_하고_아무것도_바뀌지_않는다() {
        pet(EvolutionStage.BABY, 20, 60, 0, false, null, null);
        int inkBefore = ink();
        HuntResult r = huntService.hunt(user.getId(), enemy, true);
        assertThat(r.ok()).isFalse();
        assertThat(r.why()).isEqualTo("hungry");
        Tamagotchi t = reload();
        assertThat(t.getHunger()).isEqualTo(80);
        assertThat(t.getStrength()).isEqualTo(60);
        assertThat(t.exp()).isZero();
        assertThat(ink()).isEqualTo(inkBefore);
        verify(farmService, never()).progress(any(User.class), any(), anyInt());
    }

    @Test
    void 파워가_스물_미만이면_weak() {
        pet(EvolutionStage.BABY, 60, 19, 0, false, null, null);
        HuntResult r = huntService.hunt(user.getId(), enemy, false);
        assertThat(r.ok()).isFalse();
        assertThat(r.why()).isEqualTo("weak");
        assertThat(reload().getStrength()).isEqualTo(19);
    }

    @Test
    void 자는_중이면_asleep() {
        pet(EvolutionStage.BABY, 60, 60, 0, true, null, null);
        HuntResult r = huntService.hunt(user.getId(), enemy, false);
        assertThat(r.ok()).isFalse();
        assertThat(r.why()).isEqualTo("asleep");
    }

    @Test
    void 마지막_한_마리로_스물이_되면_이번_것은_받고_다음부터_못_한다() {
        pet(EvolutionStage.BABY, 21, 60, 0, false, null, null);
        HuntResult last = huntService.hunt(user.getId(), enemy, false);
        assertThat(last.ok()).isTrue();
        assertThat(last.why()).isEqualTo("hungry");
        assertThat(last.exp()).isEqualTo(2);
        HuntResult next = huntService.hunt(user.getId(), enemy, false);
        assertThat(next.ok()).isFalse();
        assertThat(next.why()).isEqualTo("hungry");
    }

    @Test
    void 조각_확률을_일로_주면_그_상대의_조각이_나온다() {
        SparDrop drop = sparDropService.roll(user, enemy, 1.0);
        assertThat(drop).isNotNull();
        assertThat(drop.count()).isEqualTo(1);
    }

    @Test
    void 영으로_주면_안_나온다() {
        assertThat(sparDropService.roll(user, enemy, 0.0)).isNull();
    }

    @Test
    void 열_마리째면_농장_트레이닝_퀘스트가_하나_오른다() {
        pet();
        huntService.hunt(user.getId(), enemy, true);
        verify(farmService).progress(any(User.class), eq(FarmCatalog.Quest.TRAIN), eq(1));
    }

    @Test
    void 첫걸음_트레이닝은_세지_않는다() {
        pet();
        huntService.hunt(user.getId(), enemy, false);
        verify(farmService, never()).progress(any(User.class), any(), anyInt());
    }

    @Test
    void 잉크는_마리마다_다른_멱등_키로_준다() {
        pet();
        int before = ink();
        huntService.hunt(user.getId(), enemy, false);
        huntService.hunt(user.getId(), enemy, false);
        assertThat(ink() - before).isEqualTo(2);
    }

    @Test
    void 레벨이_오르면_lvUp_이고_단계가_바뀌면_stage_가_바뀐다() {
        int need = LevelRules.expToNext(1);
        pet(EvolutionStage.BABY, 60, 60, 0, false, 1, need - 1);
        HuntResult up = huntService.hunt(user.getId(), enemy, false);
        assertThat(up.lvUp()).isTrue();
        assertThat(up.level()).isEqualTo(2);
        assertThat(up.stage()).isEqualTo("BABY");

        // 낙서의 진화 가능 구간 끝(Lv.10)을 넘기면 저절로 스케치가 된다
        tamagotchiRepository.delete(reload());
        pet(EvolutionStage.BABY, 60, 60, 0, false, 10, LevelRules.ZONE_EXP_PER_LEVEL - 1);
        HuntResult evolved = huntService.hunt(user.getId(), enemy, false);
        assertThat(evolved.lvUp()).isTrue();
        assertThat(evolved.level()).isEqualTo(11);
        assertThat(evolved.stage()).isEqualTo("CHILD");
    }
}
