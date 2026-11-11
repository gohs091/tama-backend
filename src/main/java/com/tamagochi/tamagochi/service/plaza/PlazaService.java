package com.tamagochi.tamagochi.service.plaza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tamagochi.tamagochi.service.battle.MeetBattleService.Peer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * 끄적 광장. 테이머들이 끄적이를 데리고 모여 걸어 다니고 인사하는 곳.
 * 방은 필요할 때 생긴다: 들어온 사람은 자리가 남은 방 가운데 가장 북적이는 방으로 가고, 모두 꽉 찼으면 새 방이 생긴다.
 * 아무도 없는 방은 사라진다. 친구 초대(방 열쇠)로 들어오면 그 방으로 바로 가고, 꽉 찼어도 몇 명은 더 받는다.
 * 사람이 적은 방은 최근에 다녀간 끄적이(ghost)로 채워 보낸다. 다녀간 끄적이는 움직이지 않는 기록이라 앱이 알아서 걷게 하고 인사를 받아 준다.
 * 방은 서버 메모리에만 있다 (서버 한 대).
 *
 * 받는 메시지: hello{image,facing}, move{x,y}, emote{k,to?,re?}, ping, leave
 * 보내는 메시지: room{no,key,cap,me,members[],ghosts[]}, enter{m}, leave{id}, move{id,x,y}, emote{id,k,to?,re?}, error{message}
 * 좌표는 광장 폭·높이의 천분율(0~1000).
 */
@Slf4j
@Service
public class PlazaService {

    /** 한 방 정원. 초대로 들어오는 친구는 FRIEND_EXTRA명까지 더 받는다 */
    static final int CAP = 20;
    static final int FRIEND_EXTRA = 4;
    /** 사람과 다녀간 끄적이를 합쳐 이만큼은 보이게 채운다 */
    static final int FILL_TO = 9;
    static final int RECENT_MAX = 60;
    static final int MAX_IMAGE_CHARS = 64 * 1024;
    static final long MOVE_GAP_MS = 120;
    static final long EMOTE_GAP_MS = 500;
    static final Set<String> EMOTES = Set.of("bow", "hi", "heart", "laugh");
    private static final String KEY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 들어오려는 사람의 끄적이 (서버가 DB에서 읽은 값) */
    public record Profile(Long userId, String nickname, String petName, int level, String stage, String imageUrl, String publicId) {}

    static final class Member {
        final Peer peer;
        final Profile profile;
        final String id;
        Room room;
        String image;     // CDN 주소 또는 앱이 보낸 작은 그림(data URL)
        String facing = "right";
        int x, y;
        long lastMove, lastEmote;
        Member(Peer peer, Profile profile, String id) { this.peer = peer; this.profile = profile; this.id = id; }
    }

    static final class Room {
        final int no;
        final String key;
        final Map<String, Member> members = new LinkedHashMap<>();
        Room(int no, String key) { this.no = no; this.key = key; }
    }

    /** 다녀간 끄적이 한 마리 */
    record Ghost(Long userId, String id, String nickname, String petName, int level, String stage, String image, String facing) {}

    private final ObjectMapper om = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    private final Map<String, Member> byPeer = new LinkedHashMap<>();
    private final Map<Long, Member> byUser = new LinkedHashMap<>();
    private final Map<String, Room> rooms = new LinkedHashMap<>();   // 방 열쇠 → 방
    private final Deque<Ghost> recent = new ArrayDeque<>();

    public PlazaService() { this(System::currentTimeMillis); }

    PlazaService(LongSupplier clock) { this.clock = clock; }

