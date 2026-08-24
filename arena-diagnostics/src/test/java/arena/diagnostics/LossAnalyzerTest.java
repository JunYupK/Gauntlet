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

    /**
     * 상대 머리 쪽으로 곧장 다가간다(주축 우선, 막히면 부축, 그마저 막히면
     * 아무 안전한 방향). 두 봇을 서로 맞붙이면 정면 충돌(HEAD_ON_COLLISION)을
     * 만들어내는 데 쓴다.
     */
    private static Direction towardOpponent(GameView view) {
        int dx = view.oppHead().x() - view.myHead().x();
        int dy = view.oppHead().y() - view.myHead().y();

        Direction primary;
        Direction secondary;
        if (Math.abs(dx) >= Math.abs(dy)) {
            primary = dx >= 0 ? Direction.RIGHT : Direction.LEFT;
            secondary = dy >= 0 ? Direction.DOWN : Direction.UP;
        } else {
            primary = dy >= 0 ? Direction.DOWN : Direction.UP;
            secondary = dx >= 0 ? Direction.RIGHT : Direction.LEFT;
        }

        if (!view.isDeadly(primary)) return primary;
        if (!view.isDeadly(secondary)) return secondary;
        for (Direction d : Direction.values()) {
            if (!view.isDeadly(d)) return d;
        }
        return view.myDir();
    }

    private static BotFunction chase() {
        return LossAnalyzerTest::towardOpponent;
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
        // 엔진이 실제로 낸 0번 소유 칸수를 관찰자로 직접 잡아, occupancy()[0]과
        // 대조한다 — 이게 없으면 LossAnalyzer.analyze의 owner==0/owner==1 카운팅
        // 루프가 뒤바뀌어도(0번과 1번을 서로 바꿔 세도) 아래 total 검사만으로는
        // 걸리지 않는다.
        int[] owner0Count = { -1 };
        Match.playResult("a", avoid(), "b", avoid(), 5, 30, 30,
                (turn, gridAfter, heads) -> {
                    int count = 0;
                    for (int[] row : gridAfter.ownerSnapshot()) {
                        for (int cell : row) {
                            if (cell == 0) count++;
                        }
                    }
                    owner0Count[0] = count;
                });
        assertTrue(owner0Count[0] > 0, "관찰자가 한 번도 안 불렸다");

        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        int cells = r.width() * r.height();
        assertEquals(owner0Count[0], Math.round(m.occupancy()[0] * cells),
                "occupancy()[0]이 엔진의 실제 0번 소유 칸수와 다르다");

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

    /**
     * 리뷰 룰링(D41): 정면 충돌로 죽은 수는 fatal=true이지만 suicide=false여야
     * 한다 — 상대의 동시 선택은 스펙 §2.1상 어느 봇도 미리 볼 수 없으므로
     * 자멸로 돌리지 않는다. 그리고 그 수는 반사실 reach가 양수로 나올 수
     * 있어 loss만으로는 상위에 안 뽑힐 수 있지만, worstMoves는 fatal을
     * loss보다 먼저 정렬 키로 쓰므로 limit=1이어도 반드시 뽑혀야 한다.
     * MatchMetrics.reach는 그 사망 턴에 0이어야 한다(죽은 봇에게 남은
     * 공간은 없다).
     *
     * 시드를 고정하지 않고 1..500에서 실제로 정면 충돌이 나는 첫 시드를
     * 찾는다 — 매번 같은 범위를 스캔하므로 재현 가능하다(R1). "곧장
     * 서로에게 다가가는" 두 봇을 맞붙이면 흔히 발생한다.
     */
    @Test
    void 정면_충돌로_죽은_수는_fatal이지만_suicide는_아니다() {
        long collisionSeed = -1;
        for (long seed = 1; seed <= 500; seed++) {
            MatchResult result = Match.playResult("a", chase(), "b", chase(), seed, 30, 30);
            if (result.reason() == DeathReason.HEAD_ON_COLLISION) {
                collisionSeed = seed;
                break;
            }
        }
        assertTrue(collisionSeed > 0, "시드 1..500 안에서 정면 충돌 사례를 못 찾았다");

        Replay r = Match.play("a", chase(), "b", chase(), collisionSeed, 30, 30);
        assertEquals(DeathReason.HEAD_ON_COLLISION, r.result().reason());
        int lastTurn = r.result().turns();

        for (int bot = 0; bot < 2; bot++) {
            List<MoveAnalysis> worst = LossAnalyzer.worstMoves(r, bot, 1);
            assertEquals(1, worst.size());
            MoveAnalysis top = worst.get(0);

            assertEquals(lastTurn, top.turn(),
                    "봇 " + bot + ": worstMoves(limit=1)이 실제로 경기를 끝낸 턴을 안 뽑았다");
            assertTrue(top.fatal(), "봇 " + bot + ": 정면 충돌 턴인데 fatal이 아니다");
            assertFalse(top.suicide(),
                    "봇 " + bot + ": 정면 충돌은 상대 선택에 달렸는데 suicide로 잡혔다");
        }

        MatchMetrics m = LossAnalyzer.analyze(r);
        assertEquals(0, m.reach()[0][lastTurn - 1], "사망 턴의 reach는 0이어야 한다(봇 0)");
        assertEquals(0, m.reach()[1][lastTurn - 1], "사망 턴의 reach는 0이어야 한다(봇 1)");
    }
}
