package com.tamagochi.tamagochi.controller.user;

import com.tamagochi.tamagochi.controller.dto.JoinRequest;
import com.tamagochi.tamagochi.controller.dto.UserResponse;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinRequest request) {
        User user = userService.createUser(
                request.getTossUserId(),
                request.getNickname()
        );
        return ResponseEntity.ok(UserResponse.from(user));
    }
}