    /** 연결되면 부른다. 아직 방에 넣지 않고, 앱이 hello로 그림을 보내면 그때 들여보낸다 */
    public synchronized void connect(Peer peer, Profile profile, String inviteKey) {
        // 같은 사람이 다른 기기·탭으로 또 들어오면 먼저 있던 연결을 내보낸다
        Member old = byUser.get(profile.userId());
        if (old != null) { remove(old, true); old.peer.close(); }
        Member m = new Member(peer, profile, newId());
        m.image = profile.imageUrl();
        m.room = pickRoom(inviteKey);   // 자리만 정해 둔다 (hello 뒤에 들어간다)
        byPeer.put(peer.id(), m);
        byUser.put(profile.userId(), m);
    }

    public synchronized void onMessage(Peer peer, String text) {
        Member m = byPeer.get(peer.id());
        if (m == null) return;
        JsonNode msg;
        try { msg = om.readTree(text); } catch (Exception e) { return; }
        String t = msg.path("t").asText("");
        switch (t) {
            case "hello" -> hello(m, msg);
            case "move" -> move(m, msg);
            case "emote" -> emote(m, msg);
            case "leave" -> { remove(m, true); m.peer.close(); }
            default -> { /* ping 등 */ }
        }
    }

    public synchronized void disconnect(Peer peer) {
        Member m = byPeer.get(peer.id());
        if (m != null) remove(m, true);
    }

    // ── 들어오기

    private void hello(Member m, JsonNode msg) {
        if (m.room == null || m.room.members.containsKey(m.id)) return;
        String sent = msg.path("image").asText("");
        if (m.image == null && sent.startsWith("data:image/") && sent.length() <= MAX_IMAGE_CHARS) m.image = sent;
        if (m.image == null) {
            m.peer.send("{\"t\":\"error\",\"message\":\"끄적이 그림을 불러오지 못했어요\"}");
            remove(m, false);
            m.peer.close();
            return;
        }
        if ("left".equals(msg.path("facing").asText())) m.facing = "left";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        m.x = 150 + r.nextInt(700);
        m.y = 450 + r.nextInt(450);
        Room room = m.room;
        if (!rooms.containsKey(room.key)) rooms.put(room.key, room);   // 정해 둔 방이 그사이 비어 사라졌으면 다시 연다
        broadcast(room, event("enter").set("m", json(m)), null);
        room.members.put(m.id, m);

        ObjectNode out = event("room");
        out.put("no", room.no).put("key", room.key).put("cap", CAP).put("me", m.id);
        ArrayNode members = out.putArray("members");
        room.members.values().forEach(o -> members.add(json(o)));
        ArrayNode ghosts = out.putArray("ghosts");
        ghostsFor(room).forEach(g -> ghosts.add(json(g)));
        m.peer.send(out.toString());
    }

    /** 초대 열쇠가 살아 있으면 그 방, 아니면 자리 남은 방 가운데 가장 북적이는 방, 없으면 새 방 */
    private Room pickRoom(String inviteKey) {
        if (inviteKey != null) {
            Room invited = rooms.get(inviteKey.toUpperCase());
            if (invited != null && seats(invited) < CAP + FRIEND_EXTRA) return invited;
        }
        return rooms.values().stream()
                .filter(r -> seats(r) < CAP)
                .max(Comparator.comparingInt(this::seats))
                .orElseGet(() -> {
                    Room r = new Room(freeRoomNo(), newKey());
                    rooms.put(r.key, r);
                    return r;
                });
    }

    /** 비어 있는 가장 작은 방 번호 (방이 사라지면 그 번호를 다시 쓴다) */
    private int freeRoomNo() {
        for (int no = 1; ; no++) {
            int n = no;
            if (rooms.values().stream().noneMatch(r -> r.no == n)) return no;
        }
    }

    /** 방에 있는 사람 + 들어오기로 하고 아직 hello를 안 보낸 사람 */
    private int seats(Room room) {
        return (int) byPeer.values().stream().filter(m -> m.room == room).count();
    }

