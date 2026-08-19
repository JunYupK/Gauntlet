package arena.diagnostics;

/**
 * 봇별·턴별 지표. 바깥 인덱스가 봇, 안쪽이 턴이다.
 *
 * {@code reach[bot][turn]}: 그 턴에 그 봇이 실제로 남긴 공간. 그 턴에
 * 그 봇이 살아남았다면 {@link MoveAnalysis#reachAfterChosen}과 같다.
 * 그 턴이 그 봇의 사망 턴이라면(엔진이 실제로 사망 판정한 턴,
 * {@link MoveAnalysis#fatal} 참고) 0이다 — 죽은 봇에게 남은 공간은
 * 없기 때문이다. 이는 {@link MoveAnalysis#reachAfterChosen}이 그
 * 사망 턴에도 유지하는 반사실적 값과 의도적으로 다르다: 그쪽은
 * {@code loss}의 대칭(같은 반사실 위에서 비교)을 지키려고 값을
 * 바꾸지 않지만, 여기 {@code reach}는 화면에 "이 봇이 실제로 가진
 * 공간"을 그리는 배열이라 사망 턴엔 0으로 끊는다.
 */
public record MatchMetrics(
        int[][] reach,
        int[][] loss,
        double[] occupancy,
        double[] suicideRate
) {}
