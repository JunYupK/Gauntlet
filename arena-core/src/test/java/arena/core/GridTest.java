package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GridTest {

    @Test
    void 새_격자는_전부_빈칸이다() {
        Grid g = new Grid(30, 30);
        assertFalse(g.isWall(new Point(0, 0)));
        assertFalse(g.isWall(new Point(29, 29)));
        assertEquals(Grid.EMPTY, g.ownerAt(new Point(15, 15)));
    }

    @Test
    void 점유한_칸은_벽이_되고_소유자가_기록된다() {
        Grid g = new Grid(30, 30);
        g.claim(new Point(4, 7), 1);
        assertTrue(g.isWall(new Point(4, 7)));
        assertEquals(1, g.ownerAt(new Point(4, 7)));
    }

    @Test
    void 격자_밖을_판별한다() {
        Grid g = new Grid(30, 30);
        assertTrue(g.inBounds(new Point(0, 0)));
        assertTrue(g.inBounds(new Point(29, 29)));
        assertFalse(g.inBounds(new Point(-1, 0)));
        assertFalse(g.inBounds(new Point(30, 0)));
        assertFalse(g.inBounds(new Point(0, 30)));
    }

    @Test
    void 스냅샷은_방어적_복사라서_봇이_원본을_훼손할_수_없다() {
        Grid g = new Grid(5, 5);
        g.claim(new Point(1, 1), 0);

        boolean[][] snapshot = g.wallSnapshot();
        snapshot[1][1] = false;
        snapshot[4][4] = true;

        assertTrue(g.isWall(new Point(1, 1)), "원본이 훼손되었다");
        assertFalse(g.isWall(new Point(4, 4)), "원본이 훼손되었다");
    }

    @Test
    void 스냅샷은_y_x_순서로_인덱싱된다() {
        Grid g = new Grid(10, 20);
        g.claim(new Point(3, 7), 0);

        boolean[][] wall = g.wallSnapshot();
        assertEquals(20, wall.length, "바깥 배열은 height");
        assertEquals(10, wall[0].length, "안쪽 배열은 width");
        assertTrue(wall[7][3], "wall[y][x] 순서여야 한다");
    }
}
