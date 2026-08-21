package arena.tournament;

import arena.bots.Bot;
import arena.core.Replay;
import arena.core.SeriesRunner;
import arena.core.Standing;

import java.util.Arrays;
import java.util.List;

/**
 * 전 세대 대진표.
 *
 * 승격 판정에는 쓰지 않는다. 마지막에 한 번만 돌려 히트맵으로 남긴다.
 * 순환 우위(A가 B를 이기고 B가 C를 이기는데 C가 A를 이기는 관계)가
 * 나오면 그것도 그대로 보여줄 재료다.
 *
 * 좌석 식별은 {@code bot.name()}을 쓰지 않는다. 이름은 봇 작성자가
 * 통제하는 문자열이고, 여기서 짝짓는 두 세대(서로 다른 세대일 수도,
 * 우연히 이름이 겹치는 세대일 수도 있다) 사이에 이름이 겹치지 않는다는
 * 보장은 어디에도 없다. {@code Championship}이 이미 겪은 사고와 같은
 * 종류다: 이름을 그대로 SeriesRunner·Standing에 넘기면
 * {@link Standing#seatOf}가 bot0Id를 먼저 검사하는 탓에, 이름이 같은
 * 두 봇의 대전에서 전적이 조용히 좌석 0으로만 쏠려버린다 — 그것도
 * 예외 없이. 그래서 매 대전마다 서로 다른 게 보장된 내부 예약 id
 * 둘만 넘긴다. bots 리스트에 이름이 겹치는 두 세대가 섞여 있어도
 * 행렬은 왜곡되지 않는다.
 */
public final class RoundRobin {

    private static final String ID_A = "__roundrobin_a__";
    private static final String ID_B = "__roundrobin_b__";

    private RoundRobin() {}

    /**
     * {@code [i][j]}는 {@code bots.get(i)}가 {@code bots.get(j)} 상대로
     * 낸 승점 승률(무승부 0.5점, {@code (승 + 0.5×무) / 전체})이다.
     * 대각선({@code [i][i]})은 자기 자신과의 대전이므로 채우지 않고
     * {@link Double#NaN}으로 둔다. i와 j를 바꾼 두 칸은 승점 승률의
     * 정의상 항상 합이 1이다({@code [i][j] + [j][i] == 1.0}) — 무승부는
     * 양쪽 다 0.5점씩 가져가므로 합이 그대로 보존된다.
     *
     * <p>이 Java API 값 자체는 항상 {@code Double.NaN}이다. 하지만 이
     * 행렬을 {@code roundrobin.json}으로 내보내는 {@link BundleBuilder}는
     * 대각선을 JSON {@code null}로 바꿔서 쓴다 — 자바스크립트 프론트엔드가
     * 숫자 배열 안에서 따옴표 붙은 {@code "NaN"} 문자열을 만나면
     * {@code cell.toFixed()}가 던지거나 {@code cell > x} 비교가 조용히
     * {@code false}가 되는 타입 함정에 빠지기 때문이다. 자세한 내용은
     * {@link BundleBuilder#buildRoundRobin}·{@link BundleBuilder#toWireMatrix}
     * 참고. {@code records/}처럼 자바가 다시 읽는 파일(예:
     * {@code ChallengeReport.holdoutScoreRate})은 이 변환을 거치지 않고
     * Jackson 기본 동작(따옴표 붙은 {@code "NaN"}, 별도 설정 없이 왕복)을
     * 그대로 쓴다 — 두 파일의 소비자가 다르므로 wire 표현도 다르다.
     */
    public static double[][] run(List<Bot> bots, List<Long> seeds, int width, int height) {
        int n = bots.size();
        double[][] matrix = new double[n][n];
        for (double[] row : matrix) {
            Arrays.fill(row, Double.NaN);
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Bot a = bots.get(i);
                Bot b = bots.get(j);

                List<Replay> replays = SeriesRunner.run(
                        ID_A, a::move,
                        ID_B, b::move,
                        seeds, width, height, true);

                double rateA = Standing.of(replays, ID_A).scoreRate();
                matrix[i][j] = rateA;
                matrix[j][i] = 1.0 - rateA;
            }
        }
        return matrix;
    }
}
