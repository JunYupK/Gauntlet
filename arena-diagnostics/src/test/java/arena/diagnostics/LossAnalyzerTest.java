package arena.diagnostics;

import arena.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LossAnalyzerTest {

    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    /** 대안이 있는데도 첫 턴에 후진해 자살하는 봇. */
    private static BotFunction suicidal() {
        return view -> view.myDir().opposite();
    }

    @Test
    void 손실은_항상_0_이상이다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        for (int bot = 0; bot < 2; bot++) {
            for (int loss : m.loss()[bot]) {
                assertTrue(loss >= 0, "손실이 음수다: " + loss);
            }
        }
    }

    @Test
    void 자살한_봇은_자멸로_기록된다() {
        Replay r = Match.play("suicide", suicidal(), "avoid", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        assertEquals(1.0, m.suicideRate()[0], 1e-9,
                "첫 턴에 대안을 두고 자살했는데 자멸로 안 잡혔다");
        assertEquals(0.0, m.suicideRate()[1], 1e-9);
    }

    @Test
    void 자살한_수가_최악의_수로_뽑힌다() {
        Replay r = Match.play("suicide", suicidal(), "avoid", avoid(), 5, 30, 30);
        List<MoveAnalysis> worst = LossAnalyzer.worstMoves(r, 0, 3);

        assertFalse(worst.isEmpty());
        MoveAnalysis top = worst.get(0);
        assertEquals(0, top.reachAfterChosen(), "자살한 수의 reach는 0이어야 한다");
        assertTrue(top.loss() > 0, "자살했는데 손실이 0이다");
        assertTrue(top.suicide());
    }

    @Test
    void 점유율은_자기_벽_칸수를_전체로_나눈_값이다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        double total = m.occupancy()[0] + m.occupancy()[1];
        assertTrue(total > 0 && total <= 1.0, "점유율 합이 이상하다: " + total);
    }

    @Test
    void reach_배열의_길이는_턴_수와_같다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        assertEquals(r.result().turns(), m.reach()[0].length);
        assertEquals(r.result().turns(), m.loss()[0].length);
    }

    /**
     * 재구성한 최종 격자가 엔진의 실제 최종 격자와 벽 칸수까지 일치하는지
     * 직접 대조한다. suicide vs avoid 조합은 한쪽(0번)만 죽고 다른 쪽은
     * 살아남는 비대칭 종료라, 매치를 끝내는 턴에 생존자의 마지막 한 칸을
     * 놓치면 여기서 정확히 1칸 어긋난다(playInternal의 D38 규칙:
     * 매치를 끝내는 턴이라도 생존자의 새 좌표는 벽으로 확정한다).
     */
    @Test
    void 점유율의_분자합은_엔진이_실제로_낸_최종_벽_칸수와_같다() {
        int[] finalWallCount = { -1 };
        Match.playResult("suicide", suicidal(), "avoid", avoid(), 5, 30, 30,
                (turn, gridAfter, heads) -> {
                    int count = 0;
                    for (boolean[] row : gridAfter.wallSnapshot()) {
                        for (boolean w : row) {
                            if (w) count++;
                        }
                    }
                    finalWallCount[0] = count;
                });
        assertTrue(finalWallCount[0] > 0, "관찰자가 한 번도 안 불렸다");

        Replay r = Match.play("suicide", suicidal(), "avoid", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        int cells = r.width() * r.height();
        long reconstructed = Math.round((m.occupancy()[0] + m.occupancy()[1]) * cells);

        assertEquals(finalWallCount[0], reconstructed,
                "재구성한 최종 벽 칸수가 엔진의 실제 값과 다르다");
    }
}
