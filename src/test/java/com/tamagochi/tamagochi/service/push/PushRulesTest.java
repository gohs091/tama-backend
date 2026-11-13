package com.tamagochi.tamagochi.service.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PushRulesTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 13, 12, 0);

    private static PushRules.Pet pet(int satiation, int strength, int poop, int level, int evoLevel, boolean zone, boolean sleepy, LocalDateTime trained) {
        return new PushRules.Pet(true, false, false, false, satiation, strength, poop, level, evoLevel, zone, sleepy, trained);
    }

    private static PushRules.Pet fine() {
        return pet(80, 80, 0, 3, 6, false, false, NOON.minusHours(1));
    }

    @Test
    @DisplayName("멀쩡하면 아무 알림도 없다")
    void nothing() {
        assertThat(PushRules.applicable(fine(), NOON)).isEmpty();
    }

    @Test
    @DisplayName("각 상태가 해당하는 알림을 고르고, 응가 → 배고픔 → 아픔 → 진화 → 졸림 → 심심 순서로 놓는다")
    void eachKindAndOrder() {
        assertThat(PushRules.applicable(pet(20, 80, 0, 3, 6, false, false, NOON), NOON)).containsExactly(PushKind.HUNGRY);
        assertThat(PushRules.applicable(pet(21, 80, 0, 3, 6, false, false, NOON), NOON)).isEmpty();
        assertThat(PushRules.applicable(pet(80, 20, 0, 3, 6, false, false, NOON), NOON)).containsExactly(PushKind.SICK);
        assertThat(PushRules.applicable(pet(80, 80, 9, 3, 6, false, false, NOON), NOON)).containsExactly(PushKind.POOP);
        assertThat(PushRules.applicable(pet(80, 80, 8, 3, 6, false, false, NOON), NOON)).isEmpty();
        assertThat(PushRules.applicable(pet(80, 80, 0, 5, 6, false, false, NOON), NOON)).containsExactly(PushKind.EVO_SOON);
        assertThat(PushRules.applicable(pet(80, 80, 0, 6, 6, true, false, NOON), NOON)).containsExactly(PushKind.EVO_READY);
        assertThat(PushRules.applicable(pet(80, 80, 0, 3, 6, false, true, NOON), NOON)).containsExactly(PushKind.SLEEPY);
        assertThat(PushRules.applicable(pet(80, 80, 0, 3, 6, false, false, NOON.minusHours(24)), NOON)).containsExactly(PushKind.BORED);
        assertThat(PushRules.applicable(pet(80, 80, 0, 3, 6, false, false, NOON.minusHours(23)), NOON)).isEmpty();
        assertThat(PushRules.applicable(pet(10, 10, 10, 6, 6, true, true, NOON.minusDays(2)), NOON))
                .containsExactly(PushKind.POOP, PushKind.HUNGRY, PushKind.SICK, PushKind.EVO_READY, PushKind.SLEEPY, PushKind.BORED);
    }

    @Test
    @DisplayName("완성작(진화 없음)은 진화 알림이 없다")
    void adultHasNoEvolution() {
        assertThat(PushRules.applicable(pet(80, 80, 0, 40, 0, false, false, NOON), NOON)).isEmpty();
    }

    @Test
    @DisplayName("자는 중·모험 중·알·떠난 끄적이는 보내지 않는다")
    void skipStates() {
        assertThat(PushRules.applicable(new PushRules.Pet(true, false, true, false, 0, 0, 12, 3, 6, false, false, NOON.minusDays(3)), NOON)).isEmpty();
        assertThat(PushRules.applicable(new PushRules.Pet(true, false, false, true, 0, 0, 12, 3, 6, false, false, NOON.minusDays(3)), NOON)).isEmpty();
        assertThat(PushRules.applicable(new PushRules.Pet(true, true, false, false, 0, 0, 12, 3, 6, false, false, NOON.minusDays(3)), NOON)).isEmpty();
        assertThat(PushRules.applicable(new PushRules.Pet(false, false, false, false, 0, 0, 12, 3, 6, false, false, NOON.minusDays(3)), NOON)).isEmpty();
    }

    @Test
    @DisplayName("밤(22~8시)에는 안 보내고, 졸림은 저녁 20~23시에만 보낸다")
    void hours() {
        assertThat(PushRules.allowedAt(PushKind.HUNGRY, NOON.withHour(7))).isFalse();
        assertThat(PushRules.allowedAt(PushKind.HUNGRY, NOON.withHour(8))).isTrue();
        assertThat(PushRules.allowedAt(PushKind.HUNGRY, NOON.withHour(21))).isTrue();
        assertThat(PushRules.allowedAt(PushKind.HUNGRY, NOON.withHour(22))).isFalse();
        assertThat(PushRules.allowedAt(PushKind.SLEEPY, NOON.withHour(19))).isFalse();
        assertThat(PushRules.allowedAt(PushKind.SLEEPY, NOON.withHour(22))).isTrue();
        assertThat(PushRules.allowedAt(PushKind.SLEEPY, NOON.withHour(23))).isFalse();
    }

    @Test
    @DisplayName("이름 뒤 조사: 받침 있으면 이, 없으면 가, 소유는 의")
    void particle() {
        assertThat(PushRules.withParticle("냥냥이", PushKind.Particle.SUBJECT)).isEqualTo("냥냥이가");
        assertThat(PushRules.withParticle("용", PushKind.Particle.SUBJECT)).isEqualTo("용이");
        assertThat(PushRules.withParticle("볼티라노", PushKind.Particle.SUBJECT)).isEqualTo("볼티라노가");
        assertThat(PushRules.withParticle("Rex", PushKind.Particle.SUBJECT)).isEqualTo("Rex가");
        assertThat(PushRules.withParticle("용", PushKind.Particle.POSSESSIVE)).isEqualTo("용의");
        assertThat(PushRules.withParticle(" ", PushKind.Particle.SUBJECT)).isEqualTo("끄적이가");
    }

    @Test
    @DisplayName("익명 키는 토스 로그인 ID에서만 꺼낸다")
    void anonKey() {
        assertThat(PushService.anonKey("toss_abc123")).isEqualTo("abc123");
        assertThat(PushService.anonKey("local_x")).isNull();
        assertThat(PushService.anonKey("toss_")).isNull();
        assertThat(PushService.anonKey(null)).isNull();
    }
}
