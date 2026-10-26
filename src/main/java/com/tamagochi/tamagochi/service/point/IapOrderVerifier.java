package com.tamagochi.tamagochi.service.point;

/** 인앱결제 주문이 실제로 결제됐는지 확인한다. 클라이언트가 보낸 orderId는 그대로 믿지 않는다. */
public interface IapOrderVerifier {

    /** orderId 주문이 해당 sku 상품으로 결제 완료 상태이면 true */
    boolean isPaid(String orderId, String sku);
}
