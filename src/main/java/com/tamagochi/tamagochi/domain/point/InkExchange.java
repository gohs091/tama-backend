package com.tamagochi.tamagochi.domain.point;

import java.util.Arrays;
import java.util.Optional;

/**
 * 붉은 잉크로 검은 잉크를 바꾸는 묶음. 검은 잉크는 현금으로 팔지 않고 이 표로만 바꾼다.
 * 붉은 잉크 10개(약 1,100원)에 1,500개로, 현금으로 팔던 때와 같은 양이다. 프론트 src/utils/pointPacks.ts 와 맞춘다.
 */
public enum InkExchange {
    INK_1500("ink.1500", 10, 1500),
    INK_4500("ink.4500", 30, 4500),
    INK_8000("ink.8000", 50, 8000);  // 붉은 잉크 1개당 150 / 150 / 160: 큰 묶음이 손해 보지 않는다

    private final String id;
    private final int redCost;  // 드는 붉은 잉크
    private final int points;   // 받는 검은 잉크

    InkExchange(String id, int redCost, int points) {
        this.id = id;
        this.redCost = redCost;
        this.points = points;
    }

    public String getId() {
        return id;
    }

    public int getRedCost() {
        return redCost;
    }

    public int getPoints() {
        return points;
    }

    public static Optional<InkExchange> findById(String id) {
        return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst();
    }
}
