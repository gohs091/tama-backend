package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.domain.plaza.PlazaMute;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.plaza.PlazaBlockRepository;
import com.tamagochi.tamagochi.repository.plaza.PlazaMuteRepository;
import com.tamagochi.tamagochi.repository.plaza.PlazaReportRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.user.NicknameRules;
import com.tamagochi.tamagochi.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 차단 · 신고 저장. 메모리 DB, Redis 는 흉내 */
@SpringBootTest
@ActiveProfiles("test")
class PlazaModerationServiceTest {

    @MockitoBean StringRedisTemplate redisTemplate;

    @Autowired PlazaModerationService moderation;
    @Autowired PlazaBlockRepository blockRepository;
    @Autowired PlazaReportRepository reportRepository;
    @Autowired PlazaMuteRepository muteRepository;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    private User me, other;

    @BeforeEach
    void setUp() {
        me = userService.createUser("toss_mod_a_" + System.nanoTime(), "나");
        other = userService.createUser("toss_mod_b_" + System.nanoTime(), "상대");
        for (User u : List.of(me, other)) { u.assignUserCode(NicknameRules.randomUserCode()); userRepository.save(u); }
    }

    @Test
    void 차단을_저장하고_푼다() {
        moderation.saveBlock(me.getId(), other.getUserCode(), true);
        assertThat(moderation.blockedCodes(me.getId())).containsExactly(other.getUserCode());
        moderation.saveBlock(me.getId(), other.getUserCode(), false);
        assertThat(moderation.blockedCodes(me.getId())).isEmpty();
    }

    @Test
    void 같은_차단을_두_번_해도_하나다() {
        moderation.saveBlock(me.getId(), other.getUserCode(), true);
        moderation.saveBlock(me.getId(), other.getUserCode(), true);
        assertThat(blockRepository.findByUserId(me.getId())).hasSize(1);
    }

    @Test
    void 신고를_저장한다() {
        moderation.saveReport(me.getId(), other.getUserCode(), "chat", List.of("하나", "둘"), "ROOM12");
        var rows = reportRepository.findAll().stream().filter(r -> r.getReporterId().equals(me.getId())).toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTargetId()).isEqualTo(other.getId());
        assertThat(rows.get(0).getWhy()).isEqualTo("chat");
        assertThat(rows.get(0).getLines()).isEqualTo("하나\n둘");
        assertThat(rows.get(0).getRoomKey()).isEqualTo("ROOM12");
    }

    @Test
    void 모르는_유저_코드는_조용히_무시한다() {
        moderation.saveBlock(me.getId(), "NOPE0000", true);
        moderation.saveReport(me.getId(), "NOPE0000", "chat", List.of(), "ROOM12");
        assertThat(blockRepository.findByUserId(me.getId())).isEmpty();
        assertThat(reportRepository.findAll().stream().filter(r -> r.getReporterId().equals(me.getId()))).isEmpty();
    }

    @Test
    void 큐가_차도_차단과_신고는_예외를_내지_않고_버린다() throws Exception {
        var gate = new java.util.concurrent.CountDownLatch(1);
        UserRepository slowUsers = org.mockito.Mockito.mock(UserRepository.class);
        org.mockito.Mockito.when(slowUsers.findByUserCode(org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return java.util.Optional.empty();
        });
        var small = new PlazaModerationService(new PlazaService(() -> 0L), slowUsers, blockRepository, reportRepository, muteRepository,
                PlazaHuntRunner.boundedExecutor("plaza-moderation-test", 1));
        small.block(1, "AAAA0001", true);   // 실행 중
        small.block(1, "AAAA0002", true);   // 큐 한 칸
        small.block(1, "AAAA0003", true);   // 넘침: 버린다
        small.report(1, "AAAA0004", "chat", List.of(), "ROOM12");   // 넘침: 버린다
        gate.countDown();
        small.stop();
    }

    @Test
    void 말_못_하게_된_때를_읽는다() {
        assertThat(moderation.mutedUntil(me.getId())).isZero();
        Instant until = Instant.now().plusSeconds(3600);
        muteRepository.save(new PlazaMute(me.getId(), until));
        assertThat(moderation.mutedUntil(me.getId())).isEqualTo(until.toEpochMilli());
    }
}
