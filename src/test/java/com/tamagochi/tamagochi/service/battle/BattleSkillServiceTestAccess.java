package com.tamagochi.tamagochi.service.battle;

import com.fasterxml.jackson.databind.JsonNode;
import com.tamagochi.tamagochi.domain.battle.BattleSkill;

/** 테스트에서 패키지 전용 parse를 부르기 위한 통로 */
public final class BattleSkillServiceTestAccess {
    private BattleSkillServiceTestAccess() {
    }

    public static BattleSkill parse(JsonNode n) {
        return BattleSkillService.parse(n);
    }
}
