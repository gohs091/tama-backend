package com.tamagochi.tamagochi.repository.user;

import com.tamagochi.tamagochi.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTossUserId(String tossUserId);
}