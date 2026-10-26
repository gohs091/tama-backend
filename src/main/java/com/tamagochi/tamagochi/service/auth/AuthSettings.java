package com.tamagochi.tamagochi.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로그인(토큰) 사용 여부. 로컬 개발은 끄고(false), 운영은 켠다(기본값 true). */
@Component
public class AuthSettings {

    private final boolean enabled;

    public AuthSettings(@Value("${tamagotchi.auth.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
