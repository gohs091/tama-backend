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
import java.util.HashSet;
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
 * 받는 메시지: hello{image,facing}, move{x,y}, emote{k,to?,re?}, skill{x,y,tx,ty,d}, hit{by,id?}, ping, leave
 * 보내는 메시지: room{no,key,cap,me,members[],ghosts[]}, enter{m,gone}, leave{id}, move{id,x,y}, emote{id,k,to?,re?},
 *              skill{id,x,y,tx,ty,d}, knock{id,by,kb,s}, error{message}
 * 다녀간 끄적이(ghost): 방을 나간 사람의 기록. 시간 제한 없이 최근 RECENT_MAX명을 서버가 켜져 있는 동안 기억한다.
 *   지금 광장에 접속해 있는 사람의 기록은 보내지 않고, 그 사람이 방에 들어오면 enter의 gone으로 알려 화면에 남은 기록을 지우게 한다.
 * 기술(skill): 자기 끄적이의 배틀 기술을 광장에서 보여 준다. 바라보는 쪽(d: 1 오른쪽, -1 왼쪽)으로 곧게 나가고, 그 길에서 벗어나면 피한다.
 *   맞았는지는 맞는 쪽 앱이 정해 hit으로 알린다(자기 위치는 자기가 가장 정확하다. 다녀간 끄적이는 쏜 사람 앱이 id를 붙여 알린다).
 *   서버는 방금 쏜 기술인지, 같은 상대가 두 번 맞지 않는지 확인하고 튕김 세기(kb)와 튕길 때 쓸 난수 씨앗(s)을 붙여 knock으로 방 모두에게 알린다.
 *   kb는 레벨 차이로 조금만 달라진다: 실력(피하기)으로 뒤집을 수 있게 차이를 작게 둔다.
 *   s: 벽에 부딪히면 아무 각도로나 튕기는데(난반사), 방 모두가 같은 씨앗을 써서 같은 길로 튕기는 것을 본다.
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
    static final long SKILL_GAP_MS = 2500;
    /** 튕김 세기: 같은 레벨이면 1, 레벨 차이 하나에 KB_PER_LEVEL씩 달라지고 KB_MIN~KB_MAX 사이로 둔다 */
    static final double KB_PER_LEVEL = 0.02, KB_MIN = 0.7, KB_MAX = 1.4;
    /** 쏜 기술이 맞았다고 알릴 수 있는 시간 */
    static final long HIT_WINDOW_MS = 3000;
    /** 기술 한 번에 튕겨 낼 수 있는 끄적이 수 */
    static final int MAX_HITS_PER_CAST = 8;
    static final Set<String> EMOTES = Set.of("bow", "hi", "heart", "laugh");
    private static final String KEY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 들어오려는 사람의 끄적이 (서버가 DB에서 읽은 값) */
    public record Profile(Long userId, String nickname, String petName, int level, String stage, String imageUrl, String publicId,
                          com.tamagochi.tamagochi.domain.battle.BattleSkill skill) {}

    static final class Member {
        final Peer peer;
        final Profile profile;
        final String id;
        Room room;
        String image;     // CDN 주소 또는 앱이 보낸 작은 그림(data URL)
        String facing = "right";
        int x, y;
        long lastMove, lastEmote, lastSkill;
        Set<String> castHits;   // 마지막으로 쏜 기술에 이미 맞은 끄적이 (같은 기술에 두 번 맞지 않는다). 쏜 적이 없으면 null
        String castTo;          // 예전 앱이 겨눈 상대 (예전 앱은 다녀간 끄적이를 맞혔을 때 id 없이 알린다)
        Member(Peer peer, Profile profile, String id) { this.peer = peer; this.profile = profile; this.id = id; }
    }

    static final class Room {
        final int no;
        final String key;
        final Map<String, Member> members = new LinkedHashMap<>();
        Room(int no, String key) { this.no = no; this.key = key; }
    }

    /** 다녀간 끄적이 한 마리 */
    record Ghost(Long userId, String id, String nickname, String petName, int level, String stage, String image, String facing,
                 com.tamagochi.tamagochi.domain.battle.BattleSkill skill) {}

    private final ObjectMapper om = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    /** 다녀간 끄적이 id를 만드는 값. 서버가 켜져 있는 동안 같은 사람은 늘 같은 id를 받는다 (유저 번호는 드러나지 않는다) */
    private final long ghostSalt = random.nextLong();
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
            case "skill" -> skill(m, msg);
            case "hit" -> hit(m, msg);
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
        // gone: 이 사람이 다녀간 끄적이로 화면에 남아 있으면 지우라고 알린다 (같은 끄적이가 둘로 보이지 않게)
        ObjectNode enter = event("enter").put("gone", ghostId(m.profile.userId()));
        enter.set("m", json(m));
        broadcast(room, enter, null);
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
        // 지금 광장에 접속해 있는 사람(어느 방이든)은 다녀간 끄적이로 보내지 않는다
        List<Ghost> pool = new ArrayList<>(recent.stream().filter(g -> !byUser.containsKey(g.userId())).toList());
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

    /** 기술 쓰기. 방 모두(쓴 사람 포함)에게 보낸다 */
    private void skill(Member m, JsonNode msg) {
        if (!inRoom(m)) return;
        long now = clock.getAsLong();
        if (now - m.lastSkill < SKILL_GAP_MS) return;
        m.lastSkill = now;
        m.x = clamp(msg.path("x").asInt(m.x), 20, 980);
        m.y = clamp(msg.path("y").asInt(m.y), 0, 1000);
        ObjectNode out = event("skill").put("id", m.id).put("x", m.x).put("y", m.y)
                .put("tx", clamp(msg.path("tx").asInt(m.x), 0, 1000)).put("ty", clamp(msg.path("ty").asInt(m.y), 0, 1000));
        int d = msg.path("d").asInt(0);
        if (d != 0) out.put("d", d > 0 ? 1 : -1);
        m.castHits = new HashSet<>();
        m.castTo = null;
        String to = msg.path("to").asText("");
        if (!to.isEmpty() && to.length() <= 16 && !to.equals(m.id)) { out.put("to", to); m.castTo = to; }
        broadcast(m.room, out, null);
    }

    /**
     * 기술에 맞았다는 알림. 맞은 본인이 보내거나(by = 쏜 사람), 다녀간 끄적이가 맞았으면 쏜 사람이 그 id를 붙여 보낸다.
     * 남을 대신해 맞았다고 알릴 수는 없다. 방금 쏜 기술이고 그 기술에 아직 안 맞은 상대일 때만 받아서, 튕김 세기와 난수 씨앗을 붙여 방 모두에게 알린다.
     */
    private void hit(Member m, JsonNode msg) {
        if (!inRoom(m)) return;
        Member caster = m.room.members.get(msg.path("by").asText(""));
        if (caster == null || caster.castHits == null) return;
        if (clock.getAsLong() - caster.lastSkill > HIT_WINDOW_MS) return;
        String victim = m.id;
        if (caster == m) {
            String ghost = msg.path("id").asText("");
            if (ghost.isEmpty() && caster.castTo != null) ghost = caster.castTo;
            if (!ghost.startsWith(GHOST_PREFIX)) return;
            victim = ghost;
        }
        Integer level = levelOf(m.room, victim);
        if (level == null) return;
        if (caster.castHits.size() >= MAX_HITS_PER_CAST || !caster.castHits.add(victim)) return;
        broadcast(m.room, event("knock").put("id", victim).put("by", caster.id)
                .put("kb", knockback(caster.profile.level(), level))
                .put("s", ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE)), null);
    }

    /** 레벨 차이에 따른 튕김 세기 (같은 레벨 1.0). 높은 레벨이 낮은 레벨을 치면 멀리, 반대면 조금만 밀린다 */
    static double knockback(int attackerLevel, int targetLevel) {
        double kb = 1 + (attackerLevel - targetLevel) * KB_PER_LEVEL;
        return Math.round(Math.max(KB_MIN, Math.min(KB_MAX, kb)) * 100) / 100.0;
    }

    /** 방 사람이거나 다녀간 끄적이의 레벨 (모르는 상대면 null) */
    private Integer levelOf(Room room, String id) {
        Member member = room.members.get(id);
        if (member != null) return member.profile.level();
        return recent.stream().filter(g -> g.id().equals(id)).findFirst().map(Ghost::level).orElse(null);
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
        recent.addFirst(new Ghost(m.profile.userId(), ghostId(m.profile.userId()), m.profile.nickname(), m.profile.petName(), m.profile.level(), m.profile.stage(), m.image, m.facing, m.profile.skill()));
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
        if (m.profile.skill() != null) n.set("skill", om.valueToTree(m.profile.skill()));
        return n;
    }

    private ObjectNode json(Ghost g) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ObjectNode n = om.createObjectNode();
        n.put("id", g.id()).put("nickname", g.nickname()).put("petName", g.petName())
                .put("level", g.level()).put("stage", g.stage())
                .put("image", g.image()).put("facing", g.facing()).put("x", 80 + r.nextInt(840)).put("y", 420 + r.nextInt(520));
        if (g.skill() != null) n.set("skill", om.valueToTree(g.skill()));
        return n;
    }

    private String newId() {
        return Long.toString(random.nextLong() & Long.MAX_VALUE, 36);
    }

    /** 다녀간 끄적이 id. 접속한 사람의 id에는 '-'가 없어 서로 겹치지 않는다 */
    static final String GHOST_PREFIX = "g-";

    private String ghostId(Long userId) {
        return GHOST_PREFIX + Long.toString((userId * 0x9E3779B97F4A7C15L ^ ghostSalt) & Long.MAX_VALUE, 36);
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
