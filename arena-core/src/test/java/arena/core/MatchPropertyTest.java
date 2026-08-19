package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 엔진의 불변식을 시드 500개로 검증한다.
 * 결정론이므로 실패하면 그 시드로 언제든 재현할 수 있다.
 *
 * 브리프의 "속성_대칭성_좌석을_바꿔도_승자가_같다"는 여기 없다. 그 속성은
 * 거짓이다 — 좌석 교대는 위치는 그대로 두고 앉는 봇만 바꾸는 것이고
 * (스펙 §2.2, 미러링이 아니다), 두 좌석의 시작 위치·방향은 시드로부터
 * 독립적으로 뽑힌다. 실제로 시드 402, 412, 423에서 좌석을 바꾸면 승자가
 * 바뀐다(1..50에서는 우연히 안 바뀌었을 뿐이다). 스펙 §2.1이 실제로
 * 요구하는 것은 "한 턴 안에서 W(t)를 고정한 채 어느 봇을 먼저 묻든 결과가
 * 같다"는 순서 독립성이지, "좌석을 바꿔도 결과가 같다"는 대칭성이 아니다.
 * 아래 두 속성이 §2.1을 대신 고정한다.
 */
class MatchPropertyTest {

    private static final int SEEDS = 500;

    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    private static BotFunction hugLeft() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    private static BotFunction always(Direction d) {
        return view -> d;
    }

    /** 자기 시작 방향의 정반대로만 움직이는 봇. claimBehind 덕분에 시드와
     *  무관하게 항상 첫 턴에 자기 벽에 부딪혀 죽는다. */
    private static BotFunction reverse() {
        return view -> view.myDir().opposite();
    }

    @Test
    void 속성_동시_사망은_언제나_무승부다() {
        // 양쪽 다 reverse()를 쓰면 각자 자기 시작 칸의 바로 뒤(claimBehind가
        // 벽으로 만들어둔 칸)에 부딪혀, 시드와 무관하게 항상 턴 1에 동시
        // 사망한다. 두 봇의 시작 위치는 MIN_DISTANCE(10) 이상 떨어져 있으므로
        // "자기 시작 칸 바로 뒤" 두 칸이 우연히 같은 칸일 수 없다 — 그래서
        // 이 경로는 HEAD_ON_COLLISION이 아니라 BOTH_DIED로, 서로 다른 이유로
        // 동시에 죽는 경우를 시드 500개 전부에서 강제로 만들어낸다.
        int guaranteedDoubleDeaths = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            MatchResult r = Match.playResult("r0", reverse(), "r1", reverse(), seed, 30, 30);

            assertEquals(1, r.turns(), "시드 " + seed + ": 반전 봇은 턴 1에 죽어야 한다");
            assertEquals(DeathReason.BOTH_DIED, r.reason(),
                    "시드 " + seed + ": 서로 다른 칸에서 각자 죽어야 BOTH_DIED다");
            assertEquals(-1, r.winner(),
                    "시드 " + seed + ": 동시 사망인데 승자가 있다 (winner=" + r.winner() + ")");
            guaranteedDoubleDeaths++;
        }
        assertEquals(SEEDS, guaranteedDoubleDeaths,
                "동시 사망 시나리오가 모든 시드에서 재현되어야 한다 — 하나라도 빠지면 이 속성은 공허하다");

