package arena.tournament;

/**
 * 치명적인 수 하나에 대한 판정.
 *
 * reachIfBest  최선 대안을 골랐을 때 닿을 수 있었던 칸 수
 * reachChosen  실제 선택 이후 닿을 수 있는 칸 수
 * loss         둘의 차이. 이 수로 잃은 공간이다.
 */
public record DiagnosisEntry(
        long seed,
        int turn,
        String chose,
        String best,
        int reachIfBest,
        int reachChosen,
        int loss
) {}
