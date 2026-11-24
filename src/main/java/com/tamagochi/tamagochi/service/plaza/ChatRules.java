package com.tamagochi.tamagochi.service.plaza;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * 광장 채팅 글 다듬기와 금칙어. 전 연령이 쓰는 앱이라 거르기부터 같이 만든다.
 * 금칙어 목록은 plaza/banned-words.txt (한 줄에 하나, # 로 시작하면 주석, 세 글자 이상만).
 */
public final class ChatRules {

    private ChatRules() {}

    public static final int MAX_CHARS = 30;
    /** 이만큼 이어진 숫자는 전화번호일 수 있어 가린다 */
    private static final int MASK_DIGITS = 7;

    private static List<String> words;

    /** 보내도 되는 모양으로 다듬는다. 말이 남지 않으면 null */
    public static String clean(String raw) {
        if (raw == null) return null;
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC).replaceAll("\\s+", " ");
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(c -> {
            int type = Character.getType(c);
            // 제어 문자 · 폭 없는 문자(서식 문자) · 꺾쇠는 지운다
            if (Character.isISOControl(c) || type == Character.FORMAT || c == '<' || c == '>') return;
            sb.appendCodePoint(c);
        });
        s = sb.toString().strip().replaceAll("\\s+", " ");
        s = maskDigits(s);
        if (s.codePointCount(0, s.length()) > MAX_CHARS) s = s.substring(0, s.offsetByCodePoints(0, MAX_CHARS)).strip();
        return s.isEmpty() ? null : s;
    }

    /** 숫자가 7자리 넘게 이어지면 자리마다 * 로 바꾼다 (글자 수는 그대로) */
    private static String maskDigits(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{" + MASK_DIGITS + ",}").matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, "*".repeat(m.group().length()));
        m.appendTail(out);
        return out.toString();
    }

    /** 한글 · 영문만 남기고 소문자로 맞춘 글에 금칙어가 들어 있는가 ("시1발" 같은 끊어 쓰기도 잡힌다) */
    public static boolean banned(String text) {
        String t = squash(text);
        if (t.isEmpty()) return false;
        for (String w : words()) if (t.contains(w)) return true;
        return false;
    }

    private static String squash(String text) {
        if (text == null) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '가' && c <= '힣') || (c >= 'a' && c <= 'z')) sb.append(c);
        }
        return sb.toString();
    }

    private static synchronized List<String> words() {
        if (words == null) words = load();
        return words;
    }

    private static List<String> load() {
        try (InputStream in = ChatRules.class.getResourceAsStream("/plaza/banned-words.txt")) {
            if (in == null) throw new IllegalStateException("plaza/banned-words.txt 가 없어요");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return r.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .map(ChatRules::squash).filter(l -> l.length() >= 3).distinct().toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("plaza/banned-words.txt 를 읽지 못했어요", e);
        }
    }
}
