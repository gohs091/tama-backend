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

    public record Status(int left, int max) {}

    @Transactional
    public Status status(User user) {
        return new Status(pet(user).redrawsLeft(LocalDate.now(clock)), Tamagotchi.REDRAWS_PER_DAY);
    }

    @Transactional
    public Status redraw(User user) {
        Tamagotchi pet = pet(user);
        LocalDate today = LocalDate.now(clock);
        pet.redraw(today);
        return new Status(pet.redrawsLeft(today), Tamagotchi.REDRAWS_PER_DAY);
    }

    /** 진화 후보 다시 그리기 남은 횟수 (화가 회원이 생기면 하루 횟수를 여기서 늘린다) */
    @Transactional
    public Status candidateStatus(User user) {
        int perDay = Tamagotchi.CANDIDATE_REDRAWS_PER_DAY;
        return new Status(pet(user).candidateRedrawsLeft(LocalDate.now(clock), perDay), perDay);
    }

    /** 진화 후보 다시 그리기 한 번을 쓴다. 쓴 뒤 앱이 새 후보를 그린다 */
    @Transactional
    public Status candidateRedraw(User user) {
        int perDay = Tamagotchi.CANDIDATE_REDRAWS_PER_DAY;
        Tamagotchi pet = pet(user);
        LocalDate today = LocalDate.now(clock);
        pet.useCandidateRedraw(today, perDay);
        return new Status(pet.candidateRedrawsLeft(today, perDay), perDay);
    }

    private Tamagotchi pet(User user) {
        return tamagotchiRepository.findActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("다마고치가 존재하지 않습니다."));
    }
}
