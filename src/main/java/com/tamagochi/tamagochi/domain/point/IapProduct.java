package com.tamagochi.tamagochi.domain.point;

import java.util.Arrays;
import java.util.Optional;

/**
 * 인앱결제 상품(SKU)과 지급 포인트. SKU는 앱인토스 콘솔에 등록한 상품 ID와 같아야 하고,
 * 프론트 src/utils/pointPacks.ts 의 SKU와도 맞춰야 한다. 지급량은 항상 서버가 이 표로 정한다.
 */
public enum IapProduct {
    POINT_1000("point_1000", 700),   // 1,000원
    POINT_3000("point_3000", 2200),  // 3,000원
    POINT_5000("point_5000", 3800);  // 5,000원 (비쌀수록 원당 지급량이 많아야 한다: 700 / 733 / 760 per 1,000원)

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
