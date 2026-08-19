package arena.core;

/**
 * 경기 실행 엔진.
 *
 * 턴 판정의 핵심은 벽 집합 W(t)를 고정한 채로 두 봇의 목표 좌표를
 * 계산한 뒤 동시에 판정하는 것이다. 한 턴 안에서 봇0을 먼저 묻든
 * 봇1을 먼저 묻든 결과는 같다 — 어느 쪽 목표 좌표도 상대의 이번 턴
 * 결정을 보지 못하고, 생사 판정도 같은 W(t) 스냅샷을 기준으로
 * 동시에 이뤄지기 때문이다. 이것이 이 엔진이 실제로 보장하는
 * 순서 독립성이다.
 *
 * 주의: 이것은 "자리(seat)를 바꿔도 결과가 같다"는 뜻이 아니다.
 * 0번과 1번의 시작 위치·방향은 시드로부터 각자 독립적으로 뽑히므로
 * (스펙 §2.2) 두 좌석은 대칭이 아니다. 좌석 교대는 위치는 그대로
 * 두고 그 자리에 앉는 봇만 바꾸는 것이며 미러링이 아니다 — 즉
 * 어느 봇이 0번 자리에 앉고 어느 봇이 1번 자리에 앉느냐를 바꾸면
 * 그건 애초에 다른 경기다.
 *
 * 대전 중에는 어떤 시간 제한도 걸지 않는다. 시간 기반 판정은
 * 같은 조건에 다른 결과를 내어 R1을 깨뜨린다.
 */
public final class Match {

    private Match() {}

    /**
     * 턴이 끝날 때마다(생존 여부와 무관하게, 매치를 끝내는 턴을 포함해서)
     * 호출된다. 테스트와 진단이 엔진 내부를 관찰하는 통로.
     *
     * {@code gridAfter}는 엔진의 살아있는 인스턴스가 아니라 매 호출마다
     * 뜨는 방어적 복사본이다({@link Grid#copy()}) — {@link Grid#claim}은
     * public이고 범위 검사도 하지 않으므로, 관찰자가 실수로든 의도적으로든
     * 엔진 상태를 훼손할 수 없게 막는다. 관찰자를 넘기지 않은 호출(내부
     * no-op 관찰자)에서는 이 복사조차 만들지 않는다 — 챔피언전처럼 아무도
     * 지켜보지 않는 대량 경기에서 턴마다 격자를 복사하는 비용을 물리지
     * 않기 위해서다.
     */
    @FunctionalInterface
    public interface TurnObserver {
        void onTurn(int turn, Grid gridAfter, Point[] heads);
    }

    private static final TurnObserver NO_OP_OBSERVER = (turn, grid, heads) -> {};

    public static MatchResult playResult(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            long seed, int width, int height) {
        return playResult(id0, bot0, id1, bot1, seed, width, height, NO_OP_OBSERVER);
    }

    /** 관찰자를 받는 진입점. 시드로부터 배치를 뽑아 위 오버로드가 위임한다. */
    public static MatchResult playResult(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            long seed, int width, int height,
            TurnObserver observer) {

        StartPositions sp = StartPositions.of(seed, width, height);
        return playResult(bot0, bot1, sp, width, height, observer);
    }

    /**
     * 시작 배치를 직접 지정하는 진입점. 시드로 배치를 뽑는
     * {@link #playResult(String, BotFunction, String, BotFunction, long, int, int)}도
     * 결국 이 메서드로 위임한다 — 판정 루프가 딱 하나만 존재하므로,
     * 시드 기반 경기와 배치를 직접 지정한 경기(예: 정면 충돌·반전
     * 재현 테스트)가 서로 다른 규칙으로 갈라질 수 없다.
     */
    static MatchResult playResult(
            BotFunction bot0, BotFunction bot1,
            StartPositions sp, int width, int height) {
        return playResult(bot0, bot1, sp, width, height, NO_OP_OBSERVER);
    }

    static MatchResult playResult(
            BotFunction bot0, BotFunction bot1,
            StartPositions sp, int width, int height,
            TurnObserver observer) {

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

            // 2) 같은 W(t)를 기준으로 동시에 판정한다. 판정은 이 시점의
            // grid(=W(t))만 보고 이뤄진다 — 3)에서의 벽 확정보다 먼저다.
            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);

            // 3) 생존한 봇에 대해서만 벽을 확정한다: W(t+1) = W(t) ∪
            // {생존한 봇의 새 머리 좌표} (스펙 §2.1 W(t) 정의, §7.1 벽
            // 단조성 |W(t+1)|-|W(t)| == 생존 봇 수). 이 턴에 매치가
            // 끝나더라도(한쪽만 죽거나 둘 다 죽거나) 예외가 아니다 —
            // 살아남은 쪽이 있다면 그 머리는 여전히 벽이 된다. p0 == p1인
            // 동시 진입은 두 판정식이 대칭이라 dead0·dead1이 항상 함께
            // true이므로, 살아있는 쪽의 좌표만 벽이 되고 죽은 쪽의 좌표가
            // 실수로 덮어씌워질 일은 없다.
            if (!dead0) grid.claim(p0, 0);
            if (!dead1) grid.claim(p1, 1);

            if (observer != NO_OP_OBSERVER) {
                observer.onTurn(turn, grid.copy(), new Point[]{ p0, p1 });
            }

            if (dead0 || dead1) {
                return resolve(grid, p0, p1, dead0, dead1, turn);
            }

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
}
