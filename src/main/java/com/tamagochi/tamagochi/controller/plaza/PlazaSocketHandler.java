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

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        raw.setTextMessageSizeLimit(96 * 1024);   // 처음 보내는 끄적이 그림(작게 줄인 PNG)
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5_000, 512 * 1024);
        Peer peer = peerOf(session);

        var params = UriComponentsBuilder.fromUri(raw.getUri()).build().getQueryParams();
        Optional<String> tossUserId = authSettings.isEnabled()
                ? tokenService.verify(params.getFirst("token"))
                : Optional.ofNullable(params.getFirst("tossUserId"));
        User user = tossUserId.flatMap(userRepository::findByTossUserId).orElse(null);
        if (user == null) { reject(peer, "로그인이 필요해요"); return; }
        Tamagotchi pet = tamagotchiRepository.findActiveByUserId(user.getId()).orElse(null);
        if (pet == null || pet.isDead()) { reject(peer, "끄적이를 먼저 그려 주세요"); return; }
        if (pet.getEvolutionStage() == EvolutionStage.EGG) { reject(peer, "끄적이가 태어나면 광장에 갈 수 있어요"); return; }
        if (pet.isAdventuring()) { reject(peer, "끄적이가 모험 중이라 광장에 갈 수 없어요"); return; }

        peers.put(raw.getId(), peer);
        plazaService.connect(peer, new PlazaService.Profile(user.getId(), user.getNickname(), pet.getName(), pet.level(),
                pet.getEvolutionStage().name(), PetImageUrls.of(pet.getCurrentImageKey()), pet.getPublicId()), params.getFirst("room"));
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
