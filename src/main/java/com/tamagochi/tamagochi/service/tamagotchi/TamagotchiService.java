package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
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

    private void checkEvolution(Tamagotchi tamagotchi) {
        EvolutionStage currentStage = tamagotchi.getEvolutionStage();

        EvolutionStage nextStage = switch (currentStage) {
            case EGG -> tamagotchi.getFeedCount() >= 5 ? EvolutionStage.BABY : null;
            case BABY -> tamagotchi.getFeedCount() >= 15 ? EvolutionStage.CHILD : null;
            case CHILD -> tamagotchi.getFeedCount() >= 30 ? EvolutionStage.ADULT : null;
            case ADULT -> null;
        };

        if (nextStage != null) {
            tamagotchi.evolve(nextStage);
            log.info("진화! {} -> {}", currentStage, nextStage);
        }
    }

    @Transactional
    public Tamagotchi create(User user, String name) {
        Tamagotchi tamagotchi = Tamagotchi.builder()
                .user(user)
                .name(name)
                .evolutionStage(EvolutionStage.EGG)
                .hunger(50)
                .feedCount(0)
                .build();

        return tamagotchiRepository.save(tamagotchi);
    }

    @Transactional()
    public Tamagotchi getByUserId(Long userId) {
        return tamagotchiRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
    }
}