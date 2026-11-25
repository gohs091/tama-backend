package com.tamagochi.tamagochi.domain.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IapProductTest {

    private static final String SKU_10 = "ait.0000032667.a43f662f.e3e8089345.3031847883";
    private static final String SKU_33 = "ait.0000032667.c26f773b.ab0e80b6a6.3031873797";
    private static final String SKU_60 = "ait.0000032667.d39fae7b.06eeca7735.3031999667";

    @Test
    @DisplayName("SKU로 붉은 잉크 지급량을 찾는다")
    void findBySku() {
        assertThat(IapProduct.findBySku(SKU_10).orElseThrow().getRedInk()).isEqualTo(10);
        assertThat(IapProduct.findBySku(SKU_33).orElseThrow().getRedInk()).isEqualTo(33);
        assertThat(IapProduct.findBySku(SKU_60).orElseThrow().getRedInk()).isEqualTo(60);
    }

    @Test
    @DisplayName("비싼 상품이 원당 받는 붉은 잉크에서 손해 보지 않는다")
    void biggerPackIsBetterValue() {
        int[] won = {1100, 3300, 5500};
        String[] skus = {SKU_10, SKU_33, SKU_60};
        double prev = 0;
        for (int i = 0; i < skus.length; i++) {
            double perWon = IapProduct.findBySku(skus[i]).orElseThrow().getRedInk() / (double) won[i];
            assertThat(perWon).isGreaterThanOrEqualTo(prev);
            prev = perWon;
        }
    }

    @Test
    @DisplayName("예전 앱의 주문에는 그 앱이 보여 주던 검은 잉크를 준다")
    void legacyPointsMatchOldPacks() {
        assertThat(IapProduct.findBySku(SKU_10).orElseThrow().getLegacyPoints()).isEqualTo(1500);
        assertThat(IapProduct.findBySku(SKU_33).orElseThrow().getLegacyPoints()).isEqualTo(4500);
        assertThat(IapProduct.findBySku(SKU_60).orElseThrow().getLegacyPoints()).isEqualTo(8000);
    }

    @Test
    @DisplayName("예전 앱으로 사는 쪽이 붉은 잉크를 받아 바꾸는 쪽보다 유리하지 않다")
    void legacyGrantIsNotBetterThanExchanging() {
        for (IapProduct product : IapProduct.values()) {
            // 받은 붉은 잉크로 가장 유리하게 바꿨을 때의 검은 잉크 (묶음을 여러 번 살 수 있다)
            int best = 0;
            int[] reach = new int[product.getRedInk() + 1];
            for (int red = 1; red <= product.getRedInk(); red++) {
                reach[red] = reach[red - 1];
                for (InkExchange pack : InkExchange.values()) {
                    if (pack.getRedCost() <= red) {
                        reach[red] = Math.max(reach[red], reach[red - pack.getRedCost()] + pack.getPoints());
                    }
                }
                best = Math.max(best, reach[red]);
            }
            assertThat(product.getLegacyPoints()).isLessThanOrEqualTo(best);
        }
    }

    @Test
    @DisplayName("붉은 잉크로 바꿀 때 큰 묶음이 손해 보지 않는다")
    void biggerExchangeIsBetterValue() {
        double prev = 0;
        for (InkExchange pack : InkExchange.values()) {
            double perRed = pack.getPoints() / (double) pack.getRedCost();
            assertThat(perRed).isGreaterThanOrEqualTo(prev);
            prev = perRed;
        }
    }

    @Test
    @DisplayName("등록되지 않은 SKU · 묶음은 찾지 못한다")
    void unknownSku() {
        assertThat(IapProduct.findBySku("point_999999")).isEmpty();
        assertThat(IapProduct.findBySku(null)).isEmpty();
        assertThat(InkExchange.findById("ink.999")).isEmpty();
        assertThat(InkExchange.findById(null)).isEmpty();
    }
}
