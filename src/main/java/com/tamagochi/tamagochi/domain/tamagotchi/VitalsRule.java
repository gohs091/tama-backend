package com.tamagochi.tamagochi.domain.tamagotchi;

import java.time.Duration;

/**
 * 시간 경과에 따른 생존 규칙. 값은 application.yml(tamagotchi.vitals.*)에서 주입한다.
 * 사망 판정은 완성작 전(낙서·스케치)에만 한다. 완성작은 떠나지 않고 디버프만 받는다 (DebuffRules).
 */
public record VitalsRule(
        int satietyMinutesPerPoint,   // 포만감 1 감소에 걸리는 시간(분)
        int strengthMinutesPerPoint,  // 파워 1 감소에 걸리는 시간(분)
        Duration deathAfterEmpty,     // 포만감/파워가 0인 채로 이 시간이 지나면 사망 (완성작 전)
        int poopIntervalMinutes,      // 똥이 1개 늘어나는 간격(분)
        int maxPoop,                  // 이 개수 이상이면 사망 (완성작 전). 완성작은 여기서 더 늘지 않을 뿐이다
        Duration maxTickGap           // 틱 사이 간격이 이보다 길면 서버 중단으로 보고 그 시간은 흐르지 않은 것으로 처리
) {
}
