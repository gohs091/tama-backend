package com.tamagochi.tamagochi.domain.point;

import java.util.Arrays;
import java.util.Optional;

/**
 * 인앱결제 상품(SKU)과 지급 포인트. SKU는 앱인토스 콘솔에 등록한 상품 ID와 같아야 하고,
 * 프론트 src/utils/pointPacks.ts · src/utils/redInkPacks.ts 의 SKU와도 맞춰야 한다. 지급량은 항상 서버가 이 표로 정한다.
 */
public enum IapProduct {
    POINT_1000("ait.0000032667.a43f662f.e3e8089345.3031847883", 1500, 0),  // 1,000원 · 잉크 1,500개
    POINT_3000("ait.0000032667.c26f773b.ab0e80b6a6.3031873797", 4500, 0),  // 3,000원 · 잉크 4,500개
    POINT_5000("ait.0000032667.d39fae7b.06eeca7735.3031999667", 8000, 0),  // 5,000원 · 잉크 8,000개 (1,000원당 1,500 / 1,500 / 1,600: 비싼 상품이 손해 보지 않아야 한다)

    // 붉은 잉크: 결제로만 얻는 재화. 한 개에 약 110원이고 큰 묶음일수록 덤이 붙는다.
    // SKU는 콘솔에 상품을 등록하기 전의 임시 값이다. 등록하면 여기와 프론트 redInkPacks.ts 를 함께 바꾼다
    // (임시 값으로는 토스 주문 확인을 통과할 수 없어서 운영에서는 지급되지 않는다).
    RED_INK_10("red-ink.10", 0, 10),   // 1,100원 · 붉은 잉크 10개
    RED_INK_33("red-ink.33", 0, 33),   // 3,300원 · 붉은 잉크 33개 (덤 3)
    RED_INK_60("red-ink.60", 0, 60);   // 5,500원 · 붉은 잉크 60개 (덤 10)

    private final String sku;
    private final int points;   // 검은 잉크
    private final int redInk;   // 붉은 잉크

    IapProduct(String sku, int points, int redInk) {
        this.sku = sku;
        this.points = points;
        this.redInk = redInk;
    }

    public String getSku() {
        return sku;
    }

    public int getPoints() {
        return points;
    }

    public int getRedInk() {
        return redInk;
    }

    public boolean isRedInk() {
        return redInk > 0;
    }

    public static Optional<IapProduct> findBySku(String sku) {
        return Arrays.stream(values()).filter(p -> p.sku.equals(sku)).findFirst();
    }
}
