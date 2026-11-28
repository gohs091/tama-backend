package com.tamagochi.tamagochi.service.plaza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tamagochi.tamagochi.domain.spar.SparDrop;
import com.tamagochi.tamagochi.service.battle.MeetBattleService.Peer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 끄적 광장. 테이머들이 끄적이를 데리고 모여 걸어 다니고 인사하는 곳.
 * 방은 필요할 때 생긴다: 들어온 사람은 자리가 남은 방 가운데 가장 북적이는 방으로 가고, 모두 꽉 찼으면 새 방이 생긴다.
 * 아무도 없는 방은 사라진다. 친구 초대(방 열쇠)로 들어오면 그 방으로 바로 가고, 꽉 찼어도 몇 명은 더 받는다.
 * 사람이 적은 방은 최근에 다녀간 끄적이(ghost)로 채워 보낸다. 다녀간 끄적이는 움직이지 않는 기록이라 앱이 알아서 걷게 하고 인사를 받아 준다.
 * 방은 서버 메모리에만 있다 (서버 한 대).
 *
 * 정각 밀어내기(ev): 매 시각 정각에 방마다 열리는 놀이. 이 시간에만 기술을 쓸 수 있다.
 *   gather(모이기) → ready(잠깐: 놀이터 안에 있는 앱이 join을 보낸다) → fight(밀어내기) → done(다음 정각까지 우승자 표시)
 *   fight 동안 놀이터는 점점 줄어든다(줄어든 크기는 시각으로 정해지므로 앱이 서버 시각에 맞춰 스스로 계산한다).
 *   놀이터 밖으로 나간 끄적이는 그 앱이 out으로 알려 탈락하고, 한 마리만 남으면 우승이다. 시간이 끝나면 남은 끄적이가 함께 우승한다.
 *   참가자가 둘이 안 되면 혼자 연습(solo): 탈락·우승 없이 기술만 쓸 수 있고 놀이터도 줄지 않는다.
 *   위치는 앱이 가장 잘 알기 때문에 참가(join)·탈락(out)은 앱이 알리는 대로 받는다 (보상이 걸리면 서버가 판정하도록 바꿔야 한다).
 *
 * 받는 메시지: hello{image,facing}, move{x,y}, emote{k,to?,re?}, sleep{on}, skill{x,y,tx,ty,d}, hit{by,id?}, join, out, ping, leave
 *              (3D 방만: atk{d,m[]}, jump, chat{text}, block{u,on}, report{u,why})
 * 보내는 메시지: room{no,key,cap,me,members[],ghosts[]}, enter{m,gone}, leave{id}, move{id,x,y}, emote{id,k,to?,re?}, sleep{id,on},
 *              skill{id,x,y,tx,ty,d}, knock{id,by,kb,s}, ev{phase,now,start,next,…,players[],alive[],solo,winners[]}, out{id,left}, error{message}
 *              (room에도 ev가 들어 있다)
 *              3D 방만: room{…,v:3,now,mobs[{i,e,hp,max,at?}],hunt{ok,why?},chat,blocked[]}(members[]에 u: 유저 코드, image 는 64칸 그림), atk{id,d}, jump{id},
 *              mob{i,hp,by,at?}(맞음·죽음: at 은 다시 나올 시각) 또는 mob{i,hp,e,max}(되살아남), loot{i,exp,ink,level,stage,lvUp,drop?,hunger,power,ok,why?},
 *              chat{id,u,text}(보낸 사람 포함 방 모두, 차단한 사람 제외), chatNo{why: fast|word|muted|off}(보낸 사람에게만)
 * 채팅(3D 방): 글은 ChatRules 로 다듬고 금칙어를 거른다. 간격 1.5초 · 10초에 5번. 차단 목록은 유저 코드(u)로 주고받고 저장은 Moderation 이 잠금 밖에서 한다.
 *   차단한 사람의 chat 과 나에게 하는 emote 는 받지 않는다. 신고는 10초에 한 번, 신고당한 사람의 최근 열 줄이 함께 간다.
 * 몬스터(3D 방): 서버가 정한다. 방 사람 모두가 같은 몬스터를 보고, 죽이면 때린 사람 모두가 한 마리 몫을 받는다 (PlazaMobs, 지급은 Hunts).
 * 방문 기록: 방에 들어왔다 나간 사람마다 한 줄 (머문 시간 · 잡은 마릿수 · 말한 횟수). 저장은 Visits 가 잠금 밖에서 한다.
 * 다녀간 끄적이(ghost): 방을 나간 사람의 기록. 시간 제한 없이 최근 RECENT_MAX명을 서버가 켜져 있는 동안 기억한다.
 *   지금 광장에 접속해 있는 사람의 기록은 보내지 않고, 그 사람이 방에 들어오면 enter의 gone으로 알려 화면에 남은 기록을 지우게 한다.
 * 기술(skill): 자기 끄적이의 배틀 기술을 광장에서 보여 준다. 바라보는 쪽(d: 1 오른쪽, -1 왼쪽)으로 곧게 나가고, 그 길에서 벗어나면 피한다.
 *   맞았는지는 맞는 쪽 앱이 정해 hit으로 알린다(자기 위치는 자기가 가장 정확하다. 다녀간 끄적이는 쏜 사람 앱이 id를 붙여 알린다).
 *   서버는 방금 쏜 기술인지, 같은 상대가 두 번 맞지 않는지 확인하고 튕김 세기(kb)와 튕길 때 쓸 난수 씨앗(s)을 붙여 knock으로 방 모두에게 알린다.
 *   kb는 레벨 차이로 조금만 달라진다: 실력(피하기)으로 뒤집을 수 있게 차이를 작게 둔다.
 *   s: 벽에 부딪히면 아무 각도로나 튕기는데(난반사), 방 모두가 같은 씨앗을 써서 같은 길로 튕기는 것을 본다.
 * 잠자기(sleep): 광장에서 끄적이를 재운다. 걷거나 기술을 쓰거나 기술에 맞으면 깬다(앱들도 move·skill·knock을 보고 각자 깨운다).
 *   나중에 들어온 사람도 자는 모습을 보도록 members[]의 끄적이에 sleep:true를 붙인다.
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
    /** 앱이 보내는 작은 그림의 길이 제한. 앱은 70,000자까지 만든다 (그보다 작으면 들어오지 못하는 끄적이가 생긴다) */
    static final int MAX_IMAGE_CHARS = 72_000;
    static final long MOVE_GAP_MS = 120;
    static final long EMOTE_GAP_MS = 500;
    static final long SKILL_GAP_MS = 2500;
    /** 튕김 세기: 같은 레벨이면 1, 레벨 차이 하나에 KB_PER_LEVEL씩 달라지고 KB_MIN~KB_MAX 사이로 둔다 */
    static final double KB_PER_LEVEL = 0.02, KB_MIN = 0.7, KB_MAX = 1.4;
    /** 쏜 기술이 맞았다고 알릴 수 있는 시간 */
    static final long HIT_WINDOW_MS = 3000;
    /** 기술 한 번에 튕겨 낼 수 있는 끄적이 수 */
    static final int MAX_HITS_PER_CAST = 8;
    static final long ATK_GAP_MS = 450, JUMP_GAP_MS = 500;
    /** 채팅: 한 번 말하고 다음까지, 그리고 CHAT_WINDOW_MS 안에 CHAT_PER_WINDOW 번까지 */
    static final long CHAT_GAP_MS = 1500, CHAT_WINDOW_MS = 10_000;
    static final int CHAT_PER_WINDOW = 5;
    /** 다듬기 전에 원문을 이만큼(글자)만 본다 */
    static final int CHAT_RAW_MAX = 200;
    static final long REPORT_GAP_MS = 10_000, BLOCK_GAP_MS = 500;
    /** 신고에 붙이는 최근 말 줄 수, 한 사람이 차단해 둘 수 있는 수 */
    static final int REPORT_LINES = 10, BLOCK_MAX = 200;
    static final Set<String> REPORT_WHYS = Set.of("chat", "name", "etc");
    static final int MAX_MOBS_PER_ATK = 4;
    /** 죽은 몬스터가 다시 나오기까지 */
    static final long RESPAWN_MS = 6000;
    /** 사냥으로 퀘스트를 세는 마릿수 간격 */
    static final int QUEST_EVERY = 10;
    static final Set<String> ATK_DIRS = Set.of("u", "d", "l", "r");
    static final Set<String> EMOTES = Set.of("bow", "hi", "heart", "laugh");
    private static final String KEY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 들어오려는 사람의 끄적이 (서버가 DB에서 읽은 값) */
    public record Profile(Long userId, String nickname, String petName, int level, String stage, String imageUrl, String publicId,
                          com.tamagochi.tamagochi.domain.battle.BattleSkill skill) {}

    /**
     * 3D 앱에만 쓰는 값 (서버가 DB 에서 읽어 넘긴다). image64: 64×64 그림, userCode: 신고 · 차단에 쓰는 고정 유저 코드.
     * huntOk · huntWhy · blocked · mutedUntil 은 사냥 · 채팅 작업에서 채운다
     */
    public record Extras(String image64, String userCode, boolean huntOk, String huntWhy, Set<String> blocked, long mutedUntil) {
        public static final Extras NONE = new Extras(null, null, false, null, Set.of(), 0);
    }

    /** 사냥 한 마리의 지급 결과 (Hunts 가 DB 에서 정해 돌려준다). ok=false 면 더는 사냥할 수 없다 (why: 이유) */
    public record HuntResult(boolean ok, String why, int exp, int ink, int level, String stage, boolean lvUp, SparDrop drop, int satiety, int power) {}

    /**
     * 몬스터를 잡은 사람에게 주는 쪽. 광장의 잠금 안에서 불리므로 DB 일은 다른 실행기에 넘기고 끝나면 done 을 부른다
     * (done 은 어느 스레드에서나 불러도 된다: onLoot 이 잠금을 잡는다)
     */
    public interface Hunts {
        void kill(long userId, String enemyKey, boolean questTick, Consumer<HuntResult> done);
    }

    /** 3D 방의 몬스터 한 마리 (번호는 방의 mobs 배열 자리) */
    static final class Mob {
        final String enemy;
        final int max;
        int hp;
        long respawnAt;   // 죽었을 때만 뜻이 있다
        final Set<String> hitters = new LinkedHashSet<>();   // 이 몬스터를 한 번이라도 때린 사람 (id)
        Mob(String enemy, int max) { this.enemy = enemy; this.max = max; this.hp = max; }
        boolean dead() { return hp <= 0; }
    }

    static final class Member {
        final Peer peer;
        final Profile profile;
        final String id;
        Room room;
        String inviteKey;   // 연결 때 받은 초대 열쇠 (3D 로 옮길 때 다시 쓴다)
        Extras extras = Extras.NONE;
        String image64;     // 3D 방에서 보이는 64×64 그림 (3D 앱만). 없으면 3D 방에서도 image 를 쓴다
        boolean v3;         // hello 에 v:3 을 보낸 3D 앱
        String image;     // 서버가 줄인 그림(data URL), 앱이 보낸 작은 그림(data URL), 또는 원본 CDN 주소
        String spareImage;   // 원본 CDN 주소. 위 둘이 다 없을 때만 쓴다
        String facing = "right";
        int x, y;
        long lastMove, lastEmote, lastSkill, lastAtk, lastJump;
        final Set<String> blocked = new HashSet<>();   // 내가 차단한 사람의 유저 코드 (3D 방)
        long mutedUntil;    // 이 때(epoch ms)까지 말할 수 없다
        long lastChat, lastReport;
        long lastBlock = Long.MIN_VALUE / 2;   // 차단 · 해제를 마지막으로 받아들인 때
        final Deque<Long> chatTimes = new ArrayDeque<>();   // 최근 말한 때 (도배 제한)
        final Deque<String> lines = new ArrayDeque<>();     // 방에 나간 최근 말 열 줄 (신고에 붙인다)
        String stage;       // 지금 단계. 처음엔 profile.stage, 사냥 결과로 달라질 수 있다
        int level;          // 지금 레벨. 처음엔 profile.level, 사냥 결과로 달라질 수 있다
        boolean huntOk;     // 사냥할 수 있는 상태 (지급 결과가 못 한다고 하면 꺼진다)
        String huntWhy;
        int kills;          // 이 연결에서 잡은 마릿수 (열 마리마다 퀘스트를 센다)
        int chats;          // 이 연결에서 방에 나간 말의 수 (방문 기록용. 내용은 남기지 않는다)
        long enteredAt;     // 방에 들어온 때 (epoch ms). 방문 기록용
        boolean sleeping;   // 광장에서 자는 중 (걷거나 기술을 쓰거나 맞으면 깬다)
        Set<String> castHits;   // 마지막으로 쏜 기술에 이미 맞은 끄적이 (같은 기술에 두 번 맞지 않는다). 쏜 적이 없으면 null
        String castTo;          // 예전 앱이 겨눈 상대 (예전 앱은 다녀간 끄적이를 맞혔을 때 id 없이 알린다)
        Member(Peer peer, Profile profile, String id) { this.peer = peer; this.profile = profile; this.id = id; this.level = profile.level(); this.stage = profile.stage(); }
    }

    static final class Room {
        final int no;
        final String key;
        final String kind;   // "2d" | "3d" — 두 종류는 서로 섞이지 않는다
        final Map<String, Member> members = new LinkedHashMap<>();
        Match match;   // 이번(또는 지난) 정각의 밀어내기. 없으면 null
        Mob[] mobs;    // 3D 방의 몬스터. 2D 방은 null
        Room(int no, String key, String kind) { this.no = no; this.key = key; this.kind = kind; }
        boolean is3d() { return "3d".equals(kind); }
    }

    /**
     * 정각 밀어내기 시간표(ms): period마다 한 번 열려 length 동안 이어진다.
     * 처음 gather 동안 모이고, ready 동안 참가를 받고, 그 뒤 shrink에 걸쳐 놀이터가 minScale까지 줄어든다.
     */
    public record EventTimes(long period, long gather, long ready, long length, long shrink, double minScale) {
        static final EventTimes HOURLY = new EventTimes(3_600_000, 60_000, 3_000, 600_000, 480_000, 0.35);
    }

    /** 한 방의 밀어내기 한 판 */
    static final class Match {
        final long no;                                       // 몇 번째 정각인지 (시각 / period)
        String phase;                                        // gather | ready | fight | done
        boolean solo;                                        // 참가자가 둘이 안 돼 혼자 연습 중
        final Set<String> players = new LinkedHashSet<>();   // 참가한 끄적이 id
        final Set<String> alive = new LinkedHashSet<>();     // 아직 놀이터에 남은 끄적이
        final Map<String, String> names = new LinkedHashMap<>();   // 참가자 닉네임 (나간 뒤에도 우승자 이름을 보여 주려고)
        final List<String> winners = new ArrayList<>();
        String lastOut;                                      // 마지막으로 탈락한 끄적이 (둘이 한꺼번에 나가면 나중에 나간 쪽이 우승)
        Match(long no, String phase) { this.no = no; this.phase = phase; }
    }

    /** 다녀간 끄적이 한 마리 */
    record Ghost(Long userId, String id, String nickname, String petName, int level, String stage, String image, String facing,
                 com.tamagochi.tamagochi.domain.battle.BattleSkill skill, String userCode, String image64) {}

    private final ObjectMapper om = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    /** 다녀간 끄적이 id를 만드는 값. 서버가 켜져 있는 동안 같은 사람은 늘 같은 id를 받는다 (유저 번호는 드러나지 않는다) */
    private final long ghostSalt = random.nextLong();
    private final LongSupplier clock;
    private final EventTimes times;
    private final List<PlazaMobs.Spawn> spawns;
    private final boolean chat;
    private Hunts hunts = (userId, enemyKey, questTick, done) -> { /* 지급은 setHunts 로 붙인다 */ };
    private Moderation moderation = new Moderation() {   // 저장은 setModeration 으로 붙인다
        @Override public void block(long userId, String targetCode, boolean on) { }
        @Override public void report(long reporterId, String targetCode, String why, List<String> lines, String roomKey) { }
    };
    private final Map<String, Member> byPeer = new LinkedHashMap<>();
    private final Map<Long, Member> byUser = new LinkedHashMap<>();
    private final Map<String, Room> rooms = new LinkedHashMap<>();   // 방 열쇠 → 방
    private final Deque<Ghost> recent = new ArrayDeque<>();

    /** 시간표는 설정으로 바꿀 수 있다 (로컬에서 짧게 돌려 보려고). 기본은 매 시각 정각부터 10분 */
    @Autowired
    public PlazaService(@Value("${plaza.event.period-ms:3600000}") long period, @Value("${plaza.event.gather-ms:60000}") long gather,
                        @Value("${plaza.event.length-ms:600000}") long length, @Value("${plaza.event.shrink-ms:480000}") long shrink,
                        @Value("${plaza.chat.enabled:true}") boolean chat) {
        this(System::currentTimeMillis, new EventTimes(period, gather, EventTimes.HOURLY.ready(), length, shrink, EventTimes.HOURLY.minScale()), PlazaMobs.load(), chat);
    }

    PlazaService(LongSupplier clock) { this(clock, EventTimes.HOURLY); }

    PlazaService(LongSupplier clock, EventTimes times) { this(clock, times, PlazaMobs.load()); }

    PlazaService(LongSupplier clock, EventTimes times, List<PlazaMobs.Spawn> spawns) { this(clock, times, spawns, true); }

    PlazaService(LongSupplier clock, EventTimes times, List<PlazaMobs.Spawn> spawns, boolean chat) {
        this.clock = clock; this.times = times; this.spawns = spawns; this.chat = chat;
    }

    /**
     * 차단 · 신고를 저장하는 쪽. 광장의 잠금 안에서 불리므로 DB 일은 다른 실행기에 넘기고 바로 돌아온다.
     * 신고의 lines 는 신고당한 사람의 최근 말(오래된 것부터)이다
     */
    public interface Moderation {
        void block(long userId, String targetCode, boolean on);
        void report(long reporterId, String targetCode, String why, List<String> lines, String roomKey);
    }

    /**
     * 방문 한 번을 남기는 쪽 (사용자 수치용). 방에 들어왔던 사람이 나갈 때 불린다. 한 마리도 안 잡았어도 불린다.
     * 광장의 잠금 안에서 불리므로 DB 일은 다른 실행기에 넘기고 바로 돌아온다. kind: 2d | 3d, 시각은 epoch ms
     */
    public interface Visits {
        void left(long userId, String kind, long enteredAt, long leftAt, int kills, int chats);
    }

    private Visits visits = (userId, kind, enteredAt, leftAt, kills, chats) -> { /* 저장은 setVisits 로 붙인다 */ };

    /** 방문 기록을 남기는 쪽을 붙인다 (기본은 아무것도 안 한다) */
    public synchronized void setVisits(Visits v) { this.visits = v; }

    /** 몬스터를 잡았을 때 주는 쪽을 붙인다 (기본은 아무것도 안 한다) */
    public synchronized void setHunts(Hunts h) { this.hunts = h; }

    /** 차단 · 신고를 저장하는 쪽을 붙인다 (기본은 아무것도 안 한다) */
    public synchronized void setModeration(Moderation m) { this.moderation = m; }

    /** 연결되면 부른다. 아직 방에 넣지 않고, 앱이 hello로 그림을 보내면 그때 들여보낸다 */
    public synchronized void connect(Peer peer, Profile profile, String inviteKey) {
        connect(peer, profile, inviteKey, null);
    }

    public synchronized void connect(Peer peer, Profile profile, String inviteKey, String spareImage) {
        connect(peer, profile, inviteKey, spareImage, Extras.NONE);
    }

    /**
     * spareImage: 서버가 보여 줄 그림(profile.imageUrl)을 마련하지 못했고 앱도 그림을 보내지 않았을 때만 쓰는 원본 주소.
     * 원본은 크고(예전 그림은 1MB 안팎) 서버가 방금 받지 못한 주소일 수 있어서 맨 나중에 쓴다
     */
    public synchronized void connect(Peer peer, Profile profile, String inviteKey, String spareImage, Extras extras) {
        // 같은 사람이 다른 기기·탭으로 또 들어오면 먼저 있던 연결을 내보낸다
        Member old = byUser.get(profile.userId());
        if (old != null) { remove(old, true); old.peer.close(); }
        Member m = new Member(peer, profile, newId());
        m.image = profile.imageUrl();
        m.spareImage = spareImage;
        m.inviteKey = inviteKey;
        m.extras = extras == null ? Extras.NONE : extras;
        m.huntOk = m.extras.huntOk();
        m.huntWhy = m.extras.huntWhy();
        if (m.extras.blocked() != null) m.blocked.addAll(m.extras.blocked());
        m.mutedUntil = m.extras.mutedUntil();
        m.room = pickRoom(inviteKey, "2d");   // 2D 방의 자리만 정해 둔다 (hello 뒤에 들어간다). 3D 앱은 hello 에서 방을 다시 고른다
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
            case "sleep" -> sleep(m, msg);
            case "skill" -> skill(m, msg);
            case "atk" -> atk(m, msg);
            case "jump" -> jump(m);
            case "chat" -> chat(m, msg);
            case "block" -> block(m, msg);
            case "report" -> report(m, msg);
            case "hit" -> hit(m, msg);
            case "join" -> join(m);
            case "out" -> out(m);
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
        // 3D 앱: 잡아 둔 2D 자리를 놓고(그 방이 비면 사라진다) 같은 종류의 방을 다시 고른다
        if (msg.path("v").asInt(0) == 3) {
            Room held = m.room;
            m.v3 = true;
            m.room = null;
            if (seats(held) == 0) rooms.remove(held.key);
            m.room = pickRoom(m.inviteKey, "3d");
        }
        String sent = msg.path("image").asText("");
        // 그림 고르는 순서: 서버가 줄인 그림 → 앱이 보낸 작은 그림(주인이 홈에서 보는 그 그림) → 원본 주소
        String from = "서버";
        boolean sentOk = sent.length() <= MAX_IMAGE_CHARS && com.tamagochi.tamagochi.service.storage.PlazaImageCheck.accepts(sent);   // 길이 뒤에만 검사한다. PNG · JPEG · WebP 이고 한 변 1024 이하일 때만
        // 아이폰 앱이 가끔 투명한 빈 그림을 만들어 보낸다. 그대로 쓰면 모두의 화면에 이름표만 떠다닌다
        boolean sentBlank = sentOk && com.tamagochi.tamagochi.service.storage.PetThumbs.blank(sent);
        // 3D 방에 보이는 그림: 64×64 그림 → 앱이 보낸 그림. 둘 다 없으면 2D 와 같이 고른 m.image 를 쓴다 (m.image 는 2D 규칙 그대로라 다녀간 끄적이로 2D 방에 가도 달라지지 않는다)
        if (m.v3) m.image64 = m.extras.image64() != null ? m.extras.image64() : (sentOk && !sentBlank ? sent : null);
        if (m.image == null && sentOk && !sentBlank) { m.image = sent; from = "앱"; }
        if (m.image == null && m.spareImage != null) { m.image = m.spareImage; from = "원본 주소"; }
        // 이름표만 떠다니는 끄적이가 생기면 이 기록으로 어떤 그림이 나갔는지 찾는다
        log.info("광장 입장 - user={}, pet={}, 그림={}({}자), 앱이 보낸 그림={}자", m.profile.userId(), m.profile.publicId(), m.image == null ? "없음" : from,
                m.image == null ? 0 : m.image.length(), sent.length() + (sentBlank ? "(빈 그림)" : "") + (!sentOk && !sent.isEmpty() ? "(받지 않음: 종류 · 크기 · 길이 규칙 밖)" : ""));
        if (m.image == null) {
            m.peer.send("{\"t\":\"error\",\"message\":\"끄적이 그림을 불러오지 못했어요\"}");
            remove(m, false);
            m.peer.close();
            return;
        }
        if ("left".equals(msg.path("facing").asText())) m.facing = "left";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (m.v3) {   // 3D 방은 서쪽 큰길 위에서 시작한다. 세상 단위 x −24..−17.5, z −2.4..0.2. 앱 src/plaza3d/rules.ts 의 START 와 같아야 한다
            m.x = 167 + r.nextInt(91);
            m.y = 132 + r.nextInt(86);
        } else {
            m.x = 150 + r.nextInt(700);
            m.y = 450 + r.nextInt(450);
        }
        Room room = m.room;
        if (!rooms.containsKey(room.key)) rooms.put(room.key, room);   // 정해 둔 방이 그사이 비어 사라졌으면 다시 연다
        // gone: 이 사람이 다녀간 끄적이로 화면에 남아 있으면 지우라고 알린다 (같은 끄적이가 둘로 보이지 않게)
        ObjectNode enter = event("enter").put("gone", ghostId(m.profile.userId()));
        enter.set("m", json(m));
        broadcast(room, enter, null);
        room.members.put(m.id, m);
        m.enteredAt = clock.getAsLong();

        ObjectNode out = event("room");
        out.put("no", room.no).put("key", room.key).put("cap", CAP).put("me", m.id);
        ArrayNode members = out.putArray("members");
        room.members.values().forEach(o -> members.add(json(o)));
        ArrayNode ghosts = out.putArray("ghosts");
        ghostsFor(room).forEach(g -> ghosts.add(json(g, room.is3d())));
        out.set("ev", eventJson(room, clock.getAsLong()));
        if (room.is3d()) {
            out.put("v", 3).put("now", clock.getAsLong()).put("chat", chat);
            ArrayNode blocked = out.putArray("blocked");
            m.blocked.forEach(blocked::add);
            ArrayNode mobs = out.putArray("mobs");
            for (int i = 0; i < room.mobs.length; i++) mobs.add(mobJson(i, room.mobs[i]));
            ObjectNode hunt = out.putObject("hunt").put("ok", m.huntOk);
            if (!m.huntOk && m.huntWhy != null) hunt.put("why", m.huntWhy);
        }
        m.peer.send(out.toString());
    }

    /** 초대 열쇠가 살아 있고 같은 종류면 그 방, 아니면 자리 남은 방 가운데 가장 북적이는 방, 없으면 새 방 */
    private Room pickRoom(String inviteKey, String kind) {
        if (inviteKey != null) {
            Room invited = rooms.get(inviteKey.toUpperCase());
            if (invited != null && invited.kind.equals(kind) && seats(invited) < CAP + FRIEND_EXTRA) return invited;
        }
        return rooms.values().stream()
                .filter(r -> r.kind.equals(kind) && seats(r) < CAP)
                .max(Comparator.comparingInt(this::seats))
                .orElseGet(() -> {
                    Room r = new Room(freeRoomNo(), newKey(), kind);
                    if (r.is3d()) r.mobs = newMobs();
                    rooms.put(r.key, r);
                    return r;
                });
    }

    private Mob[] newMobs() {
        Mob[] mobs = new Mob[spawns.size()];
        for (int i = 0; i < mobs.length; i++) mobs[i] = new Mob(spawns.get(i).enemy(), PlazaMobs.kind(spawns.get(i).enemy()).hp());
        return mobs;
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
        m.sleeping = false;   // 걸으면 깬다
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
        String json = out.toString();
        for (Member o : m.room.members.values()) {
            if (o == m) continue;
            // 3D 방: 내가 차단한 사람이 나에게 하는 인사는 받지 않는다
            if (m.room.is3d() && o.id.equals(to) && blocks(o, m)) continue;
            o.peer.send(json);
        }
    }

    /** 잠자기·깨기. 인사와 같은 간격 제한을 쓴다. 상태가 그대로면 알리지 않는다 */
    private void sleep(Member m, JsonNode msg) {
        if (!inRoom(m)) return;
        boolean on = msg.path("on").asBoolean(false);
        if (on == m.sleeping) return;
        long now = clock.getAsLong();
        if (now - m.lastEmote < EMOTE_GAP_MS) return;
        m.lastEmote = now;
        m.sleeping = on;
        broadcast(m.room, event("sleep").put("id", m.id).put("on", on), m);
    }

    /** 기술 쓰기. 방 모두(쓴 사람 포함)에게 보낸다. 정각 밀어내기 동안, 아직 남아 있는 참가자만 쓸 수 있다 (혼자 연습이면 누구나) */
    /** 알은 구경만 한다 (밀어내기에 끼지 않고, 기술을 쓰지도 맞지도 않는다) */
    private static boolean spectator(Member m) {
        return "EGG".equals(m.stage);
    }

    private void skill(Member m, JsonNode msg) {
        if (!inRoom(m) || spectator(m)) return;
        Match match = fighting(m.room);
        if (match == null || !(match.solo || match.alive.contains(m.id))) return;
        long now = clock.getAsLong();
        if (now - m.lastSkill < SKILL_GAP_MS) return;
        m.lastSkill = now;
        m.x = clamp(msg.path("x").asInt(m.x), 20, 980);
        m.y = clamp(msg.path("y").asInt(m.y), 0, 1000);
        ObjectNode out = event("skill").put("id", m.id).put("x", m.x).put("y", m.y)
                .put("tx", clamp(msg.path("tx").asInt(m.x), 0, 1000)).put("ty", clamp(msg.path("ty").asInt(m.y), 0, 1000));
        int d = msg.path("d").asInt(0);
        if (d != 0) out.put("d", d > 0 ? 1 : -1);
        m.sleeping = false;   // 기술을 쓰면 깬다
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
        if (spectator(m) && msg.path("id").asText("").isEmpty()) return;   // 알은 맞지 않는다
        Match match = fighting(m.room);
        if (match == null) return;
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
        // 탈락했거나 참가하지 않은 끄적이는 맞지 않는다 (다녀간 끄적이는 언제나 맞는다)
        if (!victim.startsWith(GHOST_PREFIX) && !match.solo && !match.alive.contains(victim)) return;
        Integer level = levelOf(m.room, victim);
        if (level == null) return;
        if (caster.castHits.size() >= MAX_HITS_PER_CAST || !caster.castHits.add(victim)) return;
        Member hitMember = m.room.members.get(victim);
        if (hitMember != null) hitMember.sleeping = false;   // 맞으면 깬다
        broadcast(m.room, event("knock").put("id", victim).put("by", caster.id)
                .put("kb", knockback(caster.level, level))
                .put("s", ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE)), null);
    }

    // ── 사냥 (3D 방)

    /** 휘두르기. 모두에게 모습이 보이고, 사냥할 수 있는 사람이면 가까운 몬스터(한 번에 4마리까지)가 맞는다 */
    private void atk(Member m, JsonNode msg) {
        if (!inRoom(m) || !m.room.is3d() || spectator(m)) return;
        String d = msg.path("d").asText("");
        if (!ATK_DIRS.contains(d)) return;
        long now = clock.getAsLong();
        if (now - m.lastAtk < ATK_GAP_MS) return;
        m.lastAtk = now;
        m.sleeping = false;   // 휘두르면 깬다
        broadcast(m.room, event("atk").put("id", m.id).put("d", d), m);
        if (!m.huntOk) return;
        Set<Integer> seen = new HashSet<>();
        int looked = 0;
        for (JsonNode e : msg.path("m")) {
            if (looked++ >= MAX_MOBS_PER_ATK) break;
            if (!e.isInt()) continue;
            int i = e.asInt();
            if (i < 0 || i >= m.room.mobs.length || !seen.add(i)) continue;
            hitMob(m, i, now);
        }
    }

    private void hitMob(Member m, int i, long now) {
        Room room = m.room;
        Mob mob = room.mobs[i];
        if (mob.dead() || !PlazaMobs.near(m.x, m.y, spawns.get(i))) return;
        mob.hp = Math.max(0, mob.hp - PlazaMobs.damage(m.level, PlazaMobs.kind(mob.enemy)));
        mob.hitters.add(m.id);
        ObjectNode ev = event("mob").put("i", i).put("hp", mob.hp).put("by", m.id);
        if (mob.dead()) {
            mob.respawnAt = now + RESPAWN_MS;
            ev.put("at", mob.respawnAt);
        }
        broadcast(room, ev, null);
        if (!mob.dead()) return;
        // 때린 사람 모두가 한 마리 몫을 받는다 (때린 뒤 나간 사람은 뺀다)
        // 목록은 복사해 비운 뒤 돈다: 한 사람의 지급이 던져도 다음 사람은 받고, 옛 목록이 다음 판에 남지 않는다
        List<String> paid = new ArrayList<>(mob.hitters);
        mob.hitters.clear();
        for (String id : paid) {
            Member h = room.members.get(id);
            if (h == null) continue;
            h.kills++;
            try {
                hunts.kill(h.profile.userId(), mob.enemy, h.kills % QUEST_EVERY == 0, r -> onLoot(id, i, r));
            } catch (RuntimeException e) {
                log.warn("광장 사냥 지급을 넣지 못함 - user: {}, enemy: {}", h.profile.userId(), mob.enemy, e);
            }
        }
    }

    /** 지급 결과가 나왔다. 나간 사람 것은 버린다. 못 한다는 결과면 그 사람은 더 못 때린다 */
    public synchronized void onLoot(String memberId, int mobIndex, HuntResult r) {
        Member m = byPeer.values().stream().filter(o -> o.id.equals(memberId)).findFirst().orElse(null);
        if (m == null || !inRoom(m)) return;
        if (r.ok() && r.level() > 0) m.level = r.level();
        if (r.ok() && r.stage() != null) m.stage = r.stage();
        if (!r.ok() || r.why() != null) {   // 이번 것은 받았어도 why 가 있으면 다음부터 못 한다
            m.huntOk = false;
            m.huntWhy = r.why();
        }
        ObjectNode out = event("loot").put("i", mobIndex).put("exp", r.exp()).put("ink", r.ink()).put("level", r.level())
                .put("stage", r.stage()).put("lvUp", r.lvUp()).put("hunger", r.satiety()).put("power", r.power()).put("ok", r.ok());
        if (r.drop() != null) out.set("drop", om.valueToTree(r.drop()));
        if (r.why() != null) out.put("why", r.why());
        m.peer.send(out.toString());
    }

    // ── 채팅 · 차단 · 신고 (3D 방)

    /** o 가 m 을 차단했는가 */
    private static boolean blocks(Member o, Member m) {
        return m.extras.userCode() != null && o.blocked.contains(m.extras.userCode());
    }

    /**
     * 말하기. 막히면 보낸 사람에게만 chatNo{why}: off(꺼짐) · muted(말 못 하게 됨) · fast(너무 빠름) · word(금칙어).
     * 알이거나 말이 비면 조용히 버린다. 보낼 때는 보낸 사람 포함 방 모두에게 가되, 보낸 사람을 차단한 사람은 건너뛴다.
     */
    private void chat(Member m, JsonNode msg) {
        if (!inRoom(m) || !m.room.is3d() || spectator(m)) return;
        if (!chat) { chatNo(m, "off"); return; }
        long now = clock.getAsLong();
        if (m.mutedUntil > now) { chatNo(m, "muted"); return; }
        while (!m.chatTimes.isEmpty() && now - m.chatTimes.peekFirst() >= CHAT_WINDOW_MS) m.chatTimes.pollFirst();
        if (now - m.lastChat < CHAT_GAP_MS || m.chatTimes.size() >= CHAT_PER_WINDOW) { chatNo(m, "fast"); return; }   // 다듬기 전에 먼저 거른다
        String raw = msg.path("text").asText(null);
        if (raw != null && raw.codePointCount(0, raw.length()) > CHAT_RAW_MAX) raw = raw.substring(0, raw.offsetByCodePoints(0, CHAT_RAW_MAX));   // 아주 긴 글은 다듬기 전에 자른다
        String text = ChatRules.clean(raw);
        if (text == null) return;
        m.lastChat = now;
        m.chatTimes.addLast(now);   // 금칙어로 막힌 말도 센다 (걸러지는 말을 계속 시험해 보는 것을 막는다)
        if (ChatRules.banned(text)) { chatNo(m, "word"); return; }
        m.sleeping = false;   // 말하면 깬다
        m.chats++;
        m.lines.addLast(text);
        while (m.lines.size() > REPORT_LINES) m.lines.pollFirst();
        ObjectNode out = event("chat").put("id", m.id);
        if (m.extras.userCode() != null) out.put("u", m.extras.userCode());
        out.put("text", text);
        String json = out.toString();
        for (Member o : m.room.members.values()) if (o == m || !blocks(o, m)) o.peer.send(json);
    }

    private void chatNo(Member m, String why) {
        m.peer.send(event("chatNo").put("why", why).toString());
    }

    /** 차단 · 해제. 자기 자신은 무시. 저장은 Moderation 에 넘긴다 */
    private void block(Member m, JsonNode msg) {
        if (!inRoom(m) || !m.room.is3d()) return;
        String code = msg.path("u").asText("");
        if (code.isEmpty() || code.length() > 16 || code.equals(m.extras.userCode())) return;
        boolean on = msg.path("on").asBoolean(false);
        if (on == m.blocked.contains(code)) return;   // 이미 그 상태면 저장을 부르지 않는다
        long now = clock.getAsLong();
        if (now - m.lastBlock < BLOCK_GAP_MS) return;
        if (on) {
            if (m.blocked.size() >= BLOCK_MAX && !m.blocked.contains(code)) return;
            m.blocked.add(code);
        } else {
            m.blocked.remove(code);
        }
        m.lastBlock = now;
        try {
            moderation.block(m.profile.userId(), code, on);
        } catch (RuntimeException e) {
            log.warn("광장 차단 저장을 넣지 못함 - user: {}", m.profile.userId(), e);
        }
    }

    /** 신고. 10초에 한 번. 신고당한 사람이 방에 있으면 그 사람의 최근 말을 붙인다 */
    private void report(Member m, JsonNode msg) {
        if (!inRoom(m) || !m.room.is3d()) return;
        String code = msg.path("u").asText("");
        if (code.isEmpty() || code.length() > 16 || code.equals(m.extras.userCode())) return;
        long now = clock.getAsLong();
        if (now - m.lastReport < REPORT_GAP_MS) return;
        m.lastReport = now;
        String why = msg.path("why").asText("");
        if (!REPORT_WHYS.contains(why)) why = "etc";
        List<String> lines = m.room.members.values().stream().filter(o -> code.equals(o.extras.userCode())).findFirst()
                .map(o -> List.copyOf(o.lines)).orElse(List.of());
        try {
            moderation.report(m.profile.userId(), code, why, lines, m.room.key);
        } catch (RuntimeException e) {
            log.warn("광장 신고 저장을 넣지 못함 - user: {}", m.profile.userId(), e);
        }
    }

    private void jump(Member m) {
        if (!inRoom(m) || !m.room.is3d()) return;
        long now = clock.getAsLong();
        if (now - m.lastJump < JUMP_GAP_MS) return;
        m.lastJump = now;
        broadcast(m.room, event("jump").put("id", m.id), m);
    }

    /** 죽은 몬스터 가운데 때가 된 것을 되살린다 */
    private void respawn(Room room, long now) {
        for (int i = 0; i < room.mobs.length; i++) {
            Mob mob = room.mobs[i];
            if (!mob.dead() || mob.respawnAt > now) continue;
            mob.hp = mob.max;
            broadcast(room, event("mob").put("i", i).put("hp", mob.hp).put("e", mob.enemy).put("max", mob.max), null);
        }
    }

    private ObjectNode mobJson(int i, Mob mob) {
        ObjectNode n = om.createObjectNode().put("i", i).put("e", mob.enemy).put("hp", mob.hp).put("max", mob.max);
        if (mob.dead()) n.put("at", mob.respawnAt);
        return n;
    }

    /** 레벨 차이에 따른 튕김 세기 (같은 레벨 1.0). 높은 레벨이 낮은 레벨을 치면 멀리, 반대면 조금만 밀린다 */
    static double knockback(int attackerLevel, int targetLevel) {
        double kb = 1 + (attackerLevel - targetLevel) * KB_PER_LEVEL;
        return Math.round(Math.max(KB_MIN, Math.min(KB_MAX, kb)) * 100) / 100.0;
    }

    /** 방 사람이거나 다녀간 끄적이의 레벨 (모르는 상대면 null) */
    private Integer levelOf(Room room, String id) {
        Member member = room.members.get(id);
        if (member != null) return member.level;
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
            // 밀어내는 중에 나가면 탈락한 것으로 친다
            Match match = fighting(room);
            if (match != null && match.alive.remove(m.id)) { match.lastOut = m.id; settle(room, match); }
            try {
                visits.left(m.profile.userId(), room.kind, m.enteredAt, clock.getAsLong(), m.kills, m.chats);
            } catch (RuntimeException e) {
                log.warn("광장 방문 기록을 넣지 못함 - user: {}", m.profile.userId(), e);
            }
        }
        if (seats(room) == 0) rooms.remove(room.key);
    }

    // ── 정각 밀어내기

    /** 시간표를 따라 방마다 단계를 넘긴다. 0.5초마다 불린다 */
    @Scheduled(fixedRate = 500)
    public synchronized void tick() {
        long now = clock.getAsLong(), no = now / times.period(), t = now % times.period();
        for (Room room : new ArrayList<>(rooms.values())) {
            Match match = room.match;
            if (match == null || match.no != no) {
                // 새 정각: 모이기부터. 모이기가 지난 뒤에 생긴 방은 참가를 받을 수 없으니 혼자 연습으로 연다
                if (t >= times.length()) { room.match = null; continue; }
                match = room.match = new Match(no, t < times.gather() ? "gather" : "fight");
                match.solo = !"gather".equals(match.phase);
                broadcast(room, eventJson(room, now), null);
                continue;
            }
            switch (match.phase) {
                case "gather" -> {
                    if (t >= times.gather()) { match.phase = "ready"; broadcast(room, eventJson(room, now), null); }
                }
                case "ready" -> {
                    if (t >= times.gather() + times.ready()) {
                        match.players.removeIf(id -> !room.members.containsKey(id));   // 그사이 나간 사람
                        match.alive.addAll(match.players);
                        match.solo = match.players.size() < 2;
                        match.phase = "fight";
                        broadcast(room, eventJson(room, now), null);
                    }
                }
                case "fight" -> {
                    if (t >= times.length()) finish(room, match);
                }
                default -> { /* done: 다음 정각까지 그대로 */ }
            }
        }
        for (Room room : rooms.values()) if (room.is3d()) respawn(room, now);
    }

    /** 참가: 준비 단계에 놀이터 안에 있는 끄적이의 앱이 보낸다 */
    private void join(Member m) {
        if (!inRoom(m) || spectator(m)) return;
        Match match = current(m.room);
        if (match == null || !"ready".equals(match.phase)) return;
        match.players.add(m.id);
        match.names.put(m.id, m.profile.nickname());
    }

    /** 탈락: 놀이터 밖으로 나간 끄적이의 앱이 보낸다 */
    private void out(Member m) {
        if (!inRoom(m)) return;
        Match match = fighting(m.room);
        if (match == null || match.solo || !match.alive.remove(m.id)) return;
        match.lastOut = m.id;
        broadcast(m.room, event("out").put("id", m.id).put("left", match.alive.size()), null);
        settle(m.room, match);
    }

    /** 한 마리만 남았으면 끝낸다 */
    private void settle(Room room, Match match) {
        if (!match.solo && match.alive.size() <= 1) finish(room, match);
    }

    /** 끝: 남은 끄적이가 우승. 아무도 안 남았으면(둘이 함께 나감) 마지막에 나간 끄적이가 우승 */
    private void finish(Room room, Match match) {
        match.phase = "done";
        if (!match.solo) {
            match.winners.addAll(match.alive);
            if (match.winners.isEmpty() && match.lastOut != null) match.winners.add(match.lastOut);
        }
        broadcast(room, eventJson(room, clock.getAsLong()), null);
    }

    /** 이번 정각의 판 (없으면 null) */
    private Match current(Room room) {
        Match match = room.match;
        return match != null && match.no == clock.getAsLong() / times.period() ? match : null;
    }

    /** 지금 밀어내는 중인 판 (아니면 null) */
    private Match fighting(Room room) {
        Match match = current(room);
        return match != null && "fight".equals(match.phase) ? match : null;
    }

    /** 밀어내기 상태. 앱은 now로 서버 시각과의 차이를 알아내 남은 시간과 놀이터 크기를 스스로 계산한다 */
    private ObjectNode eventJson(Room room, long now) {
        long start = now - now % times.period();
        Match match = room.match != null && room.match.no == now / times.period() ? room.match : null;
        ObjectNode n = event("ev").put("phase", match == null ? "idle" : match.phase)
                .put("now", now).put("start", start).put("next", start + times.period())
                .put("gather", times.gather()).put("ready", times.ready()).put("length", times.length())
                .put("shrink", times.shrink()).put("min", times.minScale());
        if (match == null) return n;
        n.put("solo", match.solo);
        ArrayNode players = n.putArray("players"), alive = n.putArray("alive"), winners = n.putArray("winners");
        match.players.forEach(players::add);
        match.alive.forEach(alive::add);
        for (String id : match.winners) winners.addObject().put("id", id).put("name", match.names.getOrDefault(id, ""));
        return n;
    }

    private void remember(Member m) {
        recent.removeIf(g -> g.userId().equals(m.profile.userId()));
        recent.addFirst(new Ghost(m.profile.userId(), ghostId(m.profile.userId()), m.profile.nickname(), m.profile.petName(), m.level, m.stage, m.image, m.facing, m.profile.skill(), m.extras.userCode(), m.image64));
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
                .put("level", m.level).put("stage", m.stage)
                .put("image", m.v3 && m.image64 != null ? m.image64 : m.image).put("facing", m.facing).put("x", m.x).put("y", m.y);
        if (m.profile.skill() != null) n.set("skill", om.valueToTree(m.profile.skill()));
        if (m.sleeping) n.put("sleep", true);
        if (m.v3 && m.extras.userCode() != null) n.put("u", m.extras.userCode());
        return n;
    }

    private ObjectNode json(Ghost g, boolean in3d) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ObjectNode n = om.createObjectNode();
        n.put("id", g.id()).put("nickname", g.nickname()).put("petName", g.petName())
                .put("level", g.level()).put("stage", g.stage())
                .put("image", g.image()).put("facing", g.facing()).put("x", 80 + r.nextInt(840)).put("y", 420 + r.nextInt(520));
        if (g.skill() != null) n.set("skill", om.valueToTree(g.skill()));
        if (in3d) {   // 3D 방에서만: 64칸 그림이 있으면 그것으로, 유저 코드와 함께
            if (g.image64() != null) n.put("image", g.image64());
            if (g.userCode() != null) n.put("u", g.userCode());
        }
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
