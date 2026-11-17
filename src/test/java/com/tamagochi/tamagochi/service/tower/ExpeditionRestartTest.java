package com.tamagochi.tamagochi.service.tower;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.VitalsRule;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.tamagotchi.RedrawService;
import com.tamagochi.tamagochi.service.tamagotchi.TamagotchiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 탑 원정 중에는 새로 시작·다시 그리기를 할 수 없다 (포만감이 처음 값으로 돌아가 원정을 공짜로 이어 가는 것을 막는다) */
class ExpeditionRestartTest extends TowerLunchTestSupport {

    @Autowired TamagotchiService tamagotchiService;
    @Autowired RedrawService redrawService;

    @Test
    @DisplayName("원정 중 새로 시작은 거절되고 끄적이가 그대로다. 돌아오면 할 수 있다")
    void resetIsRejectedOnExpedition() {
        User user = newUser(0, 80);
        String publicId = pet(user).getPublicId();
        towerService.depart(user, null);

        assertCode(() -> tamagotchiService.reset(user), "ON_EXPEDITION");
        assertThat(pet(user).getPublicId()).isEqualTo(publicId);
        assertThat(pet(user).getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
        assertThat(pet(user).satiety()).isEqualTo(80);
        assertThat(towerService.get(user).onExpedition()).isTrue();

        towerService.returnHome(user);
        tamagotchiService.reset(user);
        assertThat(pet(user).getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        assertThat(pet(user).getPublicId()).isNotEqualTo(publicId);
    }

    @Test
    @DisplayName("원정 중 다시 그리기는 거절되고 횟수도 쓰지 않는다. 돌아오면 할 수 있다")
    void redrawIsRejectedOnExpedition() {
        User user = newUser(0, 80);
        towerService.depart(user, null);

        assertCode(() -> redrawService.redraw(user), "ON_EXPEDITION");
        assertThat(pet(user).getEvolutionStage()).isEqualTo(EvolutionStage.BABY);
        assertThat(pet(user).satiety()).isEqualTo(80);
        assertThat(redrawService.status(user).left()).isEqualTo(Tamagotchi.REDRAWS_PER_DAY);
        assertThat(towerService.get(user).onExpedition()).isTrue();

        towerService.returnHome(user);
        assertThat(redrawService.redraw(user).left()).isEqualTo(Tamagotchi.REDRAWS_PER_DAY - 1);
        assertThat(pet(user).getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
    }

    @Test
    @DisplayName("죽은 끄적이에게 원정 표시가 남아 있어도 새로 시작은 막히지 않고, 원정은 접힌다")
    void deadPetWithLeftoverExpeditionCanReset() {
        User user = newUser(0, 80);
        towerService.depart(user, null);
        Tamagotchi dead = pet(user);
        dead.applyTime(LocalDateTime.now(), new VitalsRule(8, 16, Duration.ofHours(6), 60, 0, Duration.ofMinutes(5))); // 응가 상한 0 → 바로 사망
        tamagotchiRepository.save(dead);
        assertThat(pet(user).isDead()).isTrue();
        assertThat(towerService.get(user).onExpedition()).isTrue();

        tamagotchiService.reset(user);

        assertThat(pet(user).isDead()).isFalse();
        assertThat(pet(user).getEvolutionStage()).isEqualTo(EvolutionStage.EGG);
        assertThat(towerService.get(user).onExpedition()).isFalse();
    }
}
