package arena.core;

/** winner: 0 또는 1, 무승부는 -1. */
public record MatchResult(int winner, int turns, DeathReason reason) {

    public boolean isDraw() { return winner < 0; }
}
