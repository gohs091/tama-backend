package com.tamagochi.tamagochi.service.user;

import com.tamagochi.tamagochi.domain.user.NicknameHistory;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.user.NicknameHistoryRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    UserRepository users;
    NicknameHistoryRepository history;
    UserProfileService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        history = mock(NicknameHistoryRepository.class);
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.existsByUserCode(anyString())).thenReturn(false);
        service = new UserProfileService(users, history);
    }

    @Test
    @DisplayName("예전 유저(닉네임 = tossUserId, 코드 없음)는 로그인할 때 코드와 무작위 닉네임을 받고, 이력이 남는다")
    void ensureForOldUser() {
        User u = User.builder().id(7L).tossUserId("toss_abc").nickname("toss_abc").build();

        service.ensureProfile(u);

        assertThat(u.getUserCode()).hasSize(8);
        assertThat(u.getNickname()).matches("\\S+ \\S+ \\d{4}");
        ArgumentCaptor<NicknameHistory> h = ArgumentCaptor.forClass(NicknameHistory.class);
        verify(history).save(h.capture());
        assertThat(h.getValue().getUserId()).isEqualTo(7L);
        assertThat(h.getValue().getNickname()).isEqualTo(u.getNickname());
    }

    @Test
    @DisplayName("이미 코드와 닉네임이 있으면 그대로 둔다 (코드는 절대 바뀌지 않는다)")
    void ensureKeepsExisting() {
        User u = User.builder().id(7L).tossUserId("toss_abc").nickname("무야호").userCode("K7Q29XMA").build();

        service.ensureProfile(u);

        assertThat(u.getUserCode()).isEqualTo("K7Q29XMA");
        assertThat(u.getNickname()).isEqualTo("무야호");
        verify(history, never()).save(any());
    }

    @Test
    @DisplayName("코드가 겹치면 다시 만든다")
    void codeCollisionRetries() {
        when(users.existsByUserCode(anyString())).thenReturn(true, true, false);
        User u = User.builder().id(1L).tossUserId("toss_x").nickname("무야호").build();

        service.ensureProfile(u);

        verify(users, times(3)).existsByUserCode(anyString());
        assertThat(u.getUserCode()).hasSize(8);
    }

    @Test
    @DisplayName("닉네임을 바꾸면 이력이 남고, 같은 닉네임으로 바꾸면 남기지 않는다")
    void changeNickname() {
        User u = User.builder().id(3L).tossUserId("toss_y").nickname("무야호").userCode("AAAA2222").build();

        service.changeNickname(u, "  콩이 엄마 ");
        service.changeNickname(u, "콩이 엄마");

        assertThat(u.getNickname()).isEqualTo("콩이 엄마");
        verify(history, times(1)).save(any());
    }
}
