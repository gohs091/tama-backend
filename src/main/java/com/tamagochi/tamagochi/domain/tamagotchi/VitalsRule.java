package com.tamagochi.tamagochi.domain.tamagotchi;

import java.time.Duration;

/** 시간 경과에 따른 소진 규칙 (끄적이는 떠나지 않는다. 불이익은 DebuffRules). 값은 application.yml(tamagotchi.vitals.*)에서 주입한다. */
public record VitalsRule(
        int satietyMinutesPerPoint,   // 포만감 1 감소에 걸리는 시간(분)
        int strengthMinutesPerPoint,  // 파워 1 감소에 걸리는 시간(분)
        int poopIntervalMinutes,      // 똥이 1개 늘어나는 간격(분)
        int maxPoop,                  // 똥은 이 개수에서 더 늘지 않는다
        Duration maxTickGap           // 틱 사이 간격이 이보다 길면 서버 중단으로 보고 그 시간은 흐르지 않은 것으로 처리
) {
}
