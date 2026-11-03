package com.tamagochi.tamagochi.service.battle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static com.tamagochi.tamagochi.service.battle.MeetBattleRules.*;

/**
 * 만나서 배틀 방. 방장이 방을 만들면 QR 코드(방 코드)가 생기고, 같은 자리의 친구가 그 QR을 찍어 들어온다.
 * 들어오자마자(둘의 끄적이 그림이 모이면) 시작 연출 → 3초 카운트다운 → 5초 연타 줄다리기, 서버가 판정한다.
 * 결과 뒤에는 둘 다 "한 판 더"(ready)를 누르면 카운트다운부터 다시. 방은 서버 메모리에만 있다 (서버 한 대).
 *
 * 받는 메시지: hello{profile}, ready, tap{n}, taunt{id}, leave
 * 보내는 메시지: created{code,ttlMs}, room{phase,me,foe}, countdown{ms}, fight{ms}, bar{me,foe}, result{...}, taunt{from,id}, left, error{message}
 */
@Slf4j
@Service
public class MeetBattleService {

    /** 연결 하나 (WebSocket 세션을 감싼다. 테스트에서는 가짜로 바꾼다) */
    public interface Peer {
        String id();
        void send(String json);
        void close();
    }

    enum Phase { WAITING, LOBBY, INTRO, COUNTDOWN, FIGHT, RESULT }

    static final class Player {
        final Peer peer;
        final Long userId;
        JsonNode profile;
        boolean ready;
        long lastTaunt;
        Player(Peer peer, Long userId) { this.peer = peer; this.userId = userId; }
    }

    static final class Room {
        final String id;
        String code;
        long codeAt;
        final long createdAt;
        Player host;
        Player guest;
        Phase phase = Phase.WAITING;
        TugMatch match;
        ScheduledFuture<?> tick;
        ScheduledFuture<?> pending;
        Room(String id, long now) { this.id = id; this.createdAt = now; }
        boolean isHost(Peer p) { return host != null && host.peer.id().equals(p.id()); }
        Player me(Peer p) { return isHost(p) ? host : guest; }
        Player foe(Peer p) { return isHost(p) ? guest : host; }
    }

    /** 방 코드: 숫자 6자리. QR 대신 친구 화면을 보고 바로 입력할 수 있게 짧게 둔다 */
    private static final String CODE_CHARS = "0123456789";
    private static final int CODE_LENGTH = 6;

    private final ObjectMapper mapper;
    private final MeetBattleRecorder recorder;
    private final ScheduledExecutorService scheduler;
    private final LongSupplier clock;
    private final SecureRandom random = new SecureRandom();

    private final Map<String, Room> roomsByCode = new ConcurrentHashMap<>();
    private final Map<String, Long> codeIssuedAt = new ConcurrentHashMap<>();
    private final Map<String, Room> roomsByPeer = new ConcurrentHashMap<>();

