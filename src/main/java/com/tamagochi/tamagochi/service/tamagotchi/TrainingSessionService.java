package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TrainingSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    private final TrainingSessionRepository repository;
    private final Clock clock;

    /**
     * 트레이닝 세션을 시작한다. 아직 안 쓴 세션이 만료 전이면 새로 만들지 않고 그걸 돌려준다
     * (시작 요청을 연타해 세션을 쌓거나, 새 세션으로 시작 시각을 늦추는 것을 막는다).
     */
    @Transactional
    public TrainingSession start(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findFirstByUserIdAndUsedAtIsNullOrderByStartedAtDesc(user.getId())
                .filter(open -> !open.isExpired(now))
                .orElseGet(() -> repository.save(new TrainingSession(UUID.randomUUID().toString(), user, now)));
    }

    /** 내 세션 하나 (설욕전 상대를 가져올 때). 남의 세션이거나 없으면 빈 값 */
    public java.util.Optional<TrainingSession> findMine(User user, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return java.util.Optional.empty();
        return repository.findById(sessionId).filter(s -> s.getUser().getId().equals(user.getId()));
    }

    /** 결과 제출 시 세션을 확인하고 사용 처리한다. 남의 세션이거나 없는 세션, 시간이 맞지 않으면 예외. */
    @Transactional
    public TrainingSession consume(User user, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("트레이닝을 먼저 시작해 주세요.");
        }
        TrainingSession session = repository.findById(sessionId)
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalStateException("트레이닝 정보를 찾을 수 없어요."));
        session.use(LocalDateTime.now(clock));
        return session;
    }
}
