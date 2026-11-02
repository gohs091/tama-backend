package com.tamagochi.tamagochi.repository.tamagotchi;

import com.tamagochi.tamagochi.domain.tamagotchi.PetStatus;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TamagotchiRepository extends JpaRepository<Tamagotchi, Long> {

    Optional<Tamagotchi> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, PetStatus status);

    /** 유저가 지금 키우는 끄적이. 한 유저가 여러 마리를 갖게 돼도 이 하나가 기준이다 */
    default Optional<Tamagotchi> findActiveByUserId(Long userId) {
        return findFirstByUserIdAndStatusOrderByIdDesc(userId, PetStatus.ACTIVE);
    }

    List<Tamagotchi> findAllByStatus(PetStatus status);

    Optional<Tamagotchi> findByPublicId(String publicId);

    List<Tamagotchi> findTop500ByPublicIdIsNull();
}
