package com.tamagochi.tamagochi.domain.battle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.spar.SparRoster;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BattleSkillTest {

    @Test
    @DisplayName("스파링 상대 412명 모두 앱에 있는 기술 틀을 갖고, 한 틀로 몰리지 않는다")
    void everyEnemyHasKnownTemplate() {
        List<SparRoster.Enemy> all = SparRoster.all();
        for (SparRoster.Enemy e : all) assertThat(BattleSkill.TEMPLATES).as(e.id()).contains(e.skill());
        Map<String, Long> count = all.stream().collect(Collectors.groupingBy(SparRoster.Enemy::skill, Collectors.counting()));
        assertThat(count).hasSize(18);
        assertThat(count.values()).allMatch(n -> n < all.size() / 4);
        assertThat(SparRoster.find("ns-hamster").orElseThrow().skill()).isEqualTo("barrage");
    }

    @Test
    @DisplayName("AI 답을 다듬는다: 모르는 틀·입자는 기본값, 잘못된 색은 기본 색, 이름은 20자까지")
    void sanitize() {
        BattleSkill ok = BattleSkill.sanitize(" Vortex ", "황금 깃털 회오리", List.of("#FFCF4A", "#b0702a", "#fff6d6"), "feather");
        assertThat(ok).isEqualTo(new BattleSkill("vortex", "황금 깃털 회오리", List.of("#FFCF4A", "#b0702a", "#fff6d6"), "feather"));

        BattleSkill bad = BattleSkill.sanitize("laser", "", List.of("red", "#12"), "bubble");
        assertThat(bad.template()).isEqualTo("projectile");
        assertThat(bad.particle()).isEqualTo("circle");
        assertThat(bad.colors()).hasSize(3).allMatch(c -> c.matches("^#[0-9a-fA-F]{6}$"));
        assertThat(bad.name()).isNotBlank();
        assertThat(BattleSkill.sanitize("beam", "가".repeat(30), null, null).name()).hasSize(20);
    }

    @Test
    @DisplayName("AI JSON 답을 읽는다")
    void parseAiJson() throws Exception {
        var node = new ObjectMapper().readTree("{\"template\":\"chomp\",\"name\":\"이빨 상어 콱\",\"colors\":[\"#8fa6bb\",\"#ffffff\",\"#dff0ff\"],\"particle\":\"drop\"}");
        BattleSkill s = com.tamagochi.tamagochi.service.battle.BattleSkillServiceTestAccess.parse(node);
        assertThat(s.template()).isEqualTo("chomp");
        assertThat(s.colors()).containsExactly("#8fa6bb", "#ffffff", "#dff0ff");
    }

    @Test
    @DisplayName("기술은 정한 단계에서만 유효하다: 진화하면 다시 정한다")
    void skillBelongsToStage() {
        Tamagotchi t = Tamagotchi.builder().name("t").evolutionStage(EvolutionStage.BABY).build();
        assertThat(t.hasSkillForCurrentStage()).isFalse();
        t.applySkill("dash", "돌진", "#111111,#222222,#333333", "smoke");
        assertThat(t.hasSkillForCurrentStage()).isTrue();
        Tamagotchi child = Tamagotchi.builder().name("t").evolutionStage(EvolutionStage.CHILD)
                .skillTemplate("dash").skillName("돌진").skillColors("#111111,#222222,#333333").skillParticle("smoke").skillStage("BABY").build();
        assertThat(child.hasSkillForCurrentStage()).isFalse();
    }
}
