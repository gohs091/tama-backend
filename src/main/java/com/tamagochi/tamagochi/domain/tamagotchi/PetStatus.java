package com.tamagochi.tamagochi.domain.tamagotchi;

/** 끄적이 상태. 지금 키우는 끄적이는 ACTIVE 하나다. 여러 마리를 갖게 되면 떠난 끄적이는 GONE으로 남겨 둔다 */
public enum PetStatus {
    ACTIVE,
    GONE
}
