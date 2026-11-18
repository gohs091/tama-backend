package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/** 갓 태어난 끄적이가 마음에 안 들면 다시 그린다. 이미지 생성을 아끼려고 하루 횟수를 서버가 센다 (날짜는 서버 시계, KST) */
@Service
@RequiredArgsConstructor
public class RedrawService {

    private final TamagotchiRepository tamagotchiRepository;
    private final Clock clock;
    private final com.tamagochi.tamagochi.service.tower.ExpeditionGuard expeditionGuard;

    public record Status(int left, int max) {}

    @Transactional
    public Status status(User user) {
        return new Status(pet(user).redrawsLeft(LocalDate.now(clock)), Tamagotchi.REDRAWS_PER_DAY);
    }

    @Transactional
    public Status redraw(User user) {
        Tamagotchi pet = pet(user);
        expeditionGuard.requireHomeToRestart(user, pet.isDead()); // 탑 원정 중에는 다시 그릴 수 없다 (횟수를 쓰기 전에 확인)
        LocalDate today = LocalDate.now(clock);
        pet.redraw(today);
        return new Status(pet.redrawsLeft(today), Tamagotchi.REDRAWS_PER_DAY);
    }

    /**
     * 진화 후보 다시 그리기는 없앴다: 잘 그려진 진화 그림을 다시 그리면 그때마다 이미지 비용이 든다 (회원은 하루 5번까지 됐다).
     * 그리기에 실패했을 때의 재시도 1번은 횟수를 쓰지 않고 ImageQuotaService의 단계별 하루 한도(EVOLVED_BASE_PER_STAGE) 안에서 된다.
     * 예전 앱이 물어 오면 남은 횟수 0으로 답해서 "다시 그리기" 버튼이 눌리지 않게 한다.
     */
    public Status candidateStatus(User user) {
        return new Status(0, 0);
    }

    /** 예전 앱이 다시 그리기를 요청하면 거절한다 (횟수도 늘리지 않는다) */
    public Status candidateRedraw(User user) {
        throw new IllegalStateException("진화한 모습은 다시 그릴 수 없어요.");
    }

    private Tamagotchi pet(User user) {
        return tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
    }
}
