package com.tamagochi.tamagochi.domain.farm;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 밭 한 칸. crop이 비어 있으면 빈 밭. grown = 지금까지 자란 정도(초, 물 있을 때는 빠르게 센다) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmPlot {

    @Column(name = "crop")
    private String crop;

    @Column(name = "grown", nullable = false)
    private long grown;

    /** 이 칸에 심은 횟수. 씨앗값을 낼 때 같은 결제를 두 번 하지 않게 하는 키에 쓴다 */
    @Column(name = "seq", nullable = false)
    private int seq;

    FarmPlot(String crop, long grown, int seq) {
        this.crop = crop;
        this.grown = grown;
        this.seq = seq;
    }

    static FarmPlot empty() {
        return new FarmPlot(null, 0, 0);
    }

    FarmPlot planted(String crop) {
        return new FarmPlot(crop, 0, seq + 1);
    }

    FarmPlot grownBy(long seconds, long max) {
        return new FarmPlot(crop, Math.min(max, grown + seconds), seq);
    }

    FarmPlot cleared() {
        return new FarmPlot(null, 0, seq);
    }

    FarmPlot full(long max) {
        return new FarmPlot(crop, max, seq);
    }
}