    @Autowired
    public MeetBattleService(ObjectMapper mapper, MeetBattleRecorder recorder) {
        this(mapper, recorder, Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "meet-battle");
            t.setDaemon(true);
            return t;
        }), System::currentTimeMillis);
        // 오래된 방 코드 바꾸기·버려진 방 닫기
        scheduler.scheduleAtFixedRate(this::sweep, 5, 5, TimeUnit.SECONDS);
    }

    MeetBattleService(ObjectMapper mapper, MeetBattleRecorder recorder, ScheduledExecutorService scheduler, LongSupplier clock) {
        this.mapper = mapper;
        this.recorder = recorder;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    // ── 연결 ──────────────────────────────────────────────

    /** 방장: 새 방을 만든다 */
    public void create(Peer peer, Long userId) {
        Room room = new Room(peer.id(), clock.getAsLong());
        synchronized (room) {
            room.host = new Player(peer, userId);
            roomsByPeer.put(peer.id(), room);
            assignCode(room);
        }
    }

    /** 손님: QR의 방 코드로 들어간다 */
    public void join(Peer peer, Long userId, String code) {
        Room room = code == null ? null : roomsByCode.get(code.toUpperCase());
        if (room == null) {
            error(peer, "QR이 만료됐어요. 친구 화면의 새 QR을 찍어 주세요");
            peer.close();
            return;
        }
        Long issued = codeIssuedAt.get(code.toUpperCase());
        if (issued == null || clock.getAsLong() - issued > CODE_VALID_MS) {
            error(peer, "QR이 만료됐어요. 친구 화면의 새 QR을 찍어 주세요");
            peer.close();
            return;
        }
        synchronized (room) {
            if (room.phase != Phase.WAITING || room.guest != null) {
                error(peer, "이미 대결 중인 방이에요");
                peer.close();
                return;
            }
            if (room.host.userId.equals(userId)) {
                error(peer, "내 QR은 친구가 찍어야 해요");
                peer.close();
                return;
            }
            room.guest = new Player(peer, userId);
            room.phase = Phase.LOBBY;
            forgetCodes(room);
            roomsByPeer.put(peer.id(), room);
            broadcastRoom(room);
            maybeStartIntro(room);
            // 그림이 늦게 와도 기다리지 않고 시작한다
            scheduler.schedule(() -> { synchronized (room) {
                        if (room.phase != Phase.LOBBY) return;
                        log.info("Meet battle started without waiting for profile: hostProfile={}, guestProfile={}",
                                room.host.profile != null, room.guest != null && room.guest.profile != null);
                        startIntro(room);
                    } },
                    PROFILE_WAIT_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void disconnect(Peer peer) {
        Room room = roomsByPeer.remove(peer.id());
        if (room == null) return;
        synchronized (room) {
            boolean fighting = room.phase == Phase.COUNTDOWN || room.phase == Phase.FIGHT;
            // 시작 연출 중에 나가면 대결 없이 끝난다
            Player me = room.me(peer);
            Player foe = room.foe(peer);
            if (fighting && room.match != null && me != null && foe != null) {
                // 대결 중에 나가면 진다 (보상 없음)
                finish(room, room.isHost(peer) ? -1 : 1, me.userId, false);
            }
            close(room);
            if (foe != null) {
                send(foe.peer, obj("left"));
                roomsByPeer.remove(foe.peer.id());
            }
        }
    }

    // ── 메시지 ────────────────────────────────────────────

    public void onMessage(Peer peer, String text) {
        Room room = roomsByPeer.get(peer.id());
        if (room == null) return;
        JsonNode msg;
        try {
            msg = mapper.readTree(text);
        } catch (Exception e) {
            return;
        }
        String type = msg.path("t").asText("");
        synchronized (room) {
            Player me = room.me(peer);
            if (me == null) return;
            switch (type) {
                case "hello" -> hello(room, me, msg.path("profile"));
                case "ready" -> ready(room, me);
                case "tap" -> {
                    if (room.phase == Phase.FIGHT && room.match != null) {
                        room.match.addTaps(room.isHost(peer), Math.min(50, msg.path("n").asInt(0)), clock.getAsLong());
                    }
                }
                case "taunt" -> taunt(room, me, msg.path("id").asInt(-1));
                case "leave" -> { /* 연결을 끊으면 disconnect에서 정리한다 */ }
                default -> { }
            }
        }
    }

    /** 상대 화면 이름표에 보일 끄적이 이름. 규칙에 맞지 않으면 빈 값(앱이 "친구 끄적이"로 보여준다) */
    static String battleName(String raw) {
        try {
            return com.tamagochi.tamagochi.service.tamagotchi.PetNameRules.clean(raw);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private void hello(Room room, Player me, JsonNode profile) {
        if (!profile.isObject()) return;
        ObjectNode p = mapper.createObjectNode();
        String image = profile.path("image").asText("");
        boolean imageOk = image.startsWith("data:image/") && image.length() <= THUMB_MAX_CHARS;
        if (!imageOk) {
            // 상대 화면에 끄적이가 안 보이는 원인을 찾기 위한 로그 (그림 내용은 남기지 않는다)
            log.warn("Meet battle profile image dropped: user={}, stage={}, length={}, dataUrl={}",
                    me.userId, profile.path("stage").asText(""), image.length(), image.startsWith("data:image/"));
        }
        p.put("image", imageOk ? image : "");
        p.put("level", Math.max(1, Math.min(999, profile.path("level").asInt(1))));
        p.put("stage", profile.path("stage").asText("BABY").replaceAll("[^A-Z]", ""));
        p.put("facing", "left".equals(profile.path("facing").asText()) ? "left" : "right");
        p.put("name", battleName(profile.path("name").asText("")));
        me.profile = p;
        broadcastRoom(room);
        maybeStartIntro(room);
    }

    private void maybeStartIntro(Room room) {
        if (room.phase == Phase.LOBBY && room.guest != null && room.host.profile != null && room.guest.profile != null) startIntro(room);
    }

    private void startIntro(Room room) {
        if (room.phase != Phase.LOBBY || room.guest == null) return;
        if (blocked(room, true)) return;
        room.phase = Phase.INTRO;
        broadcastRoom(room);
        ObjectNode in = obj("intro");
        in.put("ms", INTRO_MS);
        sendBoth(room, in);
        room.pending = scheduler.schedule(() -> { synchronized (room) { if (room.phase == Phase.INTRO) startCountdown(room); } },
                INTRO_MS, TimeUnit.MILLISECONDS);
    }

    /** 결과 화면의 "한 판 더": 둘 다 누르면 카운트다운부터 다시 */
    private void ready(Room room, Player me) {
        if (room.phase != Phase.RESULT || room.guest == null) return;
        me.ready = true;
        if (!(room.host.ready && room.guest.ready)) { broadcastRoom(room); return; }
        if (blocked(room, false)) return;
        startCountdown(room);
    }

    /**
     * 무료 판을 다 쓰고 포만감·파워가 모자란 사람이 있으면 시작하지 않고 둘에게 알린다.
     * 처음(first)이면 방을 닫고, 한 판 더라면 결과 화면에 그대로 둔다.
     */
    private boolean blocked(Room room, boolean first) {
        String hostWhy, guestWhy;
        try {
            hostWhy = recorder.blockReason(room.host.userId);
            guestWhy = recorder.blockReason(room.guest.userId);
        } catch (Exception e) {
            log.warn("Meet battle block check failed: {}", e.getMessage());
            return false;
        }
        if (hostWhy == null && guestWhy == null) return false;
        for (Player p : new Player[]{room.host, room.guest}) {
            String mine = p == room.host ? hostWhy : guestWhy;
            String theirs = p == room.host ? guestWhy : hostWhy;
            String msg = mine != null
                    ? "오늘 무료 배틀 " + FREE_DAILY_BATTLES + "판을 다 했어요. 내 끄적이가 " + mine + " 배틀할 수 없어요 (고기·알약을 주세요)"
                    : "친구 끄적이가 " + theirs + " 배틀할 수 없어요";
            ObjectNode b = obj(first ? "error" : "blocked");
            b.put("message", msg);
            send(p.peer, b);
            p.ready = false;
        }
        if (first) {
            close(room);
            roomsByPeer.remove(room.host.peer.id());
            roomsByPeer.remove(room.guest.peer.id());
            room.host.peer.close();
            room.guest.peer.close();
        } else {
            broadcastRoom(room);
        }
        return true;
    }

    private void startCountdown(Room room) {
        room.phase = Phase.COUNTDOWN;
        room.match = null;
        room.host.ready = false;
        room.guest.ready = false;
        broadcastRoom(room);
        ObjectNode cd = obj("countdown");
        cd.put("ms", COUNTDOWN_MS);
        sendBoth(room, cd);
        room.pending = scheduler.schedule(() -> startFight(room), COUNTDOWN_MS, TimeUnit.MILLISECONDS);
    }

    private void startFight(Room room) {
        synchronized (room) {
            if (room.phase != Phase.COUNTDOWN || room.guest == null) return;
            room.phase = Phase.FIGHT;
            room.match = new TugMatch(clock.getAsLong());
            ObjectNode f = obj("fight");
            f.put("ms", FIGHT_MS);
            f.put("ko", KO_LEAD);
            sendBoth(room, f);
            room.tick = scheduler.scheduleAtFixedRate(() -> tick(room), TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        }
    }

    void tick(Room room) {
        synchronized (room) {
            if (room.phase != Phase.FIGHT || room.match == null) return;
            TugMatch m = room.match;
            sendBar(room.host, m.hostTaps(), m.guestTaps());
            sendBar(room.guest, m.guestTaps(), m.hostTaps());
            Optional<TugMatch.Outcome> out = m.decided(clock.getAsLong());
            out.ifPresent(o -> finish(room, o.winner(), null, o.ko()));
        }
    }

    private void sendBar(Player p, int me, int foe) {
        if (p == null) return;
        ObjectNode b = obj("bar");
        b.put("me", me);
        b.put("foe", foe);
        send(p.peer, b);
    }

    /** 판을 끝낸다. winner: 1 = 방장, -1 = 손님, 0 = 무승부 */
    private void finish(Room room, int winner, Long quitter, boolean ko) {
        if (room.tick != null) room.tick.cancel(false);
        room.tick = null;
        room.phase = Phase.RESULT;
        room.host.ready = false;
        if (room.guest != null) room.guest.ready = false;
        TugMatch m = room.match;
        int hostTaps = m == null ? 0 : m.hostTaps();
        int guestTaps = m == null ? 0 : m.guestTaps();
        MeetBattleRecorder.Rewards rewards;
        try {
            rewards = recorder.record(room.host.userId, room.guest.userId, winner, quitter, hostTaps, guestTaps);
        } catch (Exception e) {
            log.error("Meet battle record failed", e);
            rewards = new MeetBattleRecorder.Rewards(0, 0);
        }
        sendResult(room.host, winner > 0, winner == 0, hostTaps, guestTaps, rewards.host(), ko, quitter, rewards.hostCharged(), rewards.hostFreeLeft());
        sendResult(room.guest, winner < 0, winner == 0, guestTaps, hostTaps, rewards.guest(), ko, quitter, rewards.guestCharged(), rewards.guestFreeLeft());
        if (quitter == null) broadcastRoom(room);
    }

    private void sendResult(Player p, boolean win, boolean draw, int me, int foe, int reward, boolean ko, Long quitter,
                            boolean charged, int freeLeft) {
        if (p == null || p.userId.equals(quitter)) return;
        ObjectNode r = obj("result");
        r.put("outcome", draw ? "draw" : win ? "win" : "lose");
        r.put("me", me);
        r.put("foe", foe);
        r.put("reward", reward);
        r.put("ko", ko);
        r.put("foeQuit", quitter != null);
        r.put("charged", charged);    // 이번 판에 포만감·파워를 썼다
        r.put("freeLeft", freeLeft);  // 오늘 남은 무료 판
        send(p.peer, r);
    }

    private void taunt(Room room, Player me, int id) {
        if (room.phase == Phase.WAITING || id < 0 || id >= TAUNT_COUNT) return;
        long now = clock.getAsLong();
        if (now - me.lastTaunt < TAUNT_GAP_MS) return;
        me.lastTaunt = now;
        for (Player p : new Player[]{room.host, room.guest}) {
            if (p == null) continue;
            ObjectNode t = obj("taunt");
            t.put("from", p == me ? "me" : "foe");
            t.put("id", id);
            send(p.peer, t);
        }
    }

    // ── 방 관리 ───────────────────────────────────────────

    private void assignCode(Room room) {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            code = sb.toString();
        } while (roomsByCode.putIfAbsent(code, room) != null);
        room.code = code;
        room.codeAt = clock.getAsLong();
        codeIssuedAt.put(code, room.codeAt);
        ObjectNode c = obj("created");
        c.put("code", code);
        c.put("ttlMs", CODE_TTL_MS);
        send(room.host.peer, c);
    }

    /** 이 방에 낸 코드를 모두 지운다 (친구가 들어왔거나 방이 닫혔다) */
    private void forgetCodes(Room room) {
        roomsByCode.entrySet().removeIf(e -> {
            if (e.getValue() != room) return false;
            codeIssuedAt.remove(e.getKey());
            return true;
        });
    }

    void sweep() {
        long now = clock.getAsLong();
        codeIssuedAt.entrySet().removeIf(e -> {
            if (now - e.getValue() <= CODE_VALID_MS) return false;
            roomsByCode.remove(e.getKey());
            return true;
        });
        for (Room room : roomsByPeer.values()) {
            synchronized (room) {
                if (room.phase != Phase.WAITING) continue;
                if (now - room.createdAt > WAITING_ROOM_TTL_MS) {
                    error(room.host.peer, "친구가 오지 않아 방을 닫았어요");
                    close(room);
                    roomsByPeer.remove(room.host.peer.id());
                    room.host.peer.close();
                } else if (now - room.codeAt >= CODE_TTL_MS) {
                    assignCode(room); // QR 새로 고침
                }
            }
        }
    }

    private void close(Room room) {
        if (room.tick != null) room.tick.cancel(false);
        if (room.pending != null) room.pending.cancel(false);
        forgetCodes(room);
        room.phase = Phase.RESULT;
    }

    private void broadcastRoom(Room room) {
        for (Player p : new Player[]{room.host, room.guest}) {
            if (p == null) continue;
            Player foe = p == room.host ? room.guest : room.host;
            ObjectNode r = obj("room");
            r.put("phase", room.phase.name());
            r.set("me", playerJson(p));
            r.set("foe", foe == null ? null : playerJson(foe));
            send(p.peer, r);
        }
    }

    private ObjectNode playerJson(Player p) {
        ObjectNode n = mapper.createObjectNode();
        n.set("profile", p.profile);
        n.put("ready", p.ready);
        return n;
    }

    private ObjectNode obj(String type) {
        ObjectNode n = mapper.createObjectNode();
        n.put("t", type);
        return n;
    }

    private void error(Peer peer, String message) {
        ObjectNode e = obj("error");
        e.put("message", message);
        send(peer, e);
    }

    private void sendBoth(Room room, ObjectNode msg) {
        if (room.host != null) send(room.host.peer, msg);
        if (room.guest != null) send(room.guest.peer, msg);
    }

    private void send(Peer peer, ObjectNode msg) {
        try {
            peer.send(mapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.debug("Meet battle send failed: {}", e.getMessage());
        }
    }

    int openRooms() {
        return (int) roomsByPeer.values().stream().distinct().count();
    }
}
