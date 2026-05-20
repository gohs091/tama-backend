package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingResult;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TamagotchiService {

    private final TamagotchiRepository tamagotchiRepository;
    private final PointService pointService;

    @Transactional
    public void feed(User user, String idempotencyKey) {

        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        // 1. 포인트 차감 (멱등성 처리 포함)
        pointService.usePoint(user, 10, idempotencyKey);

        // 2. 먹이주기
        tamagotchi.feed();

        // 3. 진화 조건 체크
        checkEvolution(tamagotchi);
    }

    // EGG: 10번 두드리면 부화 / BABY→CHILD: 25번 / CHILD→ADULT: 45번
    private void checkEvolution(Tamagotchi tamagotchi) {
        EvolutionStage currentStage = tamagotchi.getEvolutionStage();

        EvolutionStage nextStage = switch (currentStage) {
            case EGG   -> tamagotchi.getFeedCount() >= 10 ? EvolutionStage.BABY  : null;
            case BABY  -> tamagotchi.getFeedCount() >= 25 ? EvolutionStage.CHILD : null;
            case CHILD -> tamagotchi.getFeedCount() >= 45 ? EvolutionStage.ADULT : null;
            case ADULT -> null;
        };

        if (nextStage != null) {
            tamagotchi.evolve(nextStage);
            log.info("진화! {} -> {}", currentStage, nextStage);
        }
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

        tamagotchi.feed(); // feedCount++ (포인트 차감 없음)
        checkEvolution(tamagotchi);
        log.info("알 두드리기 - feedCount: {}", tamagotchi.getFeedCount());
    }

    @Transactional
    public Tamagotchi reset(User user) {
        Tamagotchi existing = tamagotchiRepository.findByUserId(user.getId()).orElse(null);
        String faction = existing != null ? existing.getFaction() : null;
        tamagotchiRepository.deleteByUserId(user.getId());
        return create(user, "끄적이", faction);
    }

    @Transactional
    public Tamagotchi create(User user, String name, String faction) {
        Tamagotchi tamagotchi = Tamagotchi.builder()
                .user(user)
                .name(name)
                .faction(faction)
                .evolutionStage(EvolutionStage.EGG)
                .hunger(50)
                .feedCount(0)
                .build();

        return tamagotchiRepository.save(tamagotchi);
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

        tamagotchi.sleep(); // 6시간 체크는 Entity 안에서 처리
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

        if (tamagotchi.getPoopCount() == 0) {
            throw new IllegalStateException("치울 똥이 없어요.");
        }

        // 포인트 차감
        pointService.usePoint(user, 3, idempotencyKey);

        tamagotchi.cleanPoop();
        log.info("청소 완료 - tamagotchi: {}", tamagotchi.getName());
    }

    @Transactional
    public TrainingResult training(User user, int clickCount, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
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
        int bonusReward = clickCount / 10;
        int totalReward = baseReward + bonusReward;

        // 포인트 지급 (idempotencyKey-earn 으로 차감과 분리)
        pointService.earnPoint(user, totalReward, idempotencyKey + "-earn");

        log.info("트레이닝 완료 - tamagotchi: {}, clickCount: {}, reward: {}",
                tamagotchi.getName(), clickCount, totalReward);

        return TrainingResult.builder()
                .clickCount(clickCount)
                .reward(totalReward)
                .build();
    }

    @Transactional
    public void eatPill(User user, String idempotencyKey) {
        Tamagotchi tamagotchi = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));

        if (tamagotchi.isDead()) {
            throw new IllegalStateException("다마고치가 죽었어요.");
        }
        if (tamagotchi.isSleeping()) {
            throw new IllegalStateException("다마고치가 자고 있어요.");
        }
        if (tamagotchi.getEvolutionStage() == EvolutionStage.EGG) {
            throw new IllegalStateException("알 상태에서는 알약을 먹을 수 없어요.");
        }

        // 포인트 차감
        pointService.usePoint(user, 10, idempotencyKey);

        // 스트렝스 증가
        tamagotchi.addStrength(20);

        log.info("알약 먹기 완료 - tamagotchi: {}, strength: {}",
                tamagotchi.getName(), tamagotchi.getStrength());
    }

}