package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchTest {

    /** 항상 같은 방향만 내는 봇. */
    private static BotFunction always(Direction d) {
        return view -> d;
    }

    /** 즉시 죽지 않는 방향을 고정 우선순위로 고르는 봇. */
    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    @Test
    void 벽에_박은_쪽이_진다() {
        // 시드 1의 배치를 그대로 쓰되, 한쪽만 계속 위로 달려 격자 밖으로 나가게 한다.
        // (시드 7은 0번의 초기 방향이 DOWN이라 always(UP)이 첫 수부터 자기 시작
        // 칸의 바로 뒤(=UP 방향 인접 칸)를 밟아 P0_HIT_OWN_WALL로 즉사한다 — 그건
        // 이 테스트가 노리는 "격자 밖으로 나가는" 경로가 아니라 다른 테스트가
        // 검증하는 반전(reversal) 경로이므로, 초기 방향이 UP이 아닌 시드로 바꿨다.)
        MatchResult r = Match.playResult("a", always(Direction.UP), "b", avoid(), 1, 30, 30);

        assertEquals(1, r.winner(), "격자 밖으로 나간 0번이 져야 한다");
        assertEquals(DeathReason.P0_OUT_OF_BOUNDS, r.reason());
    }

    // "판정은_순서에_의존하지_않는다"(좌석을 바꿔도 승자가 같다)는 여기서
    // 지웠다. 그 테스트는 거짓인 속성을 주장했다 — 좌석 교대는 위치는
    // 그대로 두고 앉는 봇만 바꾸는 것이고(스펙 §2.2, 미러링이 아니다),
    // 두 좌석의 시작 위치·방향은 시드로부터 독립적으로 뽑힌다. 시드
    // 1..50에서는 우연히 승자가 안 바뀌었을 뿐, 시드 402/412/423에서는
    // 좌석을 바꾸면 실제로 승자가 바뀐다.
    //
    // 스펙 §2.1이 진짜로 요구하는 순서 독립성 — 한 턴 안에서 W(t)를 고정한
    // 채 봇0을 먼저 묻든 봇1을 먼저 묻든 결과가 같다는 것 — 은
    // MatchPropertyTest의 속성_동시_사망은_언제나_무승부다와
    // 속성_같은_칸_동시_진입은_인덱스와_무관하게_무승부다가 대신 고정한다.

    @Test
    void 같은_시드와_같은_봇은_항상_같은_결과를_낸다() {
        for (long seed = 1; seed <= 20; seed++) {
            MatchResult first = Match.playResult("a", avoid(), "b", avoid(), seed, 30, 30);
            for (int i = 0; i < 5; i++) {
                assertEquals(first, Match.playResult("a", avoid(), "b", avoid(), seed, 30, 30),
                        "시드 " + seed + "이 재현되지 않았다");
            }
        }
    }

    @Test
    void 경기는_반드시_900턴_이내에_끝난다() {
        for (long seed = 1; seed <= 50; seed++) {
            MatchResult r = Match.playResult("a", avoid(), "b", avoid(), seed, 30, 30);
            assertTrue(r.turns() <= 900, "시드 " + seed + "이 " + r.turns() + "턴이나 갔다");
            assertNotEquals(DeathReason.MAX_TURNS, r.reason(),
                    "시드 " + seed + "이 턴 상한에 걸렸다 — 종료 보장이 깨졌다");
        }
    }

    @Test
    void 시작_칸이_벽이라_후진하면_자기_벽에_박는다() {
        StartPositions sp = StartPositions.of(3, 30, 30);
        // 0번 봇이 처음부터 반대로 간다 = 시작 칸으로 되돌아간다.
        MatchResult r = Match.playResult(
                "back", always(sp.d0().opposite()), "avoid", avoid(), 3, 30, 30);

        assertEquals(1, r.winner());
        assertEquals(DeathReason.P0_HIT_OWN_WALL, r.reason());
        assertEquals(1, r.turns(), "첫 턴에 끝나야 한다");
    }

    @Test
    void 시작_칸이_벽이라_후진하면_자기_벽에_박는다_1번_자리에서도() {
        // 위 테스트의 거울상: 반전하는 봇이 0번이 아니라 1번 자리에 있어도
        // 사유가 P1_HIT_OWN_WALL로 정확히 배정되는지 확인한다. reasonFor()의
        // botIndex==0/1 분기가 뒤바뀌어도(예: 상수가 전치되어도) 지금까지의
        // 테스트만으로는 아무것도 걸리지 않는다 — 이 테스트가 그 구멍을 막는다.
        StartPositions sp = StartPositions.of(3, 30, 30);
        MatchResult r = Match.playResult(
                "avoid", avoid(), "back", always(sp.d1().opposite()), 3, 30, 30);

        assertEquals(0, r.winner());
        assertEquals(DeathReason.P1_HIT_OWN_WALL, r.reason());
        assertEquals(1, r.turns(), "첫 턴에 끝나야 한다");
    }

    @Test
    void 양쪽이_같은_턴에_각자_다른_이유로_죽으면_BOTH_DIED다() {
        // 둘 다 시작하자마자 반전한다 — 서로 다른 칸에서 각자 자기 벽에
        // 부딪히므로 같은 칸으로의 동시 진입(HEAD_ON_COLLISION)이 아니라
        // BOTH_DIED여야 한다. 이 분기도 지금까지 어떤 테스트에서도 닿지
        // 않았다.
        StartPositions sp = StartPositions.of(3, 30, 30);
        MatchResult r = Match.playResult(
                "back0", always(sp.d0().opposite()),
                "back1", always(sp.d1().opposite()),
                3, 30, 30);

        assertEquals(-1, r.winner());
        assertEquals(DeathReason.BOTH_DIED, r.reason());
        assertEquals(1, r.turns(), "첫 턴에 끝나야 한다");
    }

    @Test
    void 양쪽이_같은_칸에_동시_진입하면_무승부다() {
        // 두 봇을 마주보게 두고 서로에게 직진시킨다. 판정식을 테스트에서
        // 다시 베끼지 않도록, Match의 실제 판정 루프를 그대로 태우는
        // playResult(BotFunction, BotFunction, StartPositions, int, int)
        // 오버로드를 직접 호출한다 — 시드 기반 playResult가 위임하는 바로
        // 그 메서드다. 위치는 폭의 중앙을 기준으로 잡아, 좁은 격자에서도
        // Grid.claim()이 범위를 벗어나지 않는다.
        int width = 30, height = 30;
        int y = height / 2;
        int cx = width / 2;
        StartPositions headOn = new StartPositions(
                new Point(cx - 3, y), Direction.RIGHT,
                new Point(cx + 3, y), Direction.LEFT);   // 거리 6 = 짝수라 정확히 가운데서 만난다

        MatchResult r = Match.playResult(
                always(Direction.RIGHT), always(Direction.LEFT), headOn, width, height);

        assertEquals(-1, r.winner());
        assertEquals(DeathReason.HEAD_ON_COLLISION, r.reason());
    }

    // Task 8 컨트롤러 룰링: 초기 보드 구성 규칙(시작 칸 2개 + 그 바로 뒤 칸 2개)을
    // Match.initialGrid로 공개 API화한다. arena-diagnostics의 리플레이 재구성이
    // 이 규칙을 복사하지 않고 그대로 호출하게 하기 위해서다 — 사본이 갈라지는
    // 사고(D36 headOnForTest)를 다시 만들지 않는다. 아래 두 테스트는 그
    // 공개 API 자체가 claimBehind와 같은 결과를 내는지 직접 고정한다.

    @Test
    void initialGrid는_정확히_4칸의_벽을_갖는다() {
        StartPositions sp = StartPositions.of(3, 30, 30);
        Grid grid = Match.initialGrid(sp, 30, 30);

        int wallCount = 0;
        for (boolean[] row : grid.wallSnapshot()) {
            for (boolean w : row) {
                if (w) wallCount++;
            }
        }
        assertEquals(4, wallCount, "시작 칸 2개 + 그 바로 뒤 칸 2개 = 4칸이어야 한다");
    }

    @Test
    void initialGrid에서_시작_위치로부터_후진하면_그_칸이_이미_자기_벽이다() {
        StartPositions sp = StartPositions.of(3, 30, 30);
        Grid grid = Match.initialGrid(sp, 30, 30);

        Point back0 = sp.p0().move(sp.d0().opposite());
        assertTrue(grid.isWall(back0), "0번의 후진 목적지가 아직 벽이 아니다");
        assertEquals(0, grid.ownerAt(back0), "0번의 후진 목적지는 0번 소유여야 한다");

        Point back1 = sp.p1().move(sp.d1().opposite());
        assertTrue(grid.isWall(back1), "1번의 후진 목적지가 아직 벽이 아니다");
        assertEquals(1, grid.ownerAt(back1), "1번의 후진 목적지는 1번 소유여야 한다");
    }
}
