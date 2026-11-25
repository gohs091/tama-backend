package com.tamagochi.tamagochi.service.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.config.LocalDevRedisConfig;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.tamagotchi.VitalsRule;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.toss.TossPartnerApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
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
    /** 운영 값 (application.yml tamagotchi.vitals) */
    private static final VitalsRule RULE = new VitalsRule(8, 16, Duration.ofHours(6), 60, 13, Duration.ofMinutes(5));

    private StringRedisTemplate redis;
    private TossPartnerApiClient toss;
    private PushService push;

    @BeforeEach
    void setUp() throws Exception {
        redis = new LocalDevRedisConfig().stringRedisTemplate();
        toss = mock(TossPartnerApiClient.class);
        when(toss.post(any(), any(), anyMap())).thenReturn(new ObjectMapper().readTree("{\"resultType\":\"SUCCESS\"}"));
        push = new PushService(mock(TamagotchiRepository.class), redis, toss, RULE);
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

    @Test
    @DisplayName("곧 떠나요: 굶은 지 5시간(떠나기 1시간 전)이면 쉬는 시간·하루 횟수와 상관없이 보낸다")
    void dyingIgnoresCooldownAndDailyMax() {
        User u = user(true);
        redis.opsForValue().set("push:cool:7", "1");
        redis.opsForValue().set("push:day:7:" + NOON.toLocalDate(), "2");
        Tamagotchi starving = Tamagotchi.builder().id(7L).name("냥냥이").user(u).evolutionStage(EvolutionStage.BABY)
                .hunger(100).strength(80).hungryStartedAt(NOON.minusHours(5)).build();
        assertThat(push.consider(starving, NOON)).isTrue();
        verify(toss).post(eq(PushService.SEND_PATH), eq(Map.of("templateSetCode", "doodle-app-dying", "context", Map.of("petName", "냥냥이가"))), anyMap());
        assertThat(push.consider(starving, NOON.plusMinutes(5))).isFalse();   // 한 번만
        assertThat(push.consider(pet(u, 15), NOON.plusMinutes(10))).isFalse(); // 배고픔은 쉬는 시간·하루 횟수에 걸린다
    }

    @Test
    @DisplayName("하루에 두 번까지만 보낸다")
    void dailyMaxTwo() {
        User u = user(true);
        assertThat(push.consider(pet(u, 15), NOON)).isTrue();
        redis.delete("push:cool:7");
        Tamagotchi sick = Tamagotchi.builder().id(7L).name("냥냥이").user(u).evolutionStage(EvolutionStage.BABY).hunger(85).strength(10).build();
        assertThat(push.consider(sick, NOON.plusHours(1))).isTrue();
        redis.delete("push:cool:7");
        Tamagotchi poopy = Tamagotchi.builder().id(7L).name("냥냥이").user(u).evolutionStage(EvolutionStage.BABY).hunger(85).strength(10).poopCount(6).build();
        assertThat(push.consider(poopy, NOON.plusHours(2))).isFalse();
    }

    @Test
    @DisplayName("못 보낸 알림은 보낸 것으로 치지 않는다: 쉬는 시간·하루 횟수를 남기지 않고 한 시간 뒤 다시 해 본다")
    void failureIsNotSent() throws Exception {
        when(toss.post(any(), any(), anyMap())).thenReturn(new ObjectMapper().readTree("{\"resultType\":\"FAIL\"}"));
        User u = user(true);
        assertThat(push.consider(pet(u, 15), NOON)).isFalse();
        assertThat(redis.opsForValue().get("push:cool:7")).isNull();
        assertThat(redis.opsForValue().get("push:day:7:" + NOON.toLocalDate())).isNull();
        assertThat(push.consider(pet(u, 15), NOON.plusMinutes(5))).isFalse();
        verify(toss, times(1)).post(any(), any(), anyMap());              // 쉬는 동안은 두드리지 않는다

        when(toss.post(any(), any(), anyMap())).thenReturn(new ObjectMapper().readTree("{\"resultType\":\"SUCCESS\"}"));
        redis.delete("push:fail:7:hungry");                                // 한 시간이 지났다
        assertThat(push.consider(pet(u, 15), NOON.plusHours(1))).isTrue();
    }

    @Test
    @DisplayName("새 알로 다시 시작한 끄적이는 같은 단계에서도 진화 알림을 다시 받는다")
    void evolutionAgainAfterRestart() {
        Tamagotchi first = Tamagotchi.builder().id(7L).publicId("aaa").evolutionStage(EvolutionStage.BABY).build();
        Tamagotchi reborn = Tamagotchi.builder().id(7L).publicId("bbb").evolutionStage(EvolutionStage.BABY).build();
        assertThat(PushService.episodeKey(first, PushKind.EVO_READY)).isNotEqualTo(PushService.episodeKey(reborn, PushKind.EVO_READY));
        assertThat(PushService.episodeKey(first, PushKind.HUNGRY)).isEqualTo(PushService.episodeKey(reborn, PushKind.HUNGRY)); // 상태 알림은 풀리면 지워진다
    }

    @Test
    @DisplayName("다시 만든 캠페인의 발송 코드를 알림별로 바꿔 쓴다")
    void codeOverride() {
        ReflectionTestUtils.setField(push, "codeOverrides", "hungry=doodle-app-hungry2, sick = doodle-app-sick-v2");
        assertThat(push.codeOf(PushKind.HUNGRY)).isEqualTo("doodle-app-hungry2");
        assertThat(push.codeOf(PushKind.SICK)).isEqualTo("doodle-app-sick-v2");
        assertThat(push.codeOf(PushKind.POOP)).isEqualTo("doodle-app-poop");
    }

    @Test
    @DisplayName("켜 둔 종류만 보낸다 (콘솔에서 활성화하지 않은 캠페인으로는 보내지 않는다)")
    void onlyEnabledKinds() {
        ReflectionTestUtils.setField(push, "enabledKinds", "hungry,sick");
        User u = user(true);
        Tamagotchi poopy = Tamagotchi.builder().id(7L).name("냥냥이").user(u).evolutionStage(EvolutionStage.BABY).hunger(20).strength(80).poopCount(10).build();
        assertThat(push.consider(poopy, NOON)).isFalse();      // 응가는 꺼져 있다
        Tamagotchi both = Tamagotchi.builder().id(7L).name("냥냥이").user(u).evolutionStage(EvolutionStage.BABY).hunger(85).strength(80).poopCount(10).build();
        assertThat(push.consider(both, NOON)).isTrue();         // 응가는 건너뛰고 배고픔을 보낸다
        verify(toss).post(eq(PushService.SEND_PATH), eq(Map.of("templateSetCode", "doodle-app-hungry", "context", Map.of("petName", "냥냥이가"))), anyMap());
    }
}
