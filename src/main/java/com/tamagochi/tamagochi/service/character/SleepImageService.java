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
    private final com.tamagochi.tamagochi.service.membership.PainterMembershipService membershipService;

    /** 시작 결과: charged 가 true 면 이번에 잉크를 냈다 (실패 시 돌려줘야 한다). redo 면 잃어버린 이미지를 무료로 다시 만든다 */
    public record Start(EvolutionStage stage, boolean charged, boolean redo) {
        public Start(EvolutionStage stage, boolean charged) { this(stage, charged, false); }
    }

    public Start begin(User user, String stageName, String idempotencyKey) {
        return begin(user, stageName, idempotencyKey, false);
    }

    @Transactional
    public Start begin(User user, String stageName, String idempotencyKey, boolean redo) {
        EvolutionStage stage = parse(stageName);
        Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("끄적이를 찾을 수 없어요."));
        if (pet.isDead()) throw new IllegalStateException("끄적이가 죽어서 만들 수 없어요.");
        if (pet.getEvolutionStage() != stage) throw new IllegalStateException("지금 단계의 모습만 만들 수 있어요.");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalStateException("요청 키가 필요해요.");
        if (pet.hasSleepImage(stage)) {
            // 이미지는 기기에만 저장된다. 기기에서 잃어버렸으면 단계마다 한 번 무료로 다시 만든다
            if (!redo) throw new IllegalStateException("이 단계의 자는 모습은 이미 만들었어요.");
            if (pet.hasSleepImageRedo(stage)) throw new IllegalStateException("이 단계의 자는 모습은 이미 다시 만들었어요.");
            pet.markSleepImageRedo(stage);
            return new Start(stage, false, true);
        }

        // 2026-11-07: 자는 모습은 화가 회원 혜택이다. 예전에 잉크로 산 끄적이는 계속 무료로 만든다. 더는 잉크로 팔지 않는다
        if (!pet.isSleepImagePaidByInk()) {
            if (!membershipService.isPainter(user.getId())) throw new IllegalStateException("자는 모습은 화가 회원이 만들 수 있어요.");
            pet.markSleepImageByMember();
        }
        pet.markSleepImage(stage);
        return new Start(stage, false);
    }

    /** 만들기에 실패했을 때: 단계 기록을 지우고, 이번에 잉크를 냈다면 돌려준다 */
    @Transactional
    public void abort(User user, Start start, String idempotencyKey) {
        tamagotchiRepository.findActiveByUserId(user.getId()).ifPresent(pet -> {
            if (start.redo()) pet.clearSleepImageRedo(start.stage());
            else pet.clearSleepImage(start.stage());
        });
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
