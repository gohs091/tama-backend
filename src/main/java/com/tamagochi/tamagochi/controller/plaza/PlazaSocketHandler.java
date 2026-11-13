package com.tamagochi.tamagochi.controller.plaza;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.auth.AuthSettings;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.battle.MeetBattleService.Peer;
import com.tamagochi.tamagochi.service.plaza.PlazaService;
import com.tamagochi.tamagochi.service.storage.PetImageUrls;
import com.tamagochi.tamagochi.service.storage.PetThumbs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 끄적 광장 WebSocket: /ws/plaza?token=... (&room=열쇠 면 친구가 있는 방으로).
 * 연결 주소의 token으로 로그인한 사람을 확인하고(로컬 개발은 tossUserId), 태어난 끄적이가 있어야 들어갈 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlazaSocketHandler extends TextWebSocketHandler {

    private final PlazaService plazaService;
    private final AuthSettings authSettings;
    private final AuthTokenService tokenService;
    private final UserRepository userRepository;
    private final TamagotchiRepository tamagotchiRepository;
    private final PetThumbs petThumbs;

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        raw.setTextMessageSizeLimit(96 * 1024);   // 처음 보내는 끄적이 그림(작게 줄인 PNG)
        // 들어올 때 받는 방 정보에 끄적이 그림(작게 줄인 것)이 마리마다 실린다. 느린 통신에서도 끊기지 않게 넉넉히 둔다
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 10_000, 1024 * 1024);
        Peer peer = peerOf(session);

        var params = UriComponentsBuilder.fromUri(raw.getUri()).build().getQueryParams();
        Optional<String> tossUserId = authSettings.isEnabled()
                ? tokenService.verify(params.getFirst("token"))
                : Optional.ofNullable(params.getFirst("tossUserId"));
        User user = tossUserId.flatMap(userRepository::findByTossUserId).orElse(null);
        if (user == null) { reject(peer, "로그인이 필요해요"); return; }
        Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId()).orElse(null);
        if (pet == null || pet.isDead()) { reject(peer, "끄적이를 먼저 그려 주세요"); return; }
        // 알도 광장에 들어올 수 있다 (초대받은 새 유저가 구경하게). 알은 구경만 한다: 밀어내기 참가·기술은 PlazaService가 막는다
        if (pet.isAdventuring()) { reject(peer, "끄적이가 모험 중이라 광장에 갈 수 없어요"); return; }

        // 광장에는 작게 줄인 그림을 보낸다. 원본 주소를 보내면 끄적이마다 1024px 원본(예전 그림은 1MB 안팎)을 받느라
        // 이름표만 먼저 뜨고 그림은 한참 뒤에 뜨거나 안 뜬다.
        // 줄이지 못했으면(원본을 못 받았거나 빈 그림이면) 앱이 보내는 작은 그림을 쓰고, 그것도 없을 때만 원본 주소를 쓴다
        String imageKey = pet.getCurrentImageKey();
        String image = petThumbs.of(imageKey).orElse(null);

        peers.put(raw.getId(), peer);
        plazaService.connect(peer, new PlazaService.Profile(user.getId(), user.getNickname(), pet.getName(), pet.level(),
                pet.getEvolutionStage().name(), image, pet.getPublicId(),
                com.tamagochi.tamagochi.service.battle.BattleSkillService.current(pet).orElse(null)), params.getFirst("room"),
                image == null ? PetImageUrls.of(imageKey) : null);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Peer peer = peers.get(session.getId());
        if (peer != null && message.getPayloadLength() <= 96 * 1024) plazaService.onMessage(peer, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Peer peer = peers.remove(session.getId());
        if (peer != null) plazaService.disconnect(peer);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Plaza transport error: {}", exception.getMessage());
    }

    private void reject(Peer peer, String message) {
        peer.send("{\"t\":\"error\",\"message\":\"" + message + "\"}");
        peer.close();
    }

    private static Peer peerOf(WebSocketSession session) {
        return new Peer() {
            @Override public String id() { return session.getId(); }
            @Override public void send(String json) {
                try {
                    if (session.isOpen()) session.sendMessage(new TextMessage(json));
                } catch (Exception e) {
                    log.debug("Plaza send failed: {}", e.getMessage());
                }
            }
            @Override public void close() {
                try { session.close(CloseStatus.NORMAL); } catch (Exception ignored) { }
            }
        };
    }
}
