package arena.diagnostics;

import arena.core.Grid;
import arena.core.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloodFillTest {

    @Test
    void 빈_5x5에서_머리_한_칸을_뺀_24칸에_닿는다() {
        Grid g = new Grid(5, 5);
        Point head = new Point(2, 2);
        g.claim(head, 0);

        assertEquals(24, FloodFill.reach(g, head));
    }

    @Test
    void 사방이_막히면_0칸이다() {
        Grid g = new Grid(5, 5);
        Point head = new Point(2, 2);
        g.claim(head, 0);
        g.claim(new Point(2, 1), 1);
        g.claim(new Point(2, 3), 1);
        g.claim(new Point(1, 2), 1);
        g.claim(new Point(3, 2), 1);

        assertEquals(0, FloodFill.reach(g, head));
    }

    @Test
    void 벽으로_갈린_반대편은_세지_않는다() {
        Grid g = new Grid(5, 5);
        // x=2 열을 위아래로 완전히 막는다.
        for (int y = 0; y < 5; y++) {
            g.claim(new Point(2, y), 1);
        }
        Point head = new Point(0, 0);
        g.claim(head, 0);

        // 왼쪽 영역은 x=0,1 두 열 = 10칸, 머리 1칸 제외하면 9칸.
        assertEquals(9, FloodFill.reach(g, head));
    }

    @Test
    void 모서리에_갇히면_그_방만_센다() {
        Grid g = new Grid(5, 5);
        g.claim(new Point(0, 1), 1);
        g.claim(new Point(1, 1), 1);
        g.claim(new Point(1, 0), 1);

        Point head = new Point(0, 0);
        g.claim(head, 0);

        assertEquals(0, FloodFill.reach(g, head));
    }
}
