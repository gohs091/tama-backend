package com.tamagochi.tamagochi.domain.usage;

/** 끄적이 사건의 종류 (pet_event.type) */
public enum PetEventType {
    /** 알을 깨웠다 (알 → 낙서) */
    HATCH,
    /** 다음 단계가 됐다 (낙서 → 스케치, 스케치 → 완성작). 직접 고른 진화와 구간 끝 자동 진화 모두 */
    EVOLVE,
    /** 떠났다 (굶주림 · 파워 0 · 응가). 까닭은 cause 에 */
    LEAVE,
    /** 새 알로 다시 시작했다 (떠난 뒤든 아니든) */
    RESTART,
    /** 갓 태어난 낙서가 마음에 안 들어 다시 그렸다 (알로 되돌림) */
    REDRAW
}
