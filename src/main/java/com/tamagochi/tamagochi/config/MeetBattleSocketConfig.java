package com.tamagochi.tamagochi.config;

import com.tamagochi.tamagochi.controller.battle.MeetBattleSocketHandler;
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

    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/battle").setAllowedOriginPatterns(allowedOrigins);
    }

}
