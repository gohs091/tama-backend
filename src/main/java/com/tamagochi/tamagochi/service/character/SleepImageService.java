package com.tamagochi.tamagochi.service.character;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 자는 모습 이미지: 끄적이 한 마리당 한 번 잉크 {@value #PRICE}개를 내면 이후 단계(진화한 모습)도 무료로 만들 수 있다.
 * 죽어서 새로 시작하면 다시 값을 내야 한다. 만들기에 실패하면 값을 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class SleepImageService {

    public static final int PRICE = 1000;

    private final TamagotchiRepository tamagotchiRepository;
    private final PointService pointService;

    /** 시작 결과: charged 가 true 면 이번에 잉크를 냈다 (실패 시 돌려줘야 한다) */
    public record Start(EvolutionStage stage, boolean charged) {}

    @Transactional
    public Start begin(User user, String stageName, String idempotencyKey) {
        EvolutionStage stage = parse(stageName);
        Tamagotchi pet = tamagotchiRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("끄적이를 찾을 수 없어요."));
        if (pet.isDead()) throw new IllegalStateException("끄적이가 죽어서 만들 수 없어요.");
        if (pet.getEvolutionStage() != stage) throw new IllegalStateException("지금 단계의 모습만 만들 수 있어요.");
        if (pet.hasSleepImage(stage)) throw new IllegalStateException("이 단계의 자는 모습은 이미 만들었어요.");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalStateException("요청 키가 필요해요.");

        boolean charged = !pet.isSleepImageUnlocked();
        if (charged) pointService.usePoint(user, PRICE, "sleep-image-" + idempotencyKey);
        pet.markSleepImage(stage);
        return new Start(stage, charged);
    }

    /** 만들기에 실패했을 때: 단계 기록을 지우고, 이번에 잉크를 냈다면 돌려준다 */
    @Transactional
    public void abort(User user, Start start, String idempotencyKey) {
        tamagotchiRepository.findByUserId(user.getId()).ifPresent(pet -> pet.clearSleepImage(start.stage()));
        if (start.charged()) pointService.earnPoint(user, PRICE, "sleep-image-refund-" + idempotencyKey);
    }

    static EvolutionStage parse(String stageName) {
        if (stageName == null) throw new IllegalStateException("단계가 필요해요.");
        return switch (stageName.toLowerCase(Locale.ROOT)) {
            case "baby" -> EvolutionStage.BABY;
            case "child" -> EvolutionStage.CHILD;
            case "adult" -> EvolutionStage.ADULT;
            default -> throw new IllegalStateException("만들 수 없는 단계예요.");
        };
    }
}
