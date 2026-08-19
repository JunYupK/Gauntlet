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
 *
 * 턴별 재구성 규칙(initialGrid → 사망 판정 → 생존자만 claim)도 이유는
 * 같다: {@link #replayAndAnalyze}가 유일한 재생 루프다. 한때
 * {@code analyze}가 쓰는 턴별 분석과 최종 격자(occupancy용)를 각각
 * 별도 루프로 재생했는데, 같은 규칙(생존자 claim)을 두 곳에 베낀
 * 탓에 실제로 한쪽만 고치고 한쪽을 놓치는 결함이 났었다(리뷰가 잡은
 * 사고 — 아래 클래스 하나로 합쳐 재발을 구조적으로 막았다).
 */
public final class LossAnalyzer {

    private LossAnalyzer() {}

    /** {@link #replayAndAnalyze}의 결과. 재생은 한 번만, 필요한 두 산출물을 함께 낸다. */
    private record ReplayAnalysis(List<List<MoveAnalysis>> perBot, Grid finalGrid) {}

    public static MatchMetrics analyze(Replay replay) {
        ReplayAnalysis ra = replayAndAnalyze(replay);
        List<List<MoveAnalysis>> perBot = ra.perBot();

        int turns = replay.result().turns();
        int[][] reach = new int[2][turns];
        int[][] loss = new int[2][turns];
        int[] suicides = new int[2];

        for (int bot = 0; bot < 2; bot++) {
            List<MoveAnalysis> analyses = perBot.get(bot);
            for (int i = 0; i < turns; i++) {
                MoveAnalysis a = analyses.get(i);
                // 사망 턴엔 0: 죽은 봇에게 남은 공간은 없다. reachAfterChosen
                // 자체는 loss의 대칭을 지키려고 반사실 값을 그대로 유지한다
                // (MoveAnalysis·MatchMetrics 각각의 javadoc 참고).
                reach[bot][i] = a.fatal() ? 0 : a.reachAfterChosen();
                loss[bot][i] = a.loss();
                if (a.suicide()) suicides[bot]++;
            }
        }

        Grid finalGrid = ra.finalGrid();
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

    /**
     * 손실이 큰 수부터 정렬해 상위 {@code limit}개를 낸다. 실제로 경기를
     * 끝낸 수({@link MoveAnalysis#fatal})를 손실 크기보다 먼저 정렬 키로
     * 삼는다 — 정면 충돌처럼 {@code loss}가 낮게 나올 수 있는 사망 턴도
     * (반사실상 그 칸 자체는 안전해 보였으므로) 목록 맨 위로 끌어올려,
     * 반려 피드백과 화면 하이라이트가 실제로 진 순간을 놓치지 않게 한다.
     */
    public static List<MoveAnalysis> worstMoves(Replay replay, int botIndex, int limit) {
        return replayAndAnalyze(replay).perBot().get(botIndex).stream()
                .sorted(Comparator.comparing(MoveAnalysis::fatal)
                        .thenComparingInt(MoveAnalysis::loss)
                        .reversed())
                .limit(limit)
                .toList();
    }

    /**
     * 엔진과 같은 시작 격자에서 출발해 리플레이를 단 한 번 재생하며, 매
     * 턴 두 봇 모두의 {@link MoveAnalysis}와 매치 종료 시점의 최종 격자를
     * 함께 쌓는다. {@code analyze}·{@code worstMoves}가 필요로 하는 모든
     * 산출물이 이 한 루프에서 나온다 — 재생 규칙(사망 판정, 생존자만
     * claim)이 두 번째 사본을 가질 수 없다.
     */
    private static ReplayAnalysis replayAndAnalyze(Replay replay) {
        StartPositions sp = new StartPositions(
                replay.start0(), replay.dir0(), replay.start1(), replay.dir1());
        Grid grid = Match.initialGrid(sp, replay.width(), replay.height());

        Point[] head = { replay.start0(), replay.start1() };

        List<List<MoveAnalysis>> perBot = List.of(new ArrayList<>(), new ArrayList<>());
        int turns = replay.result().turns();

        for (int turn = 1; turn <= turns; turn++) {
            Direction[] chose = { replay.moveAt(turn, 0), replay.moveAt(turn, 1) };

            Point p0 = head[0].move(chose[0]);
            Point p1 = head[1].move(chose[1]);

            // 엔진(playInternal)과 동일한 판정: 이 시점의 grid(=W(t))만
            // 보고, 두 목표 좌표를 동시에 검사한다. 자기 벽·상대 벽·격자
            // 밖·정면 충돌(p0==p1) 전부 여기서 걸린다 — MoveAnalysis.fatal이
            // 그대로 옮기는 값이 이것이다.
            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);

            perBot.get(0).add(analyzeMove(grid, head[0], 0, chose[0], turn, dead0));
            perBot.get(1).add(analyzeMove(grid, head[1], 1, chose[1], turn, dead1));

            // playInternal과 동일하게: 매치를 끝내는 턴이라도 생존한 쪽의
            // 벽은 확정한다(D38, 스펙 §2.1 W(t+1) = W(t) ∪ {생존한 봇의
            // 새 머리 좌표}). 이 grid는 루프가 끝난 뒤 finalGrid로도
            // 쓰이므로, 여기서 죽은 쪽만 걸러내지 않으면 occupancy가
            // 생존자의 마지막 한 칸만큼 조용히 작아진다.
            if (!dead0) grid.claim(p0, 0);
            if (!dead1) grid.claim(p1, 1);

            if (dead0 || dead1) break;

            head[0] = p0;
            head[1] = p1;
        }
        return new ReplayAnalysis(perBot, grid);
    }

    /**
     * 네 방향 각각에 대해 reach를 재고, 실제 선택과의 차이를 손실로 삼는다.
     * 네 후보 모두 같은 grid 스냅샷(이 턴 시작 시점의 W(t))에서 출발해
     * 각자 독립된 복사본 위에서 평가되므로, "실제 선택 vs 최선 대안"이
     * 서로 다른 시점의 보드를 비교하는 일이 없다.
     *
     * {@code fatal}은 호출자(재생 루프)가 이미 계산해 둔 실제 사망 판정을
     * 그대로 받는다 — 대안 방향에 대해서는 상대의 실제 이번 턴 수를
     * 반사실로 되돌릴 수 없지만, 실제로 선택한 방향에 대해서는 리플레이가
     * 상대의 진짜 수를 담고 있어 반사실이 필요 없다.
     */
    private static MoveAnalysis analyzeMove(
            Grid grid, Point head, int botIndex, Direction chose, int turn, boolean fatal) {

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

        // 정면 충돌은 여기 포함하지 않는다: 상대의 이번 턴 동시 선택에
        // 달려 있고, 스펙 §2.1상 어떤 봇도 그 선택을 미리 볼 수 없다.
        // isSafe(chose)는 이 턴 시작 시점의 벽만 보므로, 정면 충돌로
        // 죽은 수는 이 조건에서 "안전"으로 잡혀 suicide=false가 된다 —
        // 의도한 동작이다(MoveAnalysis.fatal의 javadoc 참고).
        boolean suicide = !isSafe(grid, head, chose) && anySafe;
        return new MoveAnalysis(turn, chose, best, chosenReach, bestReach,
                bestReach - chosenReach, suicide, fatal);
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
}
