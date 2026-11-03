package com.tamagochi.tamagochi.service.tamagotchi;

import java.text.Normalizer;

/** 끄적이 이름 규칙. 배틀·스파링 이름표에 보이므로 짧게, 다른 끄적이와 같아도 된다 */
public final class PetNameRules {

    private PetNameRules() {
    }

    public static final int MAX_LENGTH = 10;

    /** 정리된 이름을 돌려주고, 쓸 수 없으면 IllegalArgumentException(화면에 보일 문장) */
    public static String clean(String raw) {
        if (raw == null) throw new IllegalArgumentException("이름을 입력해 주세요.");
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
        if (s.isEmpty()) throw new IllegalArgumentException("이름을 입력해 주세요.");
        if (s.codePointCount(0, s.length()) > MAX_LENGTH) throw new IllegalArgumentException("이름은 " + MAX_LENGTH + "글자까지 지을 수 있어요.");
        if (s.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>' || c == '​'))
            throw new IllegalArgumentException("이름에 쓸 수 없는 글자가 있어요.");
        return s;
    }
}