    private List<Ghost> ghostsFor(Room room) {
        int want = Math.max(0, FILL_TO - room.members.size());
        if (want == 0) return List.of();
        List<Ghost> pool = new ArrayList<>(recent.stream()
                .filter(g -> room.members.values().stream().noneMatch(m -> m.profile.userId().equals(g.userId())))
                .toList());
        Collections.shuffle(pool, random);
        return pool.subList(0, Math.min(want, pool.size()));
    }

    // ── 방 안에서

    private void move(Member m, JsonNode msg) {
        if (!inRoom(m)) return;
        long now = clock.getAsLong();
        if (now - m.lastMove < MOVE_GAP_MS) return;
        m.lastMove = now;
        m.x = clamp(msg.path("x").asInt(m.x), 20, 980);
        m.y = clamp(msg.path("y").asInt(m.y), 0, 1000);
        broadcast(m.room, event("move").put("id", m.id).put("x", m.x).put("y", m.y), m);
    }

    private void emote(Member m, JsonNode msg) {
        if (!inRoom(m)) return;
        String k = msg.path("k").asText("");
        if (!EMOTES.contains(k)) return;
        long now = clock.getAsLong();
        if (now - m.lastEmote < EMOTE_GAP_MS) return;
        m.lastEmote = now;
        ObjectNode out = event("emote").put("id", m.id).put("k", k);
        String to = msg.path("to").asText("");
        if (!to.isEmpty() && to.length() <= 16) out.put("to", to);
        if (msg.path("re").asBoolean(false)) out.put("re", true);   // 받은 인사에 답하는 인사 (앱이 "나도 인사"를 다시 묻지 않는다)
        broadcast(m.room, out, m);
    }

    private void remove(Member m, boolean remember) {
        byPeer.remove(m.peer.id());
        byUser.remove(m.profile.userId(), m);
        Room room = m.room;
        m.room = null;
        if (room == null) return;
        if (room.members.remove(m.id) != null) {
            broadcast(room, event("leave").put("id", m.id), null);
            if (remember && m.image != null) remember(m);
        }
        if (seats(room) == 0) rooms.remove(room.key);
    }

    private void remember(Member m) {
        recent.removeIf(g -> g.userId().equals(m.profile.userId()));
        recent.addFirst(new Ghost(m.profile.userId(), "g" + newId(), m.profile.nickname(), m.profile.petName(), m.profile.level(), m.profile.stage(), m.image, m.facing));
        while (recent.size() > RECENT_MAX) recent.removeLast();
    }

    private boolean inRoom(Member m) {
        return m.room != null && m.room.members.containsKey(m.id);
    }

    private void broadcast(Room room, ObjectNode msg, Member except) {
        String json = msg.toString();
        for (Member o : room.members.values()) if (o != except) o.peer.send(json);
    }

    // ── JSON

    private ObjectNode event(String t) {
        return om.createObjectNode().put("t", t);
    }

    private ObjectNode json(Member m) {
        ObjectNode n = om.createObjectNode();
        n.put("id", m.id).put("nickname", m.profile.nickname()).put("petName", m.profile.petName())
                .put("level", m.profile.level()).put("stage", m.profile.stage())
                .put("image", m.image).put("facing", m.facing).put("x", m.x).put("y", m.y);
        return n;
    }

    private ObjectNode json(Ghost g) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ObjectNode n = om.createObjectNode();
        n.put("id", g.id()).put("nickname", g.nickname()).put("petName", g.petName())
                .put("level", g.level()).put("stage", g.stage())
                .put("image", g.image()).put("facing", g.facing()).put("x", 80 + r.nextInt(840)).put("y", 420 + r.nextInt(520));
        return n;
    }

    private String newId() {
        return Long.toString(random.nextLong() & Long.MAX_VALUE, 36);
    }

    private String newKey() {
        for (;;) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(KEY_CHARS.charAt(random.nextInt(KEY_CHARS.length())));
            if (!rooms.containsKey(sb.toString())) return sb.toString();
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ── 테스트용
    synchronized int roomCount() { return rooms.size(); }
    synchronized int recentCount() { return recent.size(); }
}
