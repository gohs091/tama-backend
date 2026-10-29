package com.tamagochi.tamagochi.service.promotion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrant;
import com.tamagochi.tamagochi.domain.promotion.PromotionGrantStatus;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.promotion.PromotionGrantRepository;
import com.tamagochi.tamagochi.service.toss.TossPromotionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionGranterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CODE = "TEST_CODE";
    private static final String ANON = "anon-abc";

    private PromotionGrantRepository repository;
    private TossPromotionApi api;
    private PromotionGranter granter;
    private PromotionGrant grant;

    private static TossPromotionApi.Response ok(String successJson) throws Exception {
        return TossPromotionApi.Response.parse(MAPPER.readTree("{\"resultType\":\"SUCCESS\",\"success\":" + successJson + "}"));
    }

    private static TossPromotionApi.Response fail(String code) throws Exception {
        return TossPromotionApi.Response.parse(MAPPER.readTree(
                "{\"resultType\":\"FAIL\",\"success\":null,\"error\":{\"errorCode\":\"" + code + "\",\"reason\":\"r\"}}"));
    }

    private static PromotionGrant newGrant(String tossUserId) {
        User user = User.builder().id(1L).tossUserId(tossUserId).nickname("n").build();
        return new PromotionGrant(user, "s1", LocalDate.of(2026, 10, 26), 85, 2, 2);
    }

    @BeforeEach
    void setUp() {
        repository = mock(PromotionGrantRepository.class);
        api = mock(TossPromotionApi.class);
        granter = new PromotionGranter(repository, api, Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneId.of("Asia/Seoul")), "grant", CODE, "off", "");
        grant = newGrant("toss_" + ANON);
        when(repository.claim(eq(1L), eq(PromotionGrantStatus.RECORDED), eq(PromotionGrantStatus.SENDING), any())).thenReturn(1);
        when(repository.findWithUserById(1L)).thenReturn(Optional.of(grant));
    }

    @Test
    @DisplayName("키 발급 → 지급 → 결과 SUCCESS면 지급 완료로 바뀌고, 사용자 키와 프로모션 코드, 금액을 맞게 보낸다")
    void grantsSuccessfully() throws Exception {
        when(api.getKey()).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.execute(ANON, CODE, "K1", 2)).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.result(ANON, CODE, "K1")).thenReturn(ok("\"SUCCESS\""));

        granter.grant(1L);

        assertThat(grant.getStatus()).isEqualTo(PromotionGrantStatus.GRANTED);
        assertThat(grant.getGrantKey()).isEqualTo("K1");
        verify(api).execute(ANON, CODE, "K1", 2);
    }

    @Test
    @DisplayName("진화 달성 보상은 진화용 프로모션 코드로 지급한다")
    void evolutionUsesEvolutionCode() throws Exception {
        PromotionGranter both = new PromotionGranter(repository, api,
                Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneId.of("Asia/Seoul")), "grant", CODE, "grant", "TEST_EVO");
        User user = User.builder().id(1L).tossUserId("toss_" + ANON).nickname("n").build();
        PromotionGrant evo = PromotionGrant.evolution(user, "CHILD", LocalDate.of(2026, 10, 26), 1, 10);
        when(repository.findWithUserById(1L)).thenReturn(Optional.of(evo));
        when(api.getKey()).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.execute(ANON, "TEST_EVO", "K1", 10)).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.result(ANON, "TEST_EVO", "K1")).thenReturn(ok("\"SUCCESS\""));

        both.grant(1L);

        assertThat(evo.getStatus()).isEqualTo(PromotionGrantStatus.GRANTED);
        verify(api, never()).execute(anyString(), eq(CODE), anyString(), anyInt());
    }

    @Test
    @DisplayName("진화 보상 지급이 꺼져 있으면 토스를 부르지 않고 대기로 돌려 둔다")
    void evolutionOffWaits() {
        User user = User.builder().id(1L).tossUserId("toss_" + ANON).nickname("n").build();
        PromotionGrant evo = PromotionGrant.evolution(user, "ADULT", LocalDate.of(2026, 10, 26), 1, 50);
        when(repository.findWithUserById(1L)).thenReturn(Optional.of(evo));

        granter.grant(1L); // 트레이닝만 켜져 있는 granter

        verify(api, never()).getKey();
        verify(repository).claim(eq(1L), eq(PromotionGrantStatus.SENDING), eq(PromotionGrantStatus.RECORDED), any());
    }

    @Test
    @DisplayName("진화 TEST_ 코드는 mode가 꺼져 있어도 관리자 테스트 지급은 보내고, 실제 진화 보상은 보내지 않는다")
    void evolutionTestCodeSendsOnlyAdminTests() throws Exception {
        PromotionGranter evoTest = new PromotionGranter(repository, api,
                Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneId.of("Asia/Seoul")), "off", "", "off", "TEST_EVO");
        User user = User.builder().id(1L).tossUserId("toss_" + ANON).nickname("n").build();
        assertThat(evoTest.isEvolutionConfigured()).isTrue();
        assertThat(evoTest.isEvolutionTestCode()).isTrue();

        PromotionGrant real = PromotionGrant.evolution(user, "CHILD", LocalDate.of(2026, 10, 26), 1, 10);
        when(repository.findWithUserById(1L)).thenReturn(Optional.of(real));
        evoTest.grant(1L);
        verify(api, never()).getKey(); // 실제 진화 보상은 mode=grant 가 아니면 보내지 않는다

        PromotionGrant test = PromotionGrant.evolutionTest(user, LocalDate.of(1970, 1, 1), 1);
        when(repository.findWithUserById(1L)).thenReturn(Optional.of(test));
        when(api.getKey()).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.execute(ANON, "TEST_EVO", "K1", 1)).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.result(ANON, "TEST_EVO", "K1")).thenReturn(ok("\"SUCCESS\""));
        evoTest.grant(1L);
        assertThat(test.getStatus()).isEqualTo(PromotionGrantStatus.GRANTED);
    }

    @Test
    @DisplayName("결과가 PENDING이면 접수 중으로 두고 나중에 다시 조회한다")
    void pending() throws Exception {
        when(api.getKey()).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.execute(anyString(), anyString(), anyString(), anyInt())).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.result(ANON, CODE, "K1")).thenReturn(ok("\"PENDING\""));

        granter.grant(1L);

        assertThat(grant.getStatus()).isEqualTo(PromotionGrantStatus.PENDING);
    }

    @Test
    @DisplayName("이미 지급된 키(4113)로 다시 요청하면 중복 지급 없이 결과로 확정한다")
    void alreadyGranted() throws Exception {
        grant.saveGrantKey("K1"); // 앞선 시도에서 키를 받아 둔 상태
        when(api.execute(ANON, CODE, "K1", 2)).thenReturn(fail("4113"));
        when(api.result(ANON, CODE, "K1")).thenReturn(ok("\"SUCCESS\""));

        granter.grant(1L);

        assertThat(grant.getStatus()).isEqualTo(PromotionGrantStatus.GRANTED);
        verify(api, never()).getKey(); // 받아 둔 키를 그대로 쓴다
    }

    @Test
    @DisplayName("예산 부족·프로모션 종료 같은 영구 오류는 재시도하지 않고 실패로 둔다")
    void permanentError() throws Exception {
        when(api.getKey()).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.execute(anyString(), anyString(), anyString(), anyInt())).thenReturn(fail("4112"));

        granter.grant(1L);

        assertThat(grant.getStatus()).isEqualTo(PromotionGrantStatus.FAILED);
        assertThat(grant.getLastError()).contains("4112");
    }

    @Test
    @DisplayName("요청 한도 초과(4095) 같은 일시 오류는 대기로 되돌려 다시 시도한다")
    void transientError() throws Exception {
        when(api.getKey()).thenReturn(fail("4095"));

        granter.grant(1L);

        assertThat(grant.getStatus()).isEqualTo(PromotionGrantStatus.RECORDED);
        assertThat(grant.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("호출 중 예외가 나도 대기로 되돌린다")
    void exceptionRetries() throws Exception {
        when(api.getKey()).thenThrow(new IllegalStateException("토스 서버 호출에 실패했습니다."));

        granter.grant(1L);

        assertThat(grant.getStatus()).isEqualTo(PromotionGrantStatus.RECORDED);
        assertThat(grant.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 횟수를 다 쓰면 실패로 둔다")
    void attemptsExhausted() throws Exception {
        when(api.getKey()).thenReturn(fail("4095"));
        // 한도에서 한 번 모자란 상태(MAX_ATTEMPTS - 1회 실패)를 만들고, 한 번 더 실패시킨다
        PromotionGrant almost = newGrant("toss_" + ANON);
        for (int i = 0; i < PromotionGrant.MAX_ATTEMPTS - 1; i++) almost.retryLater("x");
        assertThat(almost.getStatus()).isEqualTo(PromotionGrantStatus.RECORDED);
        when(repository.findWithUserById(1L)).thenReturn(Optional.of(almost));

        granter.grant(1L);

        assertThat(almost.getStatus()).isEqualTo(PromotionGrantStatus.FAILED);
    }

    @Test
    @DisplayName("토스 결과가 FAILED면 키를 버리고 새 키로 다시 시도하도록 되돌린다")
    void tossFailedResultRetriesWithNewKey() throws Exception {
        when(api.getKey()).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.execute(anyString(), anyString(), anyString(), anyInt())).thenReturn(ok("{\"key\":\"K1\"}"));
        when(api.result(ANON, CODE, "K1")).thenReturn(ok("\"FAILED\""));

        granter.grant(1L);

        assertThat(grant.getStatus()).isEqualTo(PromotionGrantStatus.RECORDED);
        assertThat(grant.getGrantKey()).isNull();
    }

    @Test
    @DisplayName("토스 사용자 ID가 아니면(로컬 ID 등) 지급하지 않고 실패로 둔다")
    void notTossUser() {
        PromotionGrant local = newGrant("local_123");
        when(repository.findWithUserById(1L)).thenReturn(Optional.of(local));

        granter.grant(1L);

        assertThat(local.getStatus()).isEqualTo(PromotionGrantStatus.FAILED);
        verify(api, never()).getKey();
    }

    @Test
    @DisplayName("다른 곳이 이미 처리 중이면(선점 실패) 아무것도 하지 않는다")
    void alreadyClaimed() {
        when(repository.claim(eq(1L), any(), any(), any())).thenReturn(0);

        granter.grant(1L);

        verify(api, never()).getKey();
        verify(repository, never()).findWithUserById(any());
    }

    @Test
    @DisplayName("프로모션 코드가 없으면 지급을 시도하지 않는다")
    void noCode() {
        PromotionGranter noCode = new PromotionGranter(repository, api, Clock.systemUTC(), "grant", " ", "off", "");
        noCode.grant(1L);
        noCode.sweep();
        assertThat(noCode.isConfigured()).isFalse();
        verify(repository, never()).claim(any(), any(), any(), any());
    }

    @Test
    @DisplayName("점검: 접수 중인 건은 결과를 다시 조회해 확정하고, 대기 중인 건은 다시 지급한다")
    void sweepProcessesPendingAndRecorded() throws Exception {
        PromotionGrant pending = newGrant("toss_" + ANON);
        pending.saveGrantKey("K9");
        pending.markPending();
        when(repository.findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(eq(PromotionGrantStatus.SENDING), any())).thenReturn(List.of());
        when(repository.findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(eq(PromotionGrantStatus.RECORDED), any())).thenReturn(List.of());
        when(repository.findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(eq(PromotionGrantStatus.PENDING), any())).thenReturn(List.of(pending));
        when(api.result(ANON, CODE, "K9")).thenReturn(ok("\"SUCCESS\""));

        granter.sweep();

        assertThat(pending.getStatus()).isEqualTo(PromotionGrantStatus.GRANTED);
        verify(api, times(1)).result(ANON, CODE, "K9");
    }
}
