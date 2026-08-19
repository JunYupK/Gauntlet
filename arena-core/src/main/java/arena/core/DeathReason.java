package arena.core;

public enum DeathReason {
    P0_OUT_OF_BOUNDS,
    P0_HIT_OWN_WALL,
    P0_HIT_OPPONENT_WALL,
    P1_OUT_OF_BOUNDS,
    P1_HIT_OWN_WALL,
    P1_HIT_OPPONENT_WALL,
    /** 양쪽이 같은 칸에 동시 진입. */
    HEAD_ON_COLLISION,
    /** 양쪽이 각자 다른 이유로 같은 턴에 사망. */
    BOTH_DIED,
    /** 도달 불가능해야 하는 안전장치. 걸리면 엔진 버그다. */
    MAX_TURNS
}
