package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectionTest {

    @Test
    void 각_방향은_고유한_인코딩_문자를_갖는다() {
        assertEquals('U', Direction.UP.code());
        assertEquals('D', Direction.DOWN.code());
        assertEquals('L', Direction.LEFT.code());
        assertEquals('R', Direction.RIGHT.code());
    }

    @Test
    void 인코딩_문자로부터_방향을_복원한다() {
        for (Direction d : Direction.values()) {
            assertEquals(d, Direction.fromCode(d.code()));
        }
    }

    @Test
    void 반대_방향은_델타의_부호가_뒤집힌다() {
        for (Direction d : Direction.values()) {
            Direction o = d.opposite();
            assertEquals(-d.dx(), o.dx());
            assertEquals(-d.dy(), o.dy());
        }
    }

    @Test
    void 점은_방향으로_한_칸_이동한다() {
        Point p = new Point(5, 5);
        assertEquals(new Point(5, 4), p.move(Direction.UP));
        assertEquals(new Point(5, 6), p.move(Direction.DOWN));
        assertEquals(new Point(4, 5), p.move(Direction.LEFT));
        assertEquals(new Point(6, 5), p.move(Direction.RIGHT));
    }

    @Test
    void 맨해튼_거리를_잰다() {
        assertEquals(7, new Point(1, 2).manhattan(new Point(5, 5)));
        assertEquals(0, new Point(3, 3).manhattan(new Point(3, 3)));
    }
}
