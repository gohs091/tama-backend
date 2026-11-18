package com.tamagochi.tamagochi.service.lunch;

import com.tamagochi.tamagochi.domain.user.User;
import org.springframework.stereotype.Component;

/**
 * 햄스터(NPC) 구출 보상의 훅. 햄스터를 구하면 도시락을 한 번에 여러 개 만들 수 있고, 가방이 한 칸 늘어난다 (LunchService).
 * 아직 NPC 구출 기능이 없어서 누구도 구출하지 않은 것으로 본다 (늘 false).
 * NPC가 붙으면 rescued만 채우면 된다 — 도시락 쪽은 이 값만 본다.
 */
@Component
public class HamsterReward {

    /** 햄스터 구출 뒤에 늘어나는 가방 칸 수 (상한은 LunchCatalog.maxBagSlots) */
    public static final int BAG_BONUS = 1;

    /** 이 유저가 햄스터를 구했는가 */
    public boolean rescued(User user) {
        return false;
    }
}
