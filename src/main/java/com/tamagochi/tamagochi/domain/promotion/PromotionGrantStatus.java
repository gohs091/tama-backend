package com.tamagochi.tamagochi.domain.promotion;

public enum PromotionGrantStatus {
    RECORDED,  // 대상으로 기록했고 아직 토스에 지급을 요청하지 않았다 (재시도 대기 포함)
    SENDING,   // 지급을 요청하는 중 (한 곳에서만 처리하도록 선점한 상태)
    PENDING,   // 토스가 접수했고 처리 중이다 (결과 조회로 확인)
    GRANTED,   // 토스 포인트 지급 완료
    FAILED     // 지급하지 못했다 (영구 오류이거나 재시도 횟수를 넘김). 하루 상한 횟수에는 세지 않는다
}
