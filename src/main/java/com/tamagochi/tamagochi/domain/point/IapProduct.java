package com.tamagochi.tamagochi.domain.point;

import java.util.Arrays;
import java.util.Optional;

/**
 * 인앱결제 상품(SKU)과 지급 포인트. SKU는 앱인토스 콘솔에 등록한 상품 ID와 같아야 하고,
 * 프론트 src/utils/pointPacks.ts 의 SKU와도 맞춰야 한다. 지급량은 항상 서버가 이 표로 정한다.
 */
public enum IapProduct {
    POINT_1000("ait.0000032667.a43f662f.e3e8089345.3031847883", 700),   // 1,000원 · 잉크 700개
    POINT_3000("ait.0000032667.c26f773b.ab0e80b6a6.3031873797", 2200),  // 3,000원 · 잉크 2,200개
    POINT_5000("ait.0000032667.d39fae7b.06eeca7735.3031999667", 3800);  // 5,000원 (잉크 3,800개, 비쌀수록 원당 지급량이 많아야 한다: 700 / 733 / 760 per 1,000원)

    private final String sku;
    private final int points;

    IapProduct(String sku, int points) {
        this.sku = sku;
        this.points = points;
    }

    public String getSku() {
        return sku;
    }

    public int getPoints() {
        return points;
    }

    public static Optional<IapProduct> findBySku(String sku) {
        return Arrays.stream(values()).filter(p -> p.sku.equals(sku)).findFirst();
    }
}
