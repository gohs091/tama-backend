package com.tamagochi.tamagochi.service.character;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FusionModeTest {

    private Map<FusionMode, Integer> distribution(boolean animalLike) {
        Map<FusionMode, Integer> counts = new EnumMap<>(FusionMode.class);
        for (long seed = 0; seed < 10_000; seed++) counts.merge(FusionMode.pick(seed, animalLike), 1, Integer::sum);
        return counts;
    }

    @Test
    @DisplayName("같은 시드는 항상 같은 합체 방식 (스케치/완성작까지 방식이 이어지는 기반)")
    void sameSeedSameMode() {
        for (long seed : new long[]{0, 1, 42, 123456789L, -7L}) {
            assertThat(FusionMode.pick(seed, false)).isEqualTo(FusionMode.pick(seed, false));
            assertThat(FusionMode.pick(seed, true)).isEqualTo(FusionMode.pick(seed, true));
        }
    }

    @Test
    @DisplayName("물체: 그림 자체 40% / 쥐고 있음 20% / 몸의 일부 20% / 탈것 20% 안팎")
    void objectDistribution() {
        Map<FusionMode, Integer> d = distribution(false);
        assertThat(d.get(FusionMode.SELF)).isBetween(3500, 4500);
        assertThat(d.get(FusionMode.HELD)).isBetween(1500, 2500);
        assertThat(d.get(FusionMode.FEATURE)).isBetween(1500, 2500);
        assertThat(d.get(FusionMode.MOUNT)).isBetween(1500, 2500);
    }

    @Test
    @DisplayName("동물/생물/사람: '쥐고 있음'은 나오지 않는다")
    void animalNeverHeld() {
        Map<FusionMode, Integer> d = distribution(true);
        assertThat(d).doesNotContainKey(FusionMode.HELD);
        assertThat(d.get(FusionMode.SELF)).isBetween(3500, 4500);
        assertThat(d.get(FusionMode.FEATURE)).isBetween(2500, 3500);
        assertThat(d.get(FusionMode.MOUNT)).isBetween(2500, 3500);
    }

    @Test
    @DisplayName("시드가 없으면 무작위 (여러 방식이 나온다)")
    void nullSeedIsRandom() {
        Map<FusionMode, Integer> counts = new EnumMap<>(FusionMode.class);
        for (int i = 0; i < 2000; i++) counts.merge(FusionMode.pick(null, false), 1, Integer::sum);
        assertThat(counts.keySet()).hasSize(4);
    }
}
