package com.tamagochi.tamagochi.domain.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IapProductTest {

    @Test
    @DisplayName("SKU로 지급 포인트를 찾는다")
    void findBySku() {
        assertThat(IapProduct.findBySku("ait.0000032667.a43f662f.e3e8089345.3031847883").orElseThrow().getPoints()).isEqualTo(1500);
        assertThat(IapProduct.findBySku("ait.0000032667.c26f773b.ab0e80b6a6.3031873797").orElseThrow().getPoints()).isEqualTo(4500);
        assertThat(IapProduct.findBySku("ait.0000032667.d39fae7b.06eeca7735.3031999667").orElseThrow().getPoints()).isEqualTo(8000);
    }

    @Test
    @DisplayName("비싼 상품이 원당 받는 양에서 손해 보지 않는다")
    void biggerPackIsBetterValue() {
        int[] won = {1000, 3000, 5000};
        String[] skus = {"ait.0000032667.a43f662f.e3e8089345.3031847883", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797", "ait.0000032667.d39fae7b.06eeca7735.3031999667"};
        double prev = 0;
        for (int i = 0; i < skus.length; i++) {
            double perWon = IapProduct.findBySku(skus[i]).orElseThrow().getPoints() / (double) won[i];
            assertThat(perWon).isGreaterThanOrEqualTo(prev);
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
