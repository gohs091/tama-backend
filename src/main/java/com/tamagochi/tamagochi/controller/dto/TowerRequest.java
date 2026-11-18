package com.tamagochi.tamagochi.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탑 요청. 출발: floor는 떠날 층 (비우면 돌파한 층 바로 위).
 * 맵 통과: runId·floor·map은 앱이 알고 있는 지금 원정 id·층·맵이다. 넣으면 서버 값과 같아야 하고(다르면 거절),
 * 비우면(예전 앱은 tossUserId만 보낸다) 서버 값만 따른다
 */
@Getter
@NoArgsConstructor
public class TowerRequest {
    private String tossUserId;
    private Integer floor;
    private Integer map;
    private String runId;
}
