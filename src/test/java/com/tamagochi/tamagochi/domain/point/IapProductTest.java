package com.tamagochi.tamagochi.domain.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IapProductTest {

    @Test
    @DisplayName("SKU로 지급 포인트를 찾는다")
    void findBySku() {
        assertThat(IapProduct.findBySku("point_1000").orElseThrow().getPoints()).isEqualTo(700);
        assertThat(IapProduct.findBySku("point_3000").orElseThrow().getPoints()).isEqualTo(2200);
        assertThat(IapProduct.findBySku("point_5000").orElseThrow().getPoints()).isEqualTo(3800);
    }

    @Test
    @DisplayName("비싼 상품일수록 원당 받는 양이 더 많다")
    void biggerPackIsBetterValue() {
        int[] won = {1000, 3000, 5000};
        String[] skus = {"point_1000", "point_3000", "point_5000"};
        double prev = 0;
        for (int i = 0; i < skus.length; i++) {
            double perWon = IapProduct.findBySku(skus[i]).orElseThrow().getPoints() / (double) won[i];
            assertThat(perWon).isGreaterThan(prev);
            prev = perWon;
        }
    }

    @Test
    @DisplayName("등록되지 않은 SKU는 찾지 못한다")
    void unknownSku() {
        assertThat(IapProduct.findBySku("point_999999")).isEmpty();
        assertThat(IapProduct.findBySku(null)).isEmpty();
    }
}
