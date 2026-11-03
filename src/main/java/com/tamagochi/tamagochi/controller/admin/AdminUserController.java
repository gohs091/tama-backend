package com.tamagochi.tamagochi.controller.admin;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.exception.ForbiddenException;
import com.tamagochi.tamagochi.repository.user.NicknameHistoryRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.user.NicknameRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 문의 대응용 유저 찾기. 유저 코드(가장 확실), 지금 닉네임, 예전 닉네임으로 찾아
 * 유저 번호·tossUserId를 돌려준다. 그 값으로 서버 로그를 검색한다 (로그에는 유저 번호와 코드가 남는다).
 * ADMIN_KEY가 있어야 만들어지고 X-Admin-Key 헤더가 맞아야 한다.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@ConditionalOnExpression("!'${tamagotchi.admin.key:}'.isEmpty()")
public class AdminUserController {

    private final UserRepository userRepository;
    private final NicknameHistoryRepository historyRepository;

    @Value("${tamagotchi.admin.key:}")
    private String adminKey;

    @GetMapping
    public ResponseEntity<?> find(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String nickname) {
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), adminKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("관리자 키가 필요해요.");
        }
        Map<Long, User> found = new LinkedHashMap<>();
        if (code != null && !code.isBlank()) {
            String normalized = NicknameRules.normalizeCode(code);
            if (normalized == null) throw new IllegalArgumentException("유저 코드 형식이 아니에요.");
            userRepository.findByUserCode(normalized).ifPresent(u -> found.put(u.getId(), u));
        }
        if (nickname != null && !nickname.isBlank()) {
            String n = nickname.strip();
            userRepository.findTop50ByNickname(n).forEach(u -> found.put(u.getId(), u));
            historyRepository.findTop50ByNicknameOrderByChangedAtDesc(n)
                    .forEach(h -> userRepository.findById(h.getUserId()).ifPresent(u -> found.putIfAbsent(u.getId(), u)));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : found.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("tossUserId", u.getTossUserId());
            m.put("userCode", NicknameRules.formatCode(u.getUserCode()));
            m.put("nickname", u.getNickname());
            m.put("createdAt", u.getCreatedAt());
            m.put("nicknameHistory", historyRepository.findByUserIdOrderByChangedAtDesc(u.getId()).stream()
                    .map(h -> Map.of("nickname", h.getNickname(), "changedAt", String.valueOf(h.getChangedAt())))
                    .toList());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }
}
