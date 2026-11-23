package com.tamagochi.tamagochi.service.character;

import com.tamagochi.tamagochi.domain.character.DoodleOrigin;
import com.tamagochi.tamagochi.repository.character.DoodleOriginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 처음 그린 그림의 분석 결과를 저장하고 읽는다. 저장 실패는 이미지 생성을 막지 않는다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoodleOriginService {

    private final DoodleOriginRepository repository;

    @Transactional(readOnly = true)
    public Optional<DoodleSubject> find(String tossUserId) {
        if (tossUserId == null || tossUserId.isBlank()) return Optional.empty();
        try {
            return repository.findByTossUserId(tossUserId).map(o ->
                    new DoodleSubject(o.getSubject(), o.getKind(), o.getParts(), o.getSubjectKo(), o.getTraits(), o.getBodyPlan(), o.getAttach()));
        } catch (Exception e) {
            log.warn("그림 정체 조회 실패 - {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 저장된 분석을 지운다: 새 낙서를 알아보지 못했을 때, 이전 끄적이의 정체가 새 끄적이에게 이어지지 않게 */
    @Transactional
    public void forget(String tossUserId) {
        if (tossUserId == null || tossUserId.isBlank()) return;
        try {
            repository.findByTossUserId(tossUserId).ifPresent(repository::delete);
        } catch (Exception e) {
            log.warn("그림 정체 지우기 실패 - {}", e.getMessage());
        }
    }

    @Transactional
    public void save(String tossUserId, DoodleSubject s) {
        if (tossUserId == null || tossUserId.isBlank() || s == null) return;
        try {
            DoodleOrigin origin = repository.findByTossUserId(tossUserId).orElseGet(() -> new DoodleOrigin(tossUserId));
            origin.update(s.subject(), s.kind(), s.subjectKo(), s.parts(), s.traits(), s.bodyPlan(), s.attach());
            repository.save(origin);
        } catch (Exception e) {
            log.warn("그림 정체 저장 실패 - {}", e.getMessage());
        }
    }
}
