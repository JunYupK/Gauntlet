package arena.diagnostics;

import arena.core.Direction;

/**
 * 한 수에 대한 판정.
 *
 * loss = 최선 대안의 reach − 실제 선택의 reach.
 * 실제 선택도 대안 후보에 포함되므로 loss는 항상 0 이상이다.
 */
public record MoveAnalysis(
        int turn,
        Direction chose,
        Direction best,
        int reachAfterChosen,
        int reachAfterBest,
        int loss,
        boolean suicide
) {}
