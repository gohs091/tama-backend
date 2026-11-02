package com.tamagochi.tamagochi.controller.battle;

import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.auth.AuthSettings;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import com.tamagochi.tamagochi.service.battle.MeetBattleService;
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
 * 만나서 배틀 WebSocket. 연결 주소의 token으로 로그인한 사람을 확인하고(로컬 개발은 tossUserId),
 * 부화한 끄적이가 있어야 방을 만들거나 들어갈 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetBattleSocketHandler extends TextWebSocketHandler {

    private final MeetBattleService battleService;
    private final AuthSettings authSettings;
    private final AuthTokenService tokenService;
    private final UserRepository userRepository;
    private final TamagotchiRepository tamagotchiRepository;

    private final Map<String, MeetBattleService.Peer> peers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        // 처음 보내는 끄적이 그림(작게 줄인 PNG)이 기본 한도(8KB)를 넘으므로 늘린다
        raw.setTextMessageSizeLimit(128 * 1024);
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5_000, 256 * 1024);
        MeetBattleService.Peer peer = peerOf(session);
        peers.put(raw.getId(), peer);

        var params = UriComponentsBuilder.fromUri(raw.getUri()).build().getQueryParams();
        String token = params.getFirst("token");
        Optional<String> tossUserId = authSettings.isEnabled()
                ? tokenService.verify(token)
                : Optional.ofNullable(params.getFirst("tossUserId"));
        User user = tossUserId.flatMap(userRepository::findByTossUserId).orElse(null);
        if (user == null) {
            reject(peer, "로그인이 필요해요");
            return;
        }
        Tamagotchi pet = tamagotchiRepository.findByUserId(user.getId()).orElse(null);
        if (pet == null || pet.isDead() || pet.getEvolutionStage() == EvolutionStage.EGG) {
            reject(peer, "부화한 끄적이가 있어야 배틀할 수 있어요");
            return;
        }
        if (pet.isAdventuring()) {
            reject(peer, "끄적이가 모험 중이라 배틀할 수 없어요");
            return;
        }
        if ("1".equals(params.getFirst("create"))) battleService.create(peer, user.getId());
        else battleService.join(peer, user.getId(), params.getFirst("room"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        MeetBattleService.Peer peer = peers.get(session.getId());
        if (peer != null && message.getPayloadLength() <= 128 * 1024) battleService.onMessage(peer, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        MeetBattleService.Peer peer = peers.remove(session.getId());
        if (peer != null) battleService.disconnect(peer);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Meet battle transport error: {}", exception.getMessage());
    }

    private void reject(MeetBattleService.Peer peer, String message) {
        peer.send("{\"t\":\"error\",\"message\":\"" + message + "\"}");
        peer.close();
    }

    private static MeetBattleService.Peer peerOf(WebSocketSession session) {
        return new MeetBattleService.Peer() {
            @Override public String id() { return session.getId(); }
            @Override public void send(String json) {
                try {
                    if (session.isOpen()) session.sendMessage(new TextMessage(json));
                } catch (Exception e) {
                    log.debug("Meet battle send failed: {}", e.getMessage());
                }
            }
            @Override public void close() {
                try { session.close(CloseStatus.NORMAL); } catch (Exception ignored) { }
            }
        };
    }
}
