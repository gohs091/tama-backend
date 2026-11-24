package com.tamagochi.tamagochi.service.plaza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.spar.SparRoster;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 3D 광장의 몬스터 규칙: 자리(resources/plaza/mobs.json), 종류별 체력, 피해, 거리 판정.
 * 종류는 스파링 상대(SparRoster)다. 자리 좌표는 광장 폭 · 높이의 천분율, 거리 판정만 세상 단위로 바꿔 한다.
 */
public final class PlazaMobs {

    /** 몬스터가 나오는 자리. 목록 순서가 몬스터 번호다 */
    public record Spawn(int x, int y, String enemy) {}

    /** 몬스터 종류: 체력과, 피해를 정할 때 보는 레벨 범위 */
    public record Kind(int hp, int minLevel, int maxLevel) {}

    /** 세상 크기 (천분율 좌표를 세상 단위로 바꿀 때 쓴다) */
    static final double WORLD_W = 72, WORLD_H = 30.4;
    /** 이 거리(세상 단위) 안이면 때릴 수 있다 */
    static final double NEAR = 5;

    private PlazaMobs() {}

    public static List<Spawn> load() {
        try (InputStream in = PlazaMobs.class.getResourceAsStream("/plaza/mobs.json")) {
            if (in == null) throw new IllegalStateException("plaza/mobs.json 이 없어요");
            List<Spawn> list = new ArrayList<>();
            for (JsonNode n : new ObjectMapper().readTree(in)) {
                Spawn s = new Spawn(n.get("x").asInt(), n.get("y").asInt(), n.get("e").asText());
                kind(s.enemy());   // 명단에 없는 상대면 여기서 알린다
                list.add(s);
            }
            return List.copyOf(list);
        } catch (IOException e) {
            throw new UncheckedIOException("plaza/mobs.json 을 읽지 못했어요", e);
        }
    }

    /** 체력: 약함 3 · 보통 5 · 셈 7 (보스는 셈과 같다) */
    public static Kind kind(String enemy) {
        SparRoster.Enemy e = SparRoster.find(enemy).orElseThrow(() -> new IllegalStateException("스파링 명단에 없는 상대: " + enemy));
        int hp = switch (e.power()) {
            case WEAK -> 3;
            case NORMAL -> 5;
            case STRONG, BOSS -> 7;
        };
        return new Kind(hp, e.minLevel(), e.maxLevel());
    }

    /** 한 번 휘두른 피해: 레벨이 낮으면 1, 범위 안이면 2, 높으면 3, 최대 레벨보다 10 넘게 높으면 한 방(체력 전부) */
    public static int damage(int level, Kind k) {
        if (level >= k.maxLevel() + 10) return k.hp();
        if (level > k.maxLevel()) return 3;
        if (level >= k.minLevel()) return 2;
        return 1;
    }

    /** 사람(천분율 좌표)이 자리에서 세상 단위 5 안에 있는가 */
    public static boolean near(int mx, int my, Spawn s) {
        double dx = (mx - s.x()) * WORLD_W / 1000, dy = (my - s.y()) * WORLD_H / 1000;
        return Math.sqrt(dx * dx + dy * dy) <= NEAR;
    }
}
