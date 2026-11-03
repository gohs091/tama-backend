package com.tamagochi.tamagochi.service.user;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * 닉네임 규칙과 처음 주는 무작위 닉네임, 바뀌지 않는 유저 코드.
 * 닉네임은 다른 사람과 같아도 된다 (유명한 밈을 여러 명이 쓸 수 있게). 사람을 찾을 때는 유저 코드를 쓴다.
 */
public final class NicknameRules {

    private NicknameRules() {
    }

    public static final int MAX_LENGTH = 12;  // 글자 수 (한글 한 글자 = 1)

    /** 운영자로 착각할 수 있는 이름은 막는다 */
    private static final List<String> RESERVED = List.of("운영자", "관리자", "공식", "토스", "toss", "admin", "끄적이팀");

    private static final List<String> ADJECTIVES = List.of(
            "용감한", "졸린", "배고픈", "수줍은", "씩씩한", "느긋한", "엉뚱한", "반짝이는", "꼼꼼한", "말랑한",
            "까칠한", "다정한", "재빠른", "신난", "조용한", "덜렁대는", "당당한", "포근한", "날쌘", "호기심많은",
            "새침한", "든든한", "명랑한", "꾸벅이는", "부지런한", "장난꾸러기", "똑똑한", "통통한", "의젓한", "수상한");

    private static final List<String> NOUNS = List.of(
            "연필", "크레용", "지우개", "물감", "붓", "스케치북", "색연필", "도화지", "낙서", "형광펜",
            "만년필", "파스텔", "마커", "자", "풀", "가위", "필통", "잉크", "물통", "팔레트");

    /** 유저 코드 글자: 숫자 + 헷갈리는 글자(I, L, O, U)를 뺀 영문 대문자 (32자) */
    static final String CODE_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int CODE_LENGTH = 8;

    private static final RandomGenerator RANDOM = new SecureRandom();

    /** "용감한 연필 2841". 글자 수 한도를 넘는 조합(긴 형용사 + 긴 명사)은 다시 뽑는다 */
    public static String randomNickname() {
        while (true) {
            String n = ADJECTIVES.get(RANDOM.nextInt(ADJECTIVES.size())) + " "
                    + NOUNS.get(RANDOM.nextInt(NOUNS.size())) + " "
                    + String.format("%04d", RANDOM.nextInt(10000));
            if (n.codePointCount(0, n.length()) <= MAX_LENGTH) return n;
        }
    }

    public static String randomUserCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    /** 화면용: K7Q29XMA → K7Q2-9XMA */
    public static String formatCode(String code) {
        return code == null || code.length() != CODE_LENGTH ? code : code.substring(0, 4) + "-" + code.substring(4);
    }

    /** 사람이 입력한 코드를 저장 형식으로: 공백·하이픈 제거, 대문자, 헷갈리는 글자 보정(O→0, I/L→1) */
    public static String normalizeCode(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT).replace('O', '0').replace('I', '1').replace('L', '1');
        return s.length() == CODE_LENGTH && s.chars().allMatch(c -> CODE_CHARS.indexOf(c) >= 0) ? s : null;
    }

    /** 처음 로그인 때 넣던 값(toss_...)이나 빈 값이면 아직 닉네임이 없는 것으로 본다 */
    public static boolean needsNickname(String nickname, String tossUserId) {
        return nickname == null || nickname.isBlank() || nickname.equals(tossUserId) || nickname.startsWith("toss_");
    }

    /**
     * 유저가 입력한 닉네임을 정리하고 검사한다. 통과하면 정리된 닉네임, 아니면 IllegalArgumentException(화면에 보일 문장).
     */
    public static String clean(String raw) {
        if (raw == null) throw new IllegalArgumentException("닉네임을 입력해 주세요.");
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
        if (s.isEmpty()) throw new IllegalArgumentException("닉네임을 입력해 주세요.");
        if (s.codePointCount(0, s.length()) > MAX_LENGTH) throw new IllegalArgumentException("닉네임은 " + MAX_LENGTH + "글자까지 쓸 수 있어요.");
        if (s.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>' || c == '​'))
            throw new IllegalArgumentException("닉네임에 쓸 수 없는 글자가 있어요.");
        if (s.startsWith("toss_")) throw new IllegalArgumentException("이 이름은 쓸 수 없어요.");
        String lower = s.toLowerCase(Locale.ROOT).replace(" ", "");
        if (RESERVED.stream().anyMatch(r -> lower.contains(r.toLowerCase(Locale.ROOT))))
            throw new IllegalArgumentException("운영자로 오해할 수 있는 이름은 쓸 수 없어요.");
        return s;
    }
}
