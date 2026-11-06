package com.tamagochi.tamagochi.service.battle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetBattleServiceTest {

    static final class FakePeer implements MeetBattleService.Peer {
        final String id;
        final List<JsonNode> got = new ArrayList<>();
        boolean closed;
        FakePeer(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public void send(String json) {
            try { got.add(new ObjectMapper().readTree(json)); } catch (Exception e) { throw new RuntimeException(e); }
        }
        @Override public void close() { closed = true; }
        JsonNode last(String type) {
            for (int i = got.size() - 1; i >= 0; i--) if (got.get(i).path("t").asText().equals(type)) return got.get(i);
            return null;
        }
    }

    final ObjectMapper mapper = new ObjectMapper();
    final MeetBattleRecorder recorder = mock(MeetBattleRecorder.class);
    final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    final AtomicLong now = new AtomicLong(1_000_000);
    MeetBattleService service;
    FakePeer host, guest;

    @BeforeEach
    void setUp() {
        service = new MeetBattleService(mapper, recorder, scheduler, now::get);
        when(recorder.record(anyLong(), anyLong(), anyInt(), any(), anyInt(), anyInt()))
                .thenReturn(new MeetBattleRecorder.Rewards(30, 10));
        host = new FakePeer("h");
        guest = new FakePeer("g");
    }

    private String code() {
        return host.last("created").path("code").asText();
    }

    private Runnable captured(boolean fixedRate) {
        ArgumentCaptor<Runnable> c = ArgumentCaptor.forClass(Runnable.class);
        if (fixedRate) verify(scheduler, atLeastOnce()).scheduleAtFixedRate(c.capture(), anyLong(), anyLong(), any(TimeUnit.class));
        else verify(scheduler, atLeastOnce()).schedule(c.capture(), anyLong(), any(TimeUnit.class));
        return c.getValue();
    }

    private void hello(FakePeer p) {
        service.onMessage(p, "{\"t\":\"hello\",\"profile\":{\"image\":\"data:image/png;base64,AA\",\"level\":3}}");
    }

    private void startFight() {
        service.create(host, 1L);
        service.join(guest, 2L, code());
        hello(host);
        hello(guest);
        assertThat(host.last("intro")).isNotNull();
        now.addAndGet(MeetBattleRules.INTRO_MS);
        captured(false).run(); // 연출 끝 → 카운트다운
        assertThat(guest.last("countdown")).isNotNull();
        now.addAndGet(MeetBattleRules.COUNTDOWN_MS);
        captured(false).run(); // 카운트다운 끝 → 대결
    }

    @Test
    void 방을_만들면_QR_코드를_받고_친구가_그_코드로_들어온다() {
        service.create(host, 1L);
        assertThat(code()).matches("\\d{6}"); // 친구가 바로 입력할 수 있는 숫자 6자리

        service.join(guest, 2L, code().toLowerCase());

        assertThat(guest.last("room").path("phase").asText()).isEqualTo("LOBBY");
        assertThat(host.last("room").path("foe").isNull()).isFalse();
    }

    @Test
    void 이름표에는_서버에_저장된_닉네임이_붙는다() {
        when(recorder.nickname(1L)).thenReturn("철수");
        when(recorder.nickname(2L)).thenReturn("영희");
        service.create(host, 1L);
        service.join(guest, 2L, code());
        // 화면이 닉네임을 보내도 무시하고 서버 값을 쓴다
        service.onMessage(host, "{\"t\":\"hello\",\"profile\":{\"image\":\"data:image/png;base64,AA\",\"name\":\"뭉치\",\"nickname\":\"가짜\"}}");

        JsonNode foe = guest.last("room").path("foe").path("profile");
        assertThat(foe.path("nickname").asText()).isEqualTo("철수");
        assertThat(foe.path("name").asText()).isEqualTo("뭉치");
    }

    @Test
    void 내_QR은_내가_찍을_수_없다() {
        service.create(host, 1L);
        FakePeer me2 = new FakePeer("h2");
        service.join(me2, 1L, code());
        assertThat(me2.last("error")).isNotNull();
        assertThat(me2.closed).isTrue();
    }

    @Test
    void 없는_코드나_이미_찬_방에는_못_들어간다() {
        service.join(guest, 2L, "NOPE1234");
        assertThat(guest.last("error")).isNotNull();

        service.create(host, 1L);
        String code = code();
        service.join(new FakePeer("g1"), 2L, code);
        FakePeer third = new FakePeer("g2");
        service.join(third, 3L, code);
        assertThat(third.last("error")).isNotNull();
    }

    @Test
    void 둘_다_준비하면_카운트다운_뒤_대결하고_많이_누른_쪽이_이긴다() {
        startFight();
        assertThat(host.last("fight")).isNotNull();
        Runnable tick = captured(true);

        for (int i = 0; i < 50; i++) {
            now.addAndGet(100);
            service.onMessage(host, "{\"t\":\"tap\",\"n\":1}");
            if (i % 2 == 0) service.onMessage(guest, "{\"t\":\"tap\",\"n\":1}");
            tick.run();
        }

        assertThat(host.last("result").path("outcome").asText()).isEqualTo("win");
        assertThat(guest.last("result").path("outcome").asText()).isEqualTo("lose");
        assertThat(host.last("result").path("reward").asInt()).isEqualTo(30);
        verify(recorder).record(eq(1L), eq(2L), eq(1), isNull(), anyInt(), anyInt());
    }

    @Test
    void 대결_중에_나가면_진다() {
        startFight();
        service.disconnect(guest);

        verify(recorder).record(eq(1L), eq(2L), eq(1), eq(2L), anyInt(), anyInt());
        assertThat(host.last("result").path("foeQuit").asBoolean()).isTrue();
        assertThat(host.last("left")).isNotNull();
    }

    @Test
    void 놀리기는_둘_모두에게_가고_너무_자주_보내면_무시한다() {
        service.create(host, 1L);
        service.join(guest, 2L, code());

        service.onMessage(host, "{\"t\":\"taunt\",\"id\":3}");
        service.onMessage(host, "{\"t\":\"taunt\",\"id\":4}");

        assertThat(guest.last("taunt").path("from").asText()).isEqualTo("foe");
        assertThat(guest.last("taunt").path("id").asInt()).isEqualTo(3);
        assertThat(host.last("taunt").path("from").asText()).isEqualTo("me");
    }

    @Test
    void 일분이_지나면_QR_코드가_바뀐다() {
        service.create(host, 1L);
        String first = code();
        now.addAndGet(MeetBattleRules.CODE_TTL_MS);
        service.sweep();
        assertThat(code()).isNotEqualTo(first);

        // 바뀌기 전 코드도 10분 동안은 들어올 수 있다 (앱이 처음인 친구가 끄적이를 그리는 동안)
        service.join(guest, 2L, first);
        assertThat(guest.last("error")).isNull();
        assertThat(guest.last("room").path("phase").asText()).isEqualTo("LOBBY");
    }

    @Test
    void 십분이_지난_코드로는_못_들어온다() {
        service.create(host, 1L);
        String first = code();
        now.addAndGet(MeetBattleRules.CODE_VALID_MS + 1);
        service.sweep();
        service.join(guest, 2L, first);
        assertThat(guest.last("error")).isNotNull();
    }

    @Test
    void 컨디션이_모자라면_시작하지_않고_둘에게_알린다() {
        when(recorder.blockReason(2L)).thenReturn("배가 고파서");
        service.create(host, 1L);
        service.join(guest, 2L, code());
        hello(host);
        hello(guest);
        assertThat(host.last("intro")).isNull();
        assertThat(host.last("error").path("message").asText()).contains("친구 끄적이가 배가 고파서");
        assertThat(guest.last("error").path("message").asText()).contains("내 끄적이가 배가 고파서");
    }
}
