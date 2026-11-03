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

    /** DB에 base64 그림이 남아 있는 끄적이 번호 (S3로 옮기기용, after보다 큰 번호부터) */
    @org.springframework.data.jpa.repository.Query("select t.id from Tamagotchi t where t.id > :after and " +
            "(t.babyImageBase64 is not null or t.childImageBase64 is not null or t.adultImageBase64 is not null) order by t.id")
    List<Long> findIdsWithBase64Images(@org.springframework.data.repository.query.Param("after") long after,
                                       org.springframework.data.domain.Pageable page);
}
