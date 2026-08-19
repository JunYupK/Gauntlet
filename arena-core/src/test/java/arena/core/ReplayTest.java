package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayTest {

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
    void moves는_턴당_2문자다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        assertEquals(r.result().turns() * 2, r.moves().length());
    }

    @Test
    void moves는_UDLR만_담는다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        assertTrue(r.moves().matches("[UDLR]+"), "예상 못한 문자: " + r.moves());
    }

    @Test
    void 같은_경기는_같은_해시를_낸다() {
        Replay a = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        Replay b = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        assertEquals(a.hash(), b.hash());
        assertTrue(a.hash().startsWith("sha256:"));
    }

    @Test
    void 다른_시드는_다른_해시를_낸다() {
        Replay a = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        Replay b = Match.play("a", avoid(), "b", avoid(), 6, 30, 30);
        assertNotEquals(a.hash(), b.hash());
    }

    @Test
    void 리플레이는_시작_배치를_기록한다() {
        StartPositions sp = StartPositions.of(5, 30, 30);
        Replay r = Match.play("alpha", avoid(), "beta", avoid(), 5, 30, 30);

        assertEquals("alpha", r.bot0Id());
        assertEquals("beta", r.bot1Id());
        assertEquals(sp.p0(), r.start0());
        assertEquals(sp.d0(), r.dir0());
        assertEquals(sp.p1(), r.start1());
        assertEquals(sp.d1(), r.dir1());
        assertEquals(5L, r.seed());
    }

    @Test
    void 짧은_경기의_리플레이는_1KB_미만이다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        // moves가 전체 크기를 지배한다. 187턴이면 374바이트.
        assertTrue(r.moves().length() < 1800,
                "moves가 " + r.moves().length() + "자나 된다");
    }
}
