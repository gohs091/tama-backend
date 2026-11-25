package com.tamagochi.tamagochi.domain.point;

import java.util.Arrays;
import java.util.Optional;

/**
 * 인앱결제 상품(SKU)과 지급량. 현금으로 파는 것은 붉은 잉크뿐이고, 검은 잉크는 붉은 잉크로 바꾼다({@link InkExchange}).
 * SKU는 앱인토스 콘솔에 등록한 상품 ID와 같아야 하고, 프론트 src/utils/redInkPacks.ts 의 SKU와도 맞춰야 한다.
 * 지급량은 항상 서버가 이 표로 정한다.
 *
 * 세 SKU는 예전에 검은 잉크 상품이던 것을 콘솔에서 붉은 잉크로 바꾼 것이다. 붉은 잉크를 모르는 예전 앱은
 * 같은 SKU를 "잉크 1,500개"처럼 검은 잉크로 보여 주고 결제하므로, 그 앱의 주문에는 화면이 약속한 검은 잉크(legacyPoints)를 준다.
 */
public enum IapProduct {
    RED_INK_10("ait.0000032667.a43f662f.e3e8089345.3031847883", 10, 1500),  // 1,100원 · 붉은 잉크 10개
    RED_INK_33("ait.0000032667.c26f773b.ab0e80b6a6.3031873797", 33, 4500),  // 3,300원 · 붉은 잉크 33개 (덤 3)
    RED_INK_60("ait.0000032667.d39fae7b.06eeca7735.3031999667", 60, 8000);  // 5,500원 · 붉은 잉크 60개 (덤 10)

    private final String sku;
    private final int redInk;        // 붉은 잉크
    private final int legacyPoints;  // 예전 앱(붉은 잉크를 모르는 앱)의 주문에 주는 검은 잉크

    IapProduct(String sku, int redInk, int legacyPoints) {
        this.sku = sku;
        this.redInk = redInk;
        this.legacyPoints = legacyPoints;
    }

    public String getSku() {
        return sku;
    }

    public int getRedInk() {
        return redInk;
    }

    public int getLegacyPoints() {
        return legacyPoints;
    }

    public static Optional<IapProduct> findBySku(String sku) {
        return Arrays.stream(values()).filter(p -> p.sku.equals(sku)).findFirst();
    }
}
