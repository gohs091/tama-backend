package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionRules;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.promotion.PromotionOutcome;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingResult;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import com.tamagochi.tamagochi.service.promotion.PromotionService;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TamagotchiService {
    private static final int ITEM_COST = 2; // 고기·알약 1회 가격(포인트)
    private static final int MAX_TRAINING_CLICKS = 120; // 10초 트레이닝에서 사람이 칠 수 있는 상한(클라이언트 값은 신뢰하지 않음)

    private final TamagotchiRepository tamagotchiRepository;
    private final PointService pointService;
    private final TrainingSessionService trainingSessionService;
    private final PromotionService promotionService;

    @Transactional
    public void feed(User user, String idempotencyKey) {

        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        requireHome(tamagotchi);

        // 1. 포인트 차감 (멱등성 처리 포함)
        pointService.usePoint(user, ITEM_COST, idempotencyKey);

        // 2. 먹이주기
        tamagotchi.feed();
    }

    @Transactional
    public void hatch(User user, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
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
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
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
        Tamagotchi existing = tamagotchiRepository.findByUserId(user.getId()).orElse(null);
        if (existing == null) {
            return create(user, "끄적이", null);
        }
        existing.reset();
        return existing;
    }

    @Transactional
    public Tamagotchi create(User user, String name, String faction) {
        Tamagotchi tamagotchi = Tamagotchi.builder()
                .user(user)
                .name(name)
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
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
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
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        tamagotchi.saveStageImage(stage, imageBase64);
    }

    @Transactional()
    public Tamagotchi getByUserId(Long userId) {
        return tamagotchiRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
    }
    @Transactional
    public void sleep(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        requireHome(tamagotchi);

        tamagotchi.sleep(LocalDateTime.now()); // 졸린 시간대인지는 Entity 안에서 확인
    }

    @Transactional
    public void wakeUp(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        tamagotchi.wakeUp();
    }

    @Transactional
    public void clean(User user, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
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
    public TrainingSession startTraining(User user) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        requireTrainable(tamagotchi);
        return trainingSessionService.start(user);
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
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        requireTrainable(tamagotchi);
        // 서버가 발급한 세션으로, 시작 후 10초가 지난 뒤에만 한 번 제출할 수 있다
        TrainingSession session = trainingSessionService.consume(user, sessionId);

        // 트레이닝 소모
        tamagotchi.consumeForTraining(20, 20);

        // 리워드 계산 (진화 단계별 + 클릭 수 기반)
        int baseReward = switch (tamagotchi.getEvolutionStage()) {
            case BABY -> 1;
            case CHILD -> 2;
            case ADULT -> 3;
            default -> 0;
        };

        // 클릭 수 10번당 1포인트 추가
        int taps = Math.min(Math.max(clickCount, 0), MAX_TRAINING_CLICKS);
        int bonusReward = taps / 10;
        int totalReward = baseReward + bonusReward;

        // 포인트 지급 (idempotencyKey-earn 으로 차감과 분리)
        pointService.earnPoint(user, totalReward, idempotencyKey + "-earn");

        // 진화 게이지: 탭 수로 받을 양을 서버가 정하되, 바로 오르지 않고 광고를 본 뒤 claim으로 오른다
        tamagotchi.offerEvoGain(EvolutionRules.gainForTaps(taps));
        int evoGain = tamagotchi.getPendingEvoGain();

        // 완성작의 트레이닝 결과는 프로모션 대상 여부를 판정한다 (꺼져 있으면 null)
        PromotionOutcome promotion = tamagotchi.getEvolutionStage() == EvolutionStage.ADULT
                ? promotionService.evaluate(user, session.getId(), taps)
                : null;

        log.info("트레이닝 완료 - tamagotchi: {}, clickCount: {}, reward: {}, evoGain: {}, evoGauge: {}",
                tamagotchi.getName(), clickCount, totalReward, evoGain, tamagotchi.getEvoGauge());

        return TrainingResult.builder()
                .clickCount(clickCount)
                .reward(totalReward)
                .evoGain(evoGain)
                .evoGauge(tamagotchi.getEvoGauge())
                .promotion(promotion)
                .build();
    }

    @Transactional
    public void eatPill(User user, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        requireHome(tamagotchi);
        if (tamagotchi.getEvolutionStage() == EvolutionStage.EGG) {
            throw new IllegalStateException("알 상태에서는 알약을 먹을 수 없어요.");
        }

        // 포인트 차감
        pointService.usePoint(user, ITEM_COST, idempotencyKey);

        // 스트렝스 증가
        tamagotchi.addStrength(Tamagotchi.PILL_AMOUNT);

        log.info("알약 먹기 완료 - tamagotchi: {}, strength: {}",
                tamagotchi.getName(), tamagotchi.getStrength());
    }

}