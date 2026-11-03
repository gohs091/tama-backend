package com.tamagochi.tamagochi.domain.spar;

import com.tamagochi.tamagochi.domain.land.LandCatalog.Grade;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 스파링 잉크 드롭 규칙. 수치는 모두 여기서만 고친다.
 * - 이긴 판의 PER_WIN 확률로 잉크 조각 하나가 떨어진다. 별 개수·KO 여부와는 상관없다.
 * - 어떤 잉크인지는 이 상대의 드롭 칸 중 아직 완성하지 않은 잉크에서 등급 가중치로 고른다
 *   (칸이 많을수록 원하는 잉크가 덜 나와 같은 상대를 여러 번 찾게 된다. 다 모은 잉크는 빠지므로 남은 잉크는 점점 잘 나온다).
 * - 조각을 NEED개 모으면 잉크가 완성돼 보관함에 들어간다.
 */
public final class SparDropRules {

    public static final double PER_WIN = 0.15;

    private SparDropRules() {
    }

    public static double weight(Grade grade) {
        return switch (grade) {
            case FAMILY, COMMON -> 4;
            case RARE -> 1.5;
            case LEGEND -> 0.5;
        };
    }

    /** 잉크 하나를 완성하는 데 필요한 조각 수 */
    public static int need(Grade grade) {
        return switch (grade) {
            case FAMILY, COMMON -> 5;
            case RARE -> 3;
            case LEGEND -> 1;
        };
    }

    /** roll(0 이상 1 미만)로 가중치에 따라 하나를 고른다. 후보가 없으면 null */
    public static <T> T pickWeighted(List<T> items, ToDoubleFunction<T> weight, double roll) {
        double total = items.stream().mapToDouble(weight).sum();
        if (items.isEmpty() || total <= 0) return null;
        double target = roll * total;
        for (T item : items) {
            target -= weight.applyAsDouble(item);
            if (target < 0) return item;
        }
        return items.get(items.size() - 1);
    }
}
