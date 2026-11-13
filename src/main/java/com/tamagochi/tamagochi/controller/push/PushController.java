package com.tamagochi.tamagochi.controller.push;

import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 끄적이 알림 동의. 앱이 토스 알림 동의 창의 결과를 알려 준다 */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final UserIdentity userIdentity;
    private final UserRepository userRepository;
    private final com.tamagochi.tamagochi.service.push.PushService pushService;

    @Getter
    @NoArgsConstructor
    public static class AgreementRequest {
        private String tossUserId;
        private boolean agreed;
    }

    @PostMapping("/agreement")
    @Transactional
    public ResponseEntity<?> agreement(@RequestBody AgreementRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        user.changePushAgreed(request.isAgreed());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("agreed", user.isPushAgreed()));
    }

    /** agreed: true·false, 아직 묻지 않았으면 null */
    @GetMapping("/agreement/{tossUserId}")
    public ResponseEntity<?> get(@PathVariable String tossUserId) {
        User user = userIdentity.resolve(tossUserId);
        Map<String, Object> out = new HashMap<>();
        out.put("agreed", user.getPushAgreed());
        out.put("agreementCode", pushService.agreementCode());   // 동의 창을 띄울 캠페인 코드
        return ResponseEntity.ok(out);
    }
}
