package arena.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StandingTest {

    private Replay replay(String id0, String id1, boolean swapped, int winner) {
        MatchResult result = new MatchResult(winner, 10,
                winner < 0 ? DeathReason.HEAD_ON_COLLISION : DeathReason.P0_HIT_OWN_WALL);
        return new Replay(Replay.SCHEMA, "m", 30, 30, 1L, swapped,
                id0, new Point(1, 1), Direction.UP,
                id1, new Point(2, 2), Direction.DOWN,
                "UU", result, "sha256:x");
    }

    @Test
    void 좌석_교대_경기의_승자를_올바르게_귀속한다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),   // hero가 0번 좌석에서 승
                replay("rival", "hero", true, 1)     // hero가 1번 좌석에서 승
        );

        Standing s = Standing.of(replays, "hero");
        assertEquals(2, s.wins());
        assertEquals(0, s.losses());
    }

    @Test
    void 무승부는_0점5를_준다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),    // 승
                replay("hero", "rival", false, -1),   // 무
                replay("hero", "rival", false, 1)     // 패
        );

        Standing s = Standing.of(replays, "hero");
        assertEquals(1, s.wins());
        assertEquals(1, s.draws());
        assertEquals(1, s.losses());
        assertEquals(0.5, s.scoreRate(), 1e-9);
    }

    @Test
    void 승점_승률은_승과_무의_절반을_합해_나눈_값이다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, -1)
        );
        // (3 + 0.5) / 4 = 0.875
        assertEquals(0.875, Standing.of(replays, "hero").scoreRate(), 1e-9);
    }
}
