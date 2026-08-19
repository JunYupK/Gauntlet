package arena.diagnostics;

import arena.core.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 리플레이를 재생하며 각 수의 손실을 잰다.
 *
 * 이 클래스가 내는 값 하나로 셋을 해결한다.
 *   1. 반려 피드백  — 패배 경기의 손실 상위 수
 *   2. 자멸률       — R3 보조 지표
 *   3. 화면 하이라이트 — "이 순간 봇이 졌습니다"
 *
 * 보드 재구성은 반드시 {@link Match#initialGrid}로 시작한다. 엔진은
 * 매치 시작 시 시작 칸 2개뿐 아니라 그 바로 뒤 칸 2개까지 벽으로
 * 확정한다(첫 턴 반전이 자기 벽 충돌이 되게 하려고). 이 규칙을 여기서
 * 다시 베끼면 재구성한 보드가 턴 0부터 엔진의 실제 보드와 어긋나고,
 * 그 위에서 계산하는 reach·loss·occupancy·suicideRate가 전부 조용히
 * 틀려진다 — 그래서 시작 격자는 arena-core에 유일하게 정의된 그
 * public 메서드를 그대로 호출한다.
 */
public final class LossAnalyzer {

    private LossAnalyzer() {}

    public static MatchMetrics analyze(Replay replay) {
        List<List<MoveAnalysis>> perBot = replayAndAnalyze(replay);

        int turns = replay.result().turns();
        int[][] reach = new int[2][turns];
        int[][] loss = new int[2][turns];
        int[] suicides = new int[2];

        for (int bot = 0; bot < 2; bot++) {
            List<MoveAnalysis> analyses = perBot.get(bot);
            for (int i = 0; i < turns; i++) {
                MoveAnalysis a = analyses.get(i);
                reach[bot][i] = a.reachAfterChosen();
                loss[bot][i] = a.loss();
                if (a.suicide()) suicides[bot]++;
            }
        }

        Grid finalGrid = replayToFinalGrid(replay);
        int cells = replay.width() * replay.height();
        int[] owned = new int[2];
        int[][] owner = finalGrid.ownerSnapshot();
        for (int[] row : owner) {
            for (int cell : row) {
                if (cell == 0) owned[0]++;
                else if (cell == 1) owned[1]++;
            }
        }

        return new MatchMetrics(reach, loss,
                new double[]{ (double) owned[0] / cells, (double) owned[1] / cells },
                new double[]{ (double) suicides[0] / turns, (double) suicides[1] / turns });
    }

    public static List<MoveAnalysis> worstMoves(Replay replay, int botIndex, int limit) {
        return replayAndAnalyze(replay).get(botIndex).stream()
                .sorted(Comparator.comparingInt(MoveAnalysis::loss).reversed())
                .limit(limit)
                .toList();
    }

    /** 엔진과 같은 시작 격자에서 출발해, 매 턴 두 봇 모두의 MoveAnalysis를 쌓는다. */
    private static List<List<MoveAnalysis>> replayAndAnalyze(Replay replay) {
        StartPositions sp = new StartPositions(
                replay.start0(), replay.dir0(), replay.start1(), replay.dir1());
        Grid grid = Match.initialGrid(sp, replay.width(), replay.height());

        Point[] head = { replay.start0(), replay.start1() };

        List<List<MoveAnalysis>> perBot = List.of(new ArrayList<>(), new ArrayList<>());
        int turns = replay.result().turns();

        for (int turn = 1; turn <= turns; turn++) {
            Direction[] chose = { replay.moveAt(turn, 0), replay.moveAt(turn, 1) };

            for (int bot = 0; bot < 2; bot++) {
                perBot.get(bot).add(analyzeMove(grid, head[bot], bot, chose[bot], turn));
            }

            Point p0 = head[0].move(chose[0]);
            Point p1 = head[1].move(chose[1]);

            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);

            // playInternal과 동일하게: 매치를 끝내는 턴이라도 생존한 쪽의
            // 벽은 확정한다(D38). 여기서 죽은 쪽만 걸러내지 않으면 이
            // 함수가 끝난 뒤 grid를 재사용할 코드가 생겼을 때 엔진과
            // 조용히 어긋난다.
            if (!dead0) grid.claim(p0, 0);
            if (!dead1) grid.claim(p1, 1);

            if (dead0 || dead1) break;

            head[0] = p0;
            head[1] = p1;
        }
        return perBot;
    }

    /**
     * 네 방향 각각에 대해 reach를 재고, 실제 선택과의 차이를 손실로 삼는다.
     * 네 후보 모두 같은 grid 스냅샷(이 턴 시작 시점의 W(t))에서 출발해
     * 각자 독립된 복사본 위에서 평가되므로, "실제 선택 vs 최선 대안"이
     * 서로 다른 시점의 보드를 비교하는 일이 없다.
     */
    private static MoveAnalysis analyzeMove(
            Grid grid, Point head, int botIndex, Direction chose, int turn) {

        int bestReach = -1;
        Direction best = chose;
        int chosenReach = 0;
        boolean anySafe = false;

        for (Direction d : Direction.values()) {
            boolean safe = isSafe(grid, head, d);
            if (safe) anySafe = true;

            int r = reachAfter(grid, head, botIndex, d);
            if (r > bestReach) {
                bestReach = r;
                best = d;
            }
            if (d == chose) chosenReach = r;
        }

        boolean suicide = !isSafe(grid, head, chose) && anySafe;
        return new MoveAnalysis(turn, chose, best, chosenReach, bestReach,
                bestReach - chosenReach, suicide);
    }

    private static boolean isSafe(Grid grid, Point head, Direction d) {
        Point p = head.move(d);
        return grid.inBounds(p) && !grid.isWall(p);
    }

    /** 그 방향으로 갔을 때의 reach. 즉시 사망이면 0. */
    private static int reachAfter(Grid grid, Point head, int botIndex, Direction d) {
        if (!isSafe(grid, head, d)) return 0;

        Point p = head.move(d);
        Grid next = grid.copy();
        next.claim(p, botIndex);
        return FloodFill.reach(next, p);
    }

    /**
     * playInternal의 벽 확정 순서를 그대로 따른다: 판정(dead0/dead1)이
     * 끝나면, 매치를 끝내는 턴이라도 생존한 쪽의 새 좌표는 벽으로
     * 확정한다(D38, 스펙 §2.1 W(t+1) = W(t) ∪ {생존한 봇의 새 머리 좌표}).
     * 죽은 쪽만 걸러내고 break하면 생존자의 마지막 한 칸이 최종 격자에서
     * 빠져 occupancy가 조용히 1칸 작게 나온다.
     */
    private static Grid replayToFinalGrid(Replay replay) {
        StartPositions sp = new StartPositions(
                replay.start0(), replay.dir0(), replay.start1(), replay.dir1());
        Grid grid = Match.initialGrid(sp, replay.width(), replay.height());

        Point[] head = { replay.start0(), replay.start1() };

        for (int turn = 1; turn <= replay.result().turns(); turn++) {
            Point p0 = head[0].move(replay.moveAt(turn, 0));
            Point p1 = head[1].move(replay.moveAt(turn, 1));

            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);

            if (!dead0) grid.claim(p0, 0);
            if (!dead1) grid.claim(p1, 1);

            if (dead0 || dead1) break;

            head[0] = p0;
            head[1] = p1;
        }
        return grid;
    }
}
