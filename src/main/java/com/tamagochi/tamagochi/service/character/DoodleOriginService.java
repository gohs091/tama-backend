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
