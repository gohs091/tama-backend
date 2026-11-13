package com.tamagochi.tamagochi.service.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.config.LocalDevRedisConfig;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.toss.TossPartnerApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 알림을 보낼지 정하는 흐름: 한 번만, 쉬는 시간, 보고 있을 때, 동의, 밤 */
class PushServiceTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 13, 12, 0);

    private StringRedisTemplate redis;
    private TossPartnerApiClient toss;
    private PushService push;

    @BeforeEach
    void setUp() throws Exception {
        redis = new LocalDevRedisConfig().stringRedisTemplate();
        toss = mock(TossPartnerApiClient.class);
        when(toss.post(any(), any(), anyMap())).thenReturn(new ObjectMapper().readTree("{\"resultType\":\"SUCCESS\"}"));
        push = new PushService(mock(TamagotchiRepository.class), redis, toss);
        ReflectionTestUtils.setField(push, "codePrefix", "doodle-app-");
        ReflectionTestUtils.setField(push, "inlineParticle", java.util.Set.of());
    }

    private static User user(boolean agreed) {
        User u = User.builder().id(1L).tossUserId("toss_ANON").nickname("닉").build();
        u.changePushAgreed(agreed);
        return u;
    }

    /** satiation: 앱에 보이는 포만감 (서버 hunger는 반대 방향) */
    private static Tamagotchi pet(User u, int satiation) {
        return Tamagotchi.builder().id(7L).name("냥냥이").user(u).evolutionStage(EvolutionStage.BABY).hunger(100 - satiation).strength(80).build();
    }

    @Test
    @DisplayName("배고프면 한 번 보내고, 밥을 먹어 풀리기 전에는 다시 보내지 않는다. 풀린 뒤 다시 배고파지면 또 보낸다")
    void hungryOncePerEpisode() {
        User u = user(true);
        Tamagotchi p = pet(u, 15);

        assertThat(push.consider(p, NOON)).isTrue();
        verify(toss).post(eq(PushService.SEND_PATH), eq(Map.of("templateSetCode", "doodle-app-hungry", "context", Map.of("petName", "냥냥이가"))), eq(Map.of("x-anon-key", "ANON")));

        redis.delete("push:cool:7");                       // 쉬는 시간이 지나도
        assertThat(push.consider(p, NOON.plusHours(4))).isFalse();   // 같은 배고픔으로는 다시 안 보낸다

        Tamagotchi fed = pet(u, 80);
        assertThat(push.consider(fed, NOON.plusHours(5))).isFalse(); // 먹어서 풀렸다 (표시를 지운다)
        assertThat(push.consider(pet(u, 10), NOON.plusHours(6))).isTrue();
        verify(toss, times(2)).post(any(), any(), anyMap());
    }

    @Test
    @DisplayName("알림끼리는 3시간을 띄운다")
    void cooldown() {
        User u = user(true);
        Tamagotchi hungry = pet(u, 15);
        assertThat(push.consider(hungry, NOON)).isTrue();
        Tamagotchi sick = Tamagotchi.builder().id(7L).name("냥냥이").user(u).evolutionStage(EvolutionStage.BABY).hunger(85).strength(10).build();
        assertThat(push.consider(sick, NOON.plusMinutes(10))).isFalse();
    }

    @Test
    @DisplayName("앱을 막 본 사람, 동의 안 한 사람, 밤에는 보내지 않는다")
    void skipWhenSeenOrNotAgreedOrNight() {
        push.seen(1L);
        assertThat(push.consider(pet(user(true), 15), NOON)).isFalse();
        redis.delete("push:seen:1");

        assertThat(push.consider(pet(user(false), 15), NOON)).isFalse();
        assertThat(push.consider(pet(user(true), 15), NOON.withHour(23))).isFalse();
        verify(toss, never()).post(any(), any(), anyMap());
    }

    @Test
    @DisplayName("받침 있는 이름은 \"이\"를 붙이고, 아픔은 \"의\"를 붙인다")
    void particles() {
        User u = user(true);
        Tamagotchi sick = Tamagotchi.builder().id(7L).name("용").user(u).evolutionStage(EvolutionStage.BABY).hunger(20).strength(10).build();
        assertThat(push.consider(sick, NOON)).isTrue();
        verify(toss).post(eq(PushService.SEND_PATH), eq(Map.of("templateSetCode", "doodle-app-sick", "context", Map.of("petName", "용의"))), anyMap());
    }

    @Test
    @DisplayName("템플릿에 조사가 적힌 알림은 부르는 이름만 보낸다 (받침이면 \"이\"를 붙여)")
    void inlineParticle() {
        ReflectionTestUtils.setField(push, "inlineParticle", java.util.Set.of("hungry"));
        User u = user(true);
        assertThat(push.consider(Tamagotchi.builder().id(7L).name("용").user(u).evolutionStage(EvolutionStage.BABY).hunger(85).strength(80).build(), NOON)).isTrue();
        verify(toss).post(eq(PushService.SEND_PATH), eq(Map.of("templateSetCode", "doodle-app-hungry", "context", Map.of("petName", "용이"))), anyMap());
        assertThat(PushRules.calling("냥냥이")).isEqualTo("냥냥이");
        assertThat(PushRules.calling("Rex")).isEqualTo("Rex");
    }
}
