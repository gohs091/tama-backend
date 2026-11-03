package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.land.LandCatalog;
import com.tamagochi.tamagochi.domain.spar.SparRoster;
import com.tamagochi.tamagochi.domain.tamagotchi.TrainingSession;
import com.tamagochi.tamagochi.service.storage.PetImageUrls;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

@Getter
@Builder
public class TrainingStartResponse {
    private String sessionId;
    private long minSeconds; // 이 시간이 지나기 전에는 결과를 낼 수 없다
    private Enemy enemy;     // 스파링 상대 (탭 수는 화면에서 상대가 치는 모습을 보여주는 데 쓴다)

    /**
     * key: 스파링 상대 id (SparRoster). 예전 임시 상대(crow 등)면 name·imageUrl이 null이라 앱이 가진 그림을 쓴다.
     * imageUrl: 일반 상대는 오른쪽을 보는 측면 그림, 보스(boss)는 정면 그림.
     */
    public record Enemy(String key, int level, int might, int taps, int condition,
                        String name, String faction, String power, boolean boss, String imageUrl, List<DropInk> drops) {
    }

    /** 이 상대가 떨어뜨릴 수 있는 잉크 (화면에 미리 보여준다) */
    public record DropInk(String id, String emoji, String name, String grade) {
    }

    public static TrainingStartResponse of(TrainingSession session) {
        SparRoster.Enemy e = SparRoster.find(session.getEnemyKey()).orElse(null);
        List<DropInk> drops = e == null ? List.of() : e.drops().stream()
                .map(LandCatalog::ink).flatMap(Optional::stream)
                .map(i -> new DropInk(i.id(), i.emoji(), i.name(), i.grade().name()))
                .toList();
        return TrainingStartResponse.builder()
                .sessionId(session.getId())
                .minSeconds(TrainingSession.MIN_ELAPSED.toSeconds())
                .enemy(new Enemy(session.getEnemyKey(), session.getEnemyLevel(), session.getEnemyMight(),
                        session.getEnemyTaps(), session.getEnemyCondition(),
                        e == null ? null : e.name(), e == null ? null : e.faction(), e == null ? null : e.power().name(),
                        e != null && e.boss(), e == null ? null : PetImageUrls.of(e.imageKey()), drops))
                .build();
    }
}
