package com.tamagochi.tamagochi.service.push;

/**
 * 끄적이 알림 종류. 콘솔의 발송 코드는 "doodle-app-" + code 다.
 * 위에 있을수록 먼저 보낸다 (여러 개가 한꺼번에 해당하면 하나만 보낸다).
 * particle: 템플릿 문구에서 {petName} 바로 뒤에 붙는 조사. 받침에 맞춰 서버가 붙여 넣는다 (예: "냥냥이가", "용이", "냥냥이의")
 */
public enum PushKind {
    POOP("poop", Particle.SUBJECT, Episode.UNTIL_RESOLVED),
    HUNGRY("hungry", Particle.SUBJECT, Episode.UNTIL_RESOLVED),
    SICK("sick", Particle.POSSESSIVE, Episode.UNTIL_RESOLVED),
    EVO_READY("evo-ready", Particle.SUBJECT, Episode.ONCE_PER_STAGE),
    EVO_SOON("evo-soon", Particle.SUBJECT, Episode.ONCE_PER_STAGE),
    SLEEPY("sleepy", Particle.SUBJECT, Episode.UNTIL_RESOLVED),
    BORED("bored", Particle.SUBJECT, Episode.UNTIL_RESOLVED);

    /** 조사 */
    public enum Particle { SUBJECT, POSSESSIVE }

    /**
     * 같은 알림을 다시 보내는 때.
     * UNTIL_RESOLVED: 그 상태가 풀릴 때까지 한 번 (배고픔이면 밥을 먹어 나아질 때까지 다시 안 보낸다)
     * ONCE_PER_STAGE: 단계마다 한 번 (진화 알림)
     */
    public enum Episode { UNTIL_RESOLVED, ONCE_PER_STAGE }

    public final String code;
    public final Particle particle;
    public final Episode episode;

    PushKind(String code, Particle particle, Episode episode) {
        this.code = code;
        this.particle = particle;
        this.episode = episode;
    }
}
