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

    @Test
    void 판정은_순서에_의존하지_않는다() {
        for (long seed = 1; seed <= 50; seed++) {
            MatchResult ab = Match.playResult("a", avoid(), "b", always(Direction.UP), seed, 30, 30);
            MatchResult ba = Match.playResult("b", always(Direction.UP), "a", avoid(), seed, 30, 30);

            // 좌석을 바꿔도 "누가 이겼는가"는 같아야 한다.
            int winnerAB = ab.winner() < 0 ? -1 : (ab.winner() == 0 ? 0 : 1);
            int winnerBA = ba.winner() < 0 ? -1 : (ba.winner() == 0 ? 1 : 0);
            assertEquals(winnerAB, winnerBA, "시드 " + seed + "에서 순서 의존성이 발견됐다");
        }
    }

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
    void 양쪽이_같은_칸에_동시_진입하면_무승부다() {
        // 두 봇을 마주보게 두고 서로에게 직진시킨다.
        MatchResult r = Match.headOnForTest(30, 30);
        assertEquals(-1, r.winner());
        assertEquals(DeathReason.HEAD_ON_COLLISION, r.reason());
    }
}
