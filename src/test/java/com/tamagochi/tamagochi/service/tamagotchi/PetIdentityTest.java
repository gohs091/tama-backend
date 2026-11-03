package com.tamagochi.tamagochi.service.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.PetStatus;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.service.battle.MeetBattleServiceNameAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PetIdentityTest {

    private static Tamagotchi pet() {
        return Tamagotchi.builder().name("콩이").evolutionStage(EvolutionStage.CHILD).hunger(50).strength(50).build();
    }

    @Test
    @DisplayName("새 끄적이는 무작위 공개 ID(32자)와 ACTIVE 상태로 시작한다")
    void newPet() {
        Tamagotchi a = pet(), b = pet();
        assertThat(a.getPublicId()).hasSize(32).isNotEqualTo(b.getPublicId());
        assertThat(a.getStatus()).isEqualTo(PetStatus.ACTIVE);
    }

    @Test
    @DisplayName("새로 시작하면 새 끄적이: 공개 ID와 이름이 바뀐다 (예전 그림 경로와 섞이지 않게)")
    void resetGivesNewIdentity() {
        Tamagotchi t = pet();
        String before = t.getPublicId();

        t.reset();

        assertThat(t.getPublicId()).hasSize(32).isNotEqualTo(before);
        assertThat(t.getName()).isEqualTo(Tamagotchi.DEFAULT_NAME);
        assertThat(t.getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
    }

    @Test
    @DisplayName("공개 ID가 없는 예전 행만 채운다")
    void backfill() {
        Tamagotchi t = pet();
        assertThat(t.assignPublicIdIfMissing()).isFalse();
    }

    @Test
    @DisplayName("이름: 앞뒤 공백 정리, 10글자까지, 꺾쇠·빈 이름은 막는다")
    void nameRules() {
        assertThat(PetNameRules.clean("  콩이  2호 ")).isEqualTo("콩이 2호");
        assertThatThrownBy(() -> PetNameRules.clean(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PetNameRules.clean("가나다라마바사아자차카")).hasMessageContaining("10글자");
        assertThatThrownBy(() -> PetNameRules.clean("<script>")).hasMessageContaining("쓸 수 없는 글자");
    }

    @Test
    @DisplayName("배틀 이름표: 규칙에 맞지 않는 이름은 빈 값으로 바꿔 보낸다")
    void battleName() {
        assertThat(MeetBattleServiceNameAccess.battleName("콩이")).isEqualTo("콩이");
        assertThat(MeetBattleServiceNameAccess.battleName("<b>콩이</b>")).isEmpty();
        assertThat(MeetBattleServiceNameAccess.battleName("")).isEmpty();
    }
}
