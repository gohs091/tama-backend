package com.tamagochi.tamagochi.controller.user;

import com.tamagochi.tamagochi.controller.dto.JoinRequest;
import com.tamagochi.tamagochi.controller.dto.UserResponse;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.UserIdentity;
import com.tamagochi.tamagochi.service.user.UserProfileService;
import com.tamagochi.tamagochi.service.user.UserService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileService userProfileService;
    private final UserIdentity userIdentity;

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinRequest request) {
        userIdentity.requireSame(request.getTossUserId());
        User user = userService.createUser(
                request.getTossUserId(),
                request.getNickname()
        );
        return ResponseEntity.ok(UserResponse.from(userProfileService.ensureProfile(user)));
    }

    /** 내 유저 코드와 닉네임 */
    @GetMapping("/me/{tossUserId}")
    public ResponseEntity<?> me(@PathVariable String tossUserId) {
        return ResponseEntity.ok(UserResponse.from(userProfileService.ensureProfile(userIdentity.resolve(tossUserId))));
    }

    @Getter
    @NoArgsConstructor
    public static class NicknameRequest {
        private String tossUserId;
        private String nickname;
    }

    /** 닉네임 바꾸기 (다른 사람과 같아도 된다) */
    @PostMapping("/nickname")
    public ResponseEntity<?> changeNickname(@RequestBody NicknameRequest request) {
        User user = userIdentity.resolve(request.getTossUserId());
        return ResponseEntity.ok(UserResponse.from(userProfileService.changeNickname(user, request.getNickname())));
    }
}
