package arena.core;

/**
 * 경기 실행 엔진.
 *
 * 턴 판정의 핵심은 벽 집합 W(t)를 고정한 채로 두 봇의 목표 좌표를
 * 계산한 뒤 동시에 판정하는 것이다. A와 B를 바꿔도 결과가 같으므로
 * 선후공 이점이 존재하지 않는다.
 *
 * 대전 중에는 어떤 시간 제한도 걸지 않는다. 시간 기반 판정은
 * 같은 조건에 다른 결과를 내어 R1을 깨뜨린다.
 */
public final class Match {

    private Match() {}

    public static MatchResult playResult(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            long seed, int width, int height) {

        StartPositions sp = StartPositions.of(seed, width, height);
        Grid grid = new Grid(width, height);

        Point[] head = { sp.p0(), sp.p1() };
        Direction[] dir = { sp.d0(), sp.d1() };

        // 시작 칸을 즉시 벽으로 만든다. 덕분에 후진은 별도 규칙 없이
        // 자기 벽 충돌로 자연 사망한다.
        //
        // "후진"이 실제로 죽음이 되려면 시작 칸 자체만으로는 부족하다.
        // move()는 항상 새 칸으로 이동하므로(같은 칸으로 되돌아오는 경우가
        // 없으므로) 시작 칸 하나만 벽이면 반대 방향 이동은 그 옆의 새
        // 빈 칸으로 갈 뿐 아무것도 못 만난다. 봇은 이미 방향 d를 향한
        // 채로 시작한다고 보고, 그 방향으로 한 칸 오기 직전 칸
        // (시작 칸의 바로 뒤)도 함께 벽으로 만든다. 이러면 정확히 반대
        // 방향으로의 첫 이동이 바로 그 칸에 부딪혀 자기 벽 충돌이 된다.
        grid.claim(head[0], 0);
        grid.claim(head[1], 1);
        claimBehind(grid, head[0], dir[0], 0);
        claimBehind(grid, head[1], dir[1], 1);

        int maxTurns = width * height;

        for (int turn = 1; turn <= maxTurns; turn++) {
            // 1) W(t)를 고정한 채로 두 봇의 의사를 각각 묻는다.
            Direction d0 = bot0.move(viewFor(grid, head, dir, 0, turn));
            Direction d1 = bot1.move(viewFor(grid, head, dir, 1, turn));

            Point p0 = head[0].move(d0);
            Point p1 = head[1].move(d1);

            // 2) 같은 W(t)를 기준으로 동시에 판정한다.
            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);

            if (dead0 || dead1) {
                return resolve(grid, p0, p1, dead0, dead1, turn);
            }

            // 3) 생존한 봇에 대해서만 벽을 확정한다.
            grid.claim(p0, 0);
            grid.claim(p1, 1);
            head[0] = p0; head[1] = p1;
            dir[0] = d0;  dir[1] = d1;
        }

        // 매 턴 벽이 2칸씩 늘어나므로 여기 도달할 수 없다.
        return new MatchResult(-1, maxTurns, DeathReason.MAX_TURNS);
    }

    /**
     * 시작 칸의 바로 뒤 칸(현재 방향의 반대쪽 인접 칸)을 벽으로 만든다.
     * MARGIN(≥3칸) 덕분에 항상 격자 안이지만, 방어적으로 범위를 확인한다.
     */
    private static void claimBehind(Grid grid, Point head, Direction dir, int botIndex) {
        Point behind = head.move(dir.opposite());
        if (grid.inBounds(behind)) {
            grid.claim(behind, botIndex);
        }
    }

    private static GameView viewFor(Grid grid, Point[] head, Direction[] dir, int me, int turn) {
        int opp = 1 - me;
        return new GameView(
                grid.width(), grid.height(), grid.wallSnapshot(),
                head[me], dir[me], head[opp], dir[opp], turn);
    }

    private static MatchResult resolve(
            Grid grid, Point p0, Point p1, boolean dead0, boolean dead1, int turn) {

        if (dead0 && dead1) {
            DeathReason reason = p0.equals(p1)
                    ? DeathReason.HEAD_ON_COLLISION
                    : DeathReason.BOTH_DIED;
            return new MatchResult(-1, turn, reason);
        }
        if (dead0) {
            return new MatchResult(1, turn, reasonFor(grid, p0, 0));
        }
        return new MatchResult(0, turn, reasonFor(grid, p1, 1));
    }

    private static DeathReason reasonFor(Grid grid, Point p, int botIndex) {
        if (!grid.inBounds(p)) {
            return botIndex == 0 ? DeathReason.P0_OUT_OF_BOUNDS : DeathReason.P1_OUT_OF_BOUNDS;
        }
        boolean own = grid.ownerAt(p) == botIndex;
        if (botIndex == 0) {
            return own ? DeathReason.P0_HIT_OWN_WALL : DeathReason.P0_HIT_OPPONENT_WALL;
        }
        return own ? DeathReason.P1_HIT_OWN_WALL : DeathReason.P1_HIT_OPPONENT_WALL;
    }

    /**
     * 정면 충돌을 재현하는 테스트 전용 진입점.
     * 두 봇을 같은 행에 짝수 칸 간격으로 마주보게 두고 서로에게 직진시킨다.
     */
    static MatchResult headOnForTest(int width, int height) {
        Grid grid = new Grid(width, height);
        int y = height / 2;
        Point a = new Point(10, y);
        Point b = new Point(16, y);   // 거리 6 = 짝수라 정확히 가운데서 만난다

        Point[] head = { a, b };
        Direction[] dir = { Direction.RIGHT, Direction.LEFT };
        grid.claim(a, 0);
        grid.claim(b, 1);

        for (int turn = 1; turn <= width * height; turn++) {
            Point p0 = head[0].move(dir[0]);
            Point p1 = head[1].move(dir[1]);

            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);

            if (dead0 || dead1) {
                return resolve(grid, p0, p1, dead0, dead1, turn);
            }
            grid.claim(p0, 0);
            grid.claim(p1, 1);
            head[0] = p0; head[1] = p1;
        }
        return new MatchResult(-1, width * height, DeathReason.MAX_TURNS);
    }
}
