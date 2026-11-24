package com.tamagochi.tamagochi.service.plaza;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 광장 채팅 글 다듬기와 금칙어. 전 연령이 쓰는 앱이라 거르기부터 같이 만든다.
 * 금칙어 목록은 plaza/banned-words.txt (한 줄에 하나, # 로 시작하면 주석, 한글 두 글자 이하 · 영문 세 글자 이하는 짧은 낱말로 따로 찾는다).
 */
public final class ChatRules {

    private ChatRules() {}

    public static final int MAX_CHARS = 30;
    /** 이만큼 이어진 숫자는 전화번호일 수 있어 가린다 */
    private static final int MASK_DIGITS = 7;

    private static Lists lists;

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

    /**
     * 금칙어가 들어 있는가. 두 갈래로 찾는다.
     * 긴 낱말(한글 세 글자 이상 · 영문 네 글자 이상): 한글 · 영문만 남겨 다 붙인 글에서 찾는다 ("미1친 놈" 도 잡힌다).
     * 짧은 낱말: 다 붙이면 "다시 발표" 가 "시발" 로 걸리므로 낱말 단위로 찾는다. 공백으로 나눈 조각마다 한글 · 영문만 남기고,
     * 한 글자짜리 조각이 이어지면 붙인 뒤("시 발", "시 1 발" → "시발") 그 낱말 안에 짧은 금칙어가 있는지 본다.
     */
    public static boolean banned(String text) {
        if (text == null) return false;
        Lists l = lists();
        String t = squash(text);
        for (String w : l.longWords()) if (t.contains(w)) return true;
        for (String token : tokens(text)) for (String w : l.shortWords()) if (token.contains(w)) return true;
        return false;
    }

    private record Lists(List<String> longWords, List<String> shortWords) {}

    private static String squash(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '가' && c <= '힣') || (c >= 'a' && c <= 'z')) sb.append(c);
        }
        return sb.toString();
    }

    /** 공백으로 나눠 조각마다 squash 하고(빈 조각은 버린다), 한 글자짜리 조각이 이어지면 붙인다 */
    private static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();   // 이어지는 한 글자 조각
        for (String piece : text.split("\s+")) {
            String p = squash(piece);
            if (p.isEmpty()) continue;
            if (p.length() == 1) { run.append(p); continue; }
            if (run.length() > 0) { out.add(run.toString()); run.setLength(0); }
            out.add(p);
        }
        if (run.length() > 0) out.add(run.toString());
        return out;
    }

    /** 짧은 낱말: 한글이 들어 있으면 두 글자 이하, 영문뿐이면 세 글자 이하 */
    private static boolean isShort(String w) {
        boolean hangul = w.chars().anyMatch(c -> c >= '가');
        return w.length() <= (hangul ? 2 : 3);
    }

    private static synchronized Lists lists() {
        if (lists == null) lists = load();
        return lists;
    }

    private static Lists load() {
        try (InputStream in = ChatRules.class.getResourceAsStream("/plaza/banned-words.txt")) {
            if (in == null) throw new IllegalStateException("plaza/banned-words.txt 가 없어요");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> all = r.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .map(ChatRules::squash).filter(l -> !l.isEmpty()).distinct().toList();
                return new Lists(all.stream().filter(w -> !isShort(w)).toList(), all.stream().filter(ChatRules::isShort).toList());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("plaza/banned-words.txt 를 읽지 못했어요", e);
        }
    }
}
