package com.tamagochi.tamagochi.controller.auth;

import com.tamagochi.tamagochi.controller.dto.TossLoginRequest;
import com.tamagochi.tamagochi.service.auth.TossAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TossAuthService tossAuthService;

    /** 토스 앱에서 받은 일회용 코드로 로그인하고 토큰을 받는다. */
    @PostMapping("/toss")
    public ResponseEntity<?> login(@RequestBody TossLoginRequest request) {
        return ResponseEntity.ok(tossAuthService.login(request.getCode()));
    }
}