        // 위는 항상 같은 경로(자기 벽 충돌)로만 동시 사망을 만든다. 실제
        // 대국을 흉내 낸 다양한 봇 조합에서도 우연히 동시 사망이 나오면
        // 여전히 무승부여야 한다는 걸 함께 확인한다 (이 부분은 발생 횟수를
        // 요구하지 않는다 — 위에서 이미 비공허성을 확보했다).
        BotFunction[] naturalBots = { avoid(), hugLeft(), always(Direction.UP), reverse() };
        for (long seed = 1; seed <= 100; seed++) {
            for (BotFunction b0 : naturalBots) {
                for (BotFunction b1 : naturalBots) {
                    MatchResult r = Match.playResult("x", b0, "y", b1, seed, 30, 30);
                    if (r.reason() == DeathReason.BOTH_DIED
                            || r.reason() == DeathReason.HEAD_ON_COLLISION) {
                        assertEquals(-1, r.winner(),
                                "시드 " + seed + ": 동시 사망(" + r.reason() + ")인데 승자가 있다");
                    }
                }
            }
        }
    }

    @Test
    void 속성_같은_칸_동시_진입은_인덱스와_무관하게_무승부다() {
        // 시드에 기대지 않고 정면 충돌을 직접 구성한다: package-private
        // playResult(BotFunction, BotFunction, StartPositions, int, int)
        // 오버로드로 두 봇을 마주보게 하고 정확히 가운데 칸에서 만나게 한다.
        int width = 30, height = 30;
        int y = height / 2;
        int cx = width / 2;
        Point left = new Point(cx - 3, y);
        Point right = new Point(cx + 3, y);   // 거리 6 = 짝수라 정확히 가운데서 만난다

        StartPositions original = new StartPositions(left, Direction.RIGHT, right, Direction.LEFT);
        MatchResult r1 = Match.playResult(
                always(Direction.RIGHT), always(Direction.LEFT), original, width, height);

        assertEquals(-1, r1.winner(), "정면 충돌인데 승자가 있다");
        assertEquals(DeathReason.HEAD_ON_COLLISION, r1.reason());

        // 좌석을 교환한다: 칸 위치는 그대로 두고, 그 자리에 앉는 봇 함수만
        // 바꾼다. 0번 자리에 있던 봇이 1번 자리로, 1번 자리에 있던 봇이
        // 0번 자리로 — 방향도 함께 옮겨서 여전히 같은 칸에서 마주친다.
        // 엔진이 0번을 먼저 평가하든 1번을 먼저 평가하든(스펙 §2.1) 결과가
        // 같아야 하므로, 인덱스를 뒤바꿔 재구성해도 여전히 무승부여야 한다.
        StartPositions seatsSwapped = new StartPositions(right, Direction.LEFT, left, Direction.RIGHT);
        MatchResult r2 = Match.playResult(
                always(Direction.LEFT), always(Direction.RIGHT), seatsSwapped, width, height);

        assertEquals(-1, r2.winner(), "인덱스를 바꿔 재구성해도 승자가 있다");
        assertEquals(DeathReason.HEAD_ON_COLLISION, r2.reason());
    }

    @Test
    void 속성_종료_보장_어떤_조합도_격자_칸수_이내에_끝난다() {
        BotFunction[] bots = { avoid(), hugLeft(), v -> Direction.UP, v -> v.myDir() };

        for (long seed = 1; seed <= 100; seed++) {
            for (BotFunction b0 : bots) {
                for (BotFunction b1 : bots) {
                    MatchResult r = Match.playResult("x", b0, "y", b1, seed, 30, 30);
                    assertTrue(r.turns() <= 900,
                            "시드 " + seed + ": " + r.turns() + "턴");
                    assertNotEquals(DeathReason.MAX_TURNS, r.reason(),
                            "시드 " + seed + ": 턴 상한에 걸렸다");
                }
            }
        }
    }

    @Test
    void 속성_벽_단조성_벽은_줄지_않고_생존자_수만큼_늘어난다() {
        for (long seed = 1; seed <= 100; seed++) {
            final long s = seed;   // 람다가 캡처하려면 effectively final이어야 한다
            // 턴 1 시작 전에 이미 4칸이 벽이다: 각 봇의 시작 칸(2) +
            // claimBehind가 미리 벽으로 만들어두는 시작 칸 바로 뒤(2).
            int[] previous = { 4 };

            Match.playResult("a", avoid(), "b", hugLeft(), seed, 30, 30,
                    (turn, gridAfter, heads) -> {
                        int count = countWalls(gridAfter);
                        assertEquals(previous[0] + 2, count,
                                "시드 " + s + " 턴 " + turn + ": 벽이 2칸씩 늘지 않았다");
                        previous[0] = count;
                    });
        }
    }

    private static int countWalls(Grid grid) {
        int[][] owner = grid.ownerSnapshot();
        int count = 0;
        for (int[] row : owner) {
            for (int cell : row) {
                if (cell != Grid.EMPTY) count++;
            }
        }
        return count;
    }
}
