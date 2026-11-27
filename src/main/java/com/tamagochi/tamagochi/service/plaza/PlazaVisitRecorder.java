package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.service.usage.UsageRecorder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 광장 방문 기록을 사용 기록(UsageRecorder)에 넘긴다. 광장 서비스는 잠금 하나로 도는 메모리 서비스라 DB 를 부르지 않고,
 * UsageRecorder 가 제 실행기에서 저장한다 (일을 넣고 바로 돌아오며 예외를 던지지 않는다).
 */
@Component
@RequiredArgsConstructor
public class PlazaVisitRecorder implements PlazaService.Visits {

    private final PlazaService plazaService;
    private final UsageRecorder usageRecorder;

    /** 앱이 시작할 때 광장에 기록 쪽을 건다 */
    @PostConstruct
    void install() {
        plazaService.setVisits(this);
    }

    @Override
    public void left(long userId, String kind, long enteredAt, long leftAt, int kills, int chats) {
        usageRecorder.plazaVisit(userId, kind, enteredAt, leftAt, kills, chats);
    }
}
