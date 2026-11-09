package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.BattleRules;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.promotion.PromotionOutcome;
import com.tamagochi.tamagochi.domain.spar.SparDrop;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingResult;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import com.tamagochi.tamagochi.service.promotion.PromotionService;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import com.tamagochi.tamagochi.service.spar.SparDropService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TamagotchiService {
    private static final int ITEM_COST = 5; // 고기·알약 1회 가격(잉크). 한 번에 포만감·파워 +3
    private static final int MAX_TRAINING_CLICKS = 60; // 5초 트레이닝에서 사람이 칠 수 있는 상한(클라이언트 값은 신뢰하지 않음)
    private static final int TRAINING_COST = 10; // 트레이닝 1번에 줄어드는 포만감·파워 (채우는 데 고기·알약 각 4번, 잉크 40)

    private final TamagotchiRepository tamagotchiRepository;
    private final PointService pointService;
    private final TrainingSessionService trainingSessionService;
    private final SparDropService sparDropService;
    private final PromotionService promotionService;
    private final com.tamagochi.tamagochi.service.membership.PainterMembershipService membershipService;

    /**
     * 한 판만큼 회복: 고기·알약을 각각 최대 4개(+12)까지 한꺼번에 먹이고, 실제로 먹인 만큼만 잉크를 받는다(최대 40).
     * 가득 찬 쪽은 먹이지 않는다. 결과 화면의 회복 버튼과 화가 회원 자동 반복이 쓴다.
     */
    @Transactional
    public Tamagotchi recover(User user, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        if (tamagotchi.isDead()) throw new IllegalStateException("다마고치가 죽었어요.");
        requireHome(tamagotchi);
        if (tamagotchi.getEvolutionStage() == EvolutionStage.EGG) throw new IllegalStateException("알 상태에서는 회복할 수 없어요.");
        Tamagotchi.Recovery plan = tamagotchi.planRecovery();
        if (plan.items() == 0) throw new IllegalStateException("포만감과 파워가 이미 가득 찼어요.");
        pointService.usePoint(user, plan.items() * ITEM_COST, idempotencyKey);
        tamagotchi.recover(plan);
        log.info("한 판만큼 회복 - user={}, 고기 {} · 알약 {}, 잉크 {}", user.getId(), plan.meats(), plan.pills(), plan.items() * ITEM_COST);
        return tamagotchi;
    }

    @Transactional
    public void feed(User user, String idempotencyKey) {

        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        requireHome(tamagotchi);
        if (tamagotchi.isFull()) {
            throw new IllegalStateException("배가 불러서 더 먹을 수 없어요.");
        }

        // 1. 포인트 차감 (멱등성 처리 포함)
        pointService.usePoint(user, ITEM_COST, idempotencyKey);

        // 2. 먹이주기
        tamagotchi.feed();
    }

    @Transactional
    public void hatch(User user, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.getEvolutionStage() != EvolutionStage.EGG) {
            throw new IllegalStateException("알 상태가 아닙니다.");
        }
        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }

        tamagotchi.hatch(); // 알 두드리기는 클라이언트 연출. 서버는 낙서로 올린다 (포인트 차감 없음)
        log.info("부화 - tamagotchi: {}", tamagotchi.getName());
    }

    /** 게이지가 가득 차고 컨디션 조건을 만족했을 때만 다음 단계로 진화시킨다. 판정은 서버가 한다. */
    @Transactional
    public Tamagotchi evolve(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        EvolutionStage from = tamagotchi.getEvolutionStage();
        tamagotchi.evolveIfReady();
        log.info("진화! {} -> {}", from, tamagotchi.getEvolutionStage());
        return tamagotchi;
    }

    /**
     * 새 알로 다시 시작. 기존 행을 삭제 후 삽입하면 같은 트랜잭션에서 insert가 delete보다 먼저 flush되어
     * 유저당 1개 유니크 제약에 걸리므로, 기존 행을 제자리에서 초기화한다. (팩션은 유지)
     */
    @Transactional
    public Tamagotchi reset(User user) {
        Tamagotchi existing = tamagotchiRepository.findActiveByUserId(user.getId()).orElse(null);
        if (existing == null) {
            return create(user, "끄적이", null);
        }
        existing.reset();
        return existing;
    }

    /** 지금 키우는 끄적이 이름 짓기·바꾸기 */
    @Transactional
    public Tamagotchi rename(User user, String rawName) {
        String name = PetNameRules.clean(rawName);
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        tamagotchi.rename(name);
        log.info("끄적이 이름 - user={}, pet={}", user.getId(), tamagotchi.getPublicId());
        return tamagotchi;
    }

    @Transactional
    public Tamagotchi create(User user, String name, String faction) {
        Tamagotchi tamagotchi = Tamagotchi.builder()
                .user(user)
                .name(name == null || name.isBlank() ? Tamagotchi.DEFAULT_NAME : PetNameRules.clean(name))
                .faction(faction)
                .evolutionStage(EvolutionStage.EGG)
                .hunger(Tamagotchi.INITIAL_HUNGER)
                .strength(Tamagotchi.INITIAL_STRENGTH)
                .feedCount(0)
                .sleepyStartHour(Tamagotchi.randomSleepyStartHour())
                .build();

        return tamagotchiRepository.save(tamagotchi);
    }

    /** 테스트 전용: 피드 횟수 없이 다음 진화 단계로 즉시 이동 */
    @Transactional
    public Tamagotchi forceEvolve(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        EvolutionStage next = switch (tamagotchi.getEvolutionStage()) {
            case EGG   -> EvolutionStage.BABY;
            case BABY  -> EvolutionStage.CHILD;
            case CHILD -> EvolutionStage.ADULT;
            case ADULT -> throw new IllegalStateException("이미 완성작입니다.");
        };

        tamagotchi.evolve(next);
        log.info("[TEST] 강제 진화: {} -> {}", tamagotchi.getEvolutionStage(), next);
        return tamagotchi;
    }

    @Transactional
    public void saveImage(User user, String stage, String imageBase64) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        tamagotchi.saveStageImage(stage, imageBase64);
    }

    @Transactional()
    public Tamagotchi getByUserId(Long userId) {
        return tamagotchiRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
    }
    @Transactional
    public void sleep(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        requireHome(tamagotchi);

        tamagotchi.sleep(LocalDateTime.now()); // 언제든 잠든다. 피로만큼 자고 혼자 깬다
    }

    @Transactional
    public void wakeUp(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        tamagotchi.wakeUp();
    }

    @Transactional
    public void clean(User user, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        if (tamagotchi.getPoopCount() == 0) {
            throw new IllegalStateException("치울 똥이 없어요.");
        }

        // 포인트 차감
        pointService.usePoint(user, 3, idempotencyKey);

        tamagotchi.cleanPoop();
        log.info("청소 완료 - tamagotchi: {}", tamagotchi.getName());
    }

    /** 트레이닝을 시작한다. 시작 시각은 서버가 재고, 결과는 이 세션으로만 제출할 수 있다. */
    @Transactional
    public TrainingSession startTraining(User user, String rematchSessionId) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        requireTrainable(tamagotchi);
        TrainingSession session = trainingSessionService.start(user);
        // 설욕전이면 지난 상대를, 아니면 (아직 상대가 없을 때만) 내 레벨·위력에 맞춘 새 상대를 정한다
        java.util.Optional<TrainingSession> previous = trainingSessionService.findMine(user, rematchSessionId)
                .filter(TrainingSession::hasEnemy);
        if (previous.isPresent()) session.rematch(previous.get());
        else if (!session.hasEnemy()) session.setEnemy(BattleRules.randomEnemy(tamagotchi.level(), tamagotchi.might()));
        return session;
    }

    // 잉크통에 들어가 있거나 모험을 떠나 있으면 돌보기·훈련을 할 수 없다
    private void requireHome(Tamagotchi tamagotchi) {
        if (tamagotchi.isBathing()) {
            throw new IllegalStateException("잉크통에 들어가 있어요. 꺼낸 뒤에 해 주세요.");
        }
        if (tamagotchi.isAdventuring()) {
            throw new IllegalStateException("모험을 떠나 있어요. 돌아온 뒤에 해 주세요.");
        }
    }

    private void requireTrainable(Tamagotchi tamagotchi) {
        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        requireHome(tamagotchi);
        if (tamagotchi.isSleeping()) {
            throw new IllegalStateException("다마고치가 자고 있어요.");
        }
        if (tamagotchi.getEvolutionStage() == EvolutionStage.EGG) {
            throw new IllegalStateException("알 상태에서는 트레이닝할 수 없어요.");
        }
        if (tamagotchi.getHunger() >= 80) {
            throw new IllegalStateException("배가 너무 고파서 트레이닝할 수 없어요.");
        }
        if (tamagotchi.getStrength() < 20) {
            throw new IllegalStateException("스트렝스가 부족해서 트레이닝할 수 없어요.");
        }
    }

    @Transactional
    public TrainingResult training(User user, String sessionId, int clickCount, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        requireTrainable(tamagotchi);
        // 서버가 발급한 세션으로, 시작 후 10초가 지난 뒤에만 한 번 제출할 수 있다
        TrainingSession session = trainingSessionService.consume(user, sessionId);

        // 스파링 판정: 컨디션은 트레이닝 소모 전 값. 상대와 운은 시작할 때 정해 둔 값
        int taps = Math.min(Math.max(clickCount, 0), MAX_TRAINING_CLICKS);
        if (!session.hasEnemy()) session.setEnemy(BattleRules.randomEnemy(tamagotchi.level(), tamagotchi.might()));
        int condition = ((100 - tamagotchi.getHunger()) + tamagotchi.getStrength()) / 2;
        double myPower = BattleRules.power(tamagotchi.might(), taps, condition, session.getMyLuck());
        double enemyPower = BattleRules.power(session.getEnemyMight(), session.getEnemyTaps(), session.getEnemyCondition(), session.getEnemyLuck());
        boolean win = myPower > enemyPower;

        // 트레이닝 소모 (포만감·파워 10씩: 채우는 데 잉크 40)
        tamagotchi.consumeForTraining(TRAINING_COST, TRAINING_COST);
        tamagotchi.addFatigue(Tamagotchi.FATIGUE_TRAINING);

        // 리워드 계산 (진화 단계별 + 클릭 수 기반)
        int baseReward = switch (tamagotchi.getEvolutionStage()) {
            case BABY -> 1;
            case CHILD -> 2;
            case ADULT -> 3;
            default -> 0;
        };

        // 클릭 수 5번당 1포인트 추가 (5초 트레이닝)
        int bonusReward = taps / 5;
        int totalReward = baseReward + bonusReward;
        boolean painter = membershipService.isPainter(user.getId());
        if (painter) totalReward *= 2; // 화가 회원: 잉크 보상 2배

        // 포인트 지급 (idempotencyKey-earn 으로 차감과 분리)
        pointService.earnPoint(user, totalReward, idempotencyKey + "-earn");

        // 경험치는 바로 받고(이기면 보너스), 광고를 보면 기본 경험치만큼 더 받는다. 위력·무게도 바로 바뀐다
        int mightBefore = tamagotchi.might();
        Tamagotchi.TrainingGain gain = tamagotchi.train(taps, win);
        if (painter) tamagotchi.claimExp(); // 화가 회원: 광고 없이 경험치 2배 (광고로 받던 몫을 바로 받는다)

        // 완성작의 트레이닝 결과는 프로모션 대상 여부를 판정한다 (꺼져 있으면 null)
        PromotionOutcome promotion = tamagotchi.getEvolutionStage() == EvolutionStage.ADULT
                ? promotionService.evaluate(user, session.getId(), taps)
                : null;

        // 이기면 상대가 잉크 조각을 떨어뜨릴 수 있다 (낮은 고정 확률, 성적과 무관)
        SparDrop drop = sparDropService.roll(user, session.getEnemyKey(), win);

        log.info("트레이닝 완료 - tamagotchi: {}, taps: {}, win: {} ({} vs {}), reward: {}, exp: {}, level: {}",
                tamagotchi.getName(), taps, win, Math.round(myPower), Math.round(enemyPower), totalReward, gain.exp(), tamagotchi.level());

        return TrainingResult.builder()
                .clickCount(clickCount)
                .reward(totalReward)
                .evoGain(tamagotchi.legacyPendingEvoGain())
                .evoGauge(tamagotchi.legacyEvoGauge())
                .win(win)
                .enemyTaps(session.getEnemyTaps())
                .expGain(gain.exp())
                .winBonus(gain.winBonus())
                .pendingExp(tamagotchi.pendingExp())
                .mightGain(tamagotchi.might() - mightBefore)
                .wins(tamagotchi.wins())
                .losses(tamagotchi.losses())
                .level(tamagotchi.level())
                .exp(tamagotchi.exp())
                .expToNext(tamagotchi.expToNext())
                .inEvolutionZone(tamagotchi.isInEvolutionZone())
                .promotion(promotion)
                .drop(drop)
                .build();
    }

    /** 개발용: 다음 레벨까지 남은 경험치를 바로 넣는다 */
    @Transactional
    public Tamagotchi devLevelUp(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        tamagotchi.addExp(tamagotchi.expToNext() - tamagotchi.exp());
        log.info("[TEST] 레벨 업: Lv.{}", tamagotchi.level());
        return tamagotchi;
    }

    @Transactional
    public void eatPill(User user, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        requireHome(tamagotchi);
        if (tamagotchi.getEvolutionStage() == EvolutionStage.EGG) {
            throw new IllegalStateException("알 상태에서는 알약을 먹을 수 없어요.");
        }
        if (tamagotchi.getStrength() >= 100) {
            throw new IllegalStateException("파워가 가득 차서 더 먹을 수 없어요.");
        }

        // 포인트 차감
        pointService.usePoint(user, ITEM_COST, idempotencyKey);

        // 스트렝스 증가
        tamagotchi.addStrength(Tamagotchi.PILL_AMOUNT);

        log.info("알약 먹기 완료 - tamagotchi: {}, strength: {}",
                tamagotchi.getName(), tamagotchi.getStrength());
    }

}