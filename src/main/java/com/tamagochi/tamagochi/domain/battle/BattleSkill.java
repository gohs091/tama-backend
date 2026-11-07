package com.tamagochi.tamagochi.domain.battle;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 배틀 기술 (앱의 입자 연출로 그린다). 스파링 연타 중에 서로 주고받고, 이긴 쪽이 마무리로 한 번 더 쓴다.
 * 틀(template)은 앱에 있는 18가지 연출 중 하나, 색 3개(주색·보조색·밝은 강조색), 입자 모양, 이름.
 * 승패에는 영향이 없다 (연출만).
 */
public record BattleSkill(String template, String name, List<String> colors, String particle) {

    /** 앱 연출 틀. 바꾸면 앱(src/effects/skillFx.ts)도 같이 바꾼다 */
    public static final Set<String> TEMPLATES = Set.of(
            "dash", "projectile", "slash", "barrage", "vortex", "magic", "rain", "shockwave", "beam",
            "bind", "cloud", "chomp", "spikes", "burst", "soundwave", "meteor", "charm", "lightning");
    public static final Set<String> PARTICLES = Set.of("circle", "square", "spark", "star", "drop", "leaf", "feather", "smoke", "heart");
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final List<String> FALLBACK_COLORS = List.of("#ff9f1c", "#3a2a20", "#fff3d6");

    /** AI가 준 값을 다듬는다: 모르는 틀·입자는 기본값, 잘못된 색은 기본 색, 이름은 20자까지 */
    public static BattleSkill sanitize(String template, String name, List<String> colors, String particle) {
        String t = template != null && TEMPLATES.contains(template.trim().toLowerCase()) ? template.trim().toLowerCase() : "projectile";
        String p = particle != null && PARTICLES.contains(particle.trim().toLowerCase()) ? particle.trim().toLowerCase() : "circle";
        List<String> c = colors == null ? List.of() : colors.stream().filter(v -> v != null && HEX.matcher(v.trim()).matches()).map(String::trim).toList();
        if (c.size() < 3) c = FALLBACK_COLORS;
        String n = name == null || name.isBlank() ? "필살 공격" : name.trim();
        if (n.length() > 20) n = n.substring(0, 20);
        return new BattleSkill(t, n, List.copyOf(c.subList(0, 3)), p);
    }

    public String colorsCsv() {
        return String.join(",", colors);
    }

    public static List<String> parseColors(String csv) {
        return csv == null || csv.isBlank() ? FALLBACK_COLORS : Arrays.stream(csv.split(",")).map(String::trim).toList();
    }
}
