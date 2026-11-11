package com.tamagochi.tamagochi.config;

import com.tamagochi.tamagochi.controller.battle.MeetBattleSocketHandler;
import com.tamagochi.tamagochi.controller.plaza.PlazaSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 만나서 배틀 실시간 연결: /ws/battle?token=...&create=1 (방 만들기) 또는 &room=CODE (QR로 들어가기) */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MeetBattleSocketConfig implements WebSocketConfigurer {

    private final MeetBattleSocketHandler handler;
    private final PlazaSocketHandler plazaHandler;

    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/battle").setAllowedOriginPatterns(allowedOrigins);
        // 끄적 광장: /ws/plaza?token=... (&room=열쇠 면 친구가 있는 방으로)
        registry.addHandler(plazaHandler, "/ws/plaza").setAllowedOriginPatterns(allowedOrigins);
    }

}
