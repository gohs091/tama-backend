package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.domain.farm.FarmCatalog;
import com.tamagochi.tamagochi.domain.spar.SparDrop;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainBlock;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.farm.FarmService;
import com.tamagochi.tamagochi.service.membership.PainterMembershipService;
import com.tamagochi.tamagochi.service.plaza.PlazaService.HuntResult;
import com.tamagochi.tamagochi.service.point.PointService;
import com.tamagochi.tamagochi.service.spar.SparDropService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 광장 3D 방에서 몬스터 한 마리를 잡은 사람에게 주는 몫. 스파링 한 판(TamagotchiService.training)의 10분의 1이다.
 * 포만감 · 파워 · 피로 1씩, 경험치 2(진화 가능 구간 1, 지저분하면 절반), 잉크 1, 잉크 조각 확률 1.5%. 화가 회원은 경험치 · 잉크 2배.
 * 레벨 업 · 진화 구간 · 때 넘긴 자동 진화는 Tamagotchi.addExp 가 스파링과 똑같이 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HuntService {

    /** 한 마리가 잉크 조각을 떨어뜨릴 확률 (스파링은 이긴 판에 15%) */
    static final double DROP_CHANCE = 0.015;
    static final int INK_PER_KILL = 1;

    private final UserRepository userRepository;
    private final TamagotchiRepository tamagotchiRepository;
    private final PointService pointService;
    private final SparDropService sparDropService;
    private final FarmService farmService;
    private final PainterMembershipService membershipService;

    /** 사냥할 수 있으면 null, 아니면 이유(dead/away/asleep/egg/hungry/weak). 조건은 스파링과 같다(TrainBlock) */
    public static String status(Tamagotchi t) {
        TrainBlock block = TrainBlock.of(t);
        return block == null ? null : block.why();
    }

    /**
     * 한 마리 몫을 준다. 못 하는 상태면 아무것도 바꾸지 않고 ok=false 로 돌려준다.
     * ok 는 "이번 것을 받았는가"이고, 이번 것으로 다음부터 못 하게 되면 ok=true 에 why 를 채운다.
     */
    @Transactional
    public HuntResult hunt(long userId, String enemyKey, boolean questTick) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("유저가 없어요."));
        Tamagotchi t = tamagotchiRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
        String blocked = status(t);
        if (blocked != null) {
            return new HuntResult(false, blocked, 0, 0, t.level(), t.getEvolutionStage().name(), false, null, 100 - t.getHunger(), t.getStrength());
        }

        boolean painter = membershipService.isPainter(userId);
        int levelBefore = t.level();
        int exp = t.hunt();
        if (painter) {   // 화가 회원: 경험치 2배
            t.addExp(exp);
            exp *= 2;
        }
        int ink = painter ? INK_PER_KILL * 2 : INK_PER_KILL;
        pointService.earnPoint(user, ink, "hunt-" + userId + "-" + UUID.randomUUID());   // 마리마다 다른 키
        if (questTick) farmService.progress(user, FarmCatalog.Quest.TRAIN, 1);
        SparDrop drop = sparDropService.roll(user, enemyKey, DROP_CHANCE);

        log.info("광장 사냥 - user: {}, enemy: {}, exp: {}, ink: {}, level: {}", userId, enemyKey, exp, ink, t.level());
        return new HuntResult(true, status(t), exp, ink, t.level(), t.getEvolutionStage().name(), t.level() > levelBefore,
                drop, 100 - t.getHunger(), t.getStrength());
    }
}
