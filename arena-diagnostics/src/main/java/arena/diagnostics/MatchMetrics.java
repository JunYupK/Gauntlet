package arena.diagnostics;

/** 봇별·턴별 지표. 바깥 인덱스가 봇, 안쪽이 턴이다. */
public record MatchMetrics(
        int[][] reach,
        int[][] loss,
        double[] occupancy,
        double[] suicideRate
) {}
