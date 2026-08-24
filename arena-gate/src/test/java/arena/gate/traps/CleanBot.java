package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;

/**
 * 대조군. 모든 관문을 통과해야 한다.
 *
 * 관문이 통과시켜야 할 것까지 반려하면 루프가 영원히 막히므로,
 * 함정 봇만큼이나 이 봇이 중요하다.
 */
public final class CleanBot implements Bot {

    private static final Direction[] PRIORITY = {
            Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT
    };

    @Override
    public String name() { return "CleanBot"; }

    @Override
    public Direction move(GameView view) {
        Direction best = view.myDir();
        int bestSpace = -1;

        for (Direction d : PRIORITY) {
            if (view.isDeadly(d)) continue;
            int space = openNeighbours(view, d);
            if (space > bestSpace) {
                bestSpace = space;
                best = d;
            }
        }
        return best;
    }

    /** 그 칸에 갔을 때 인접한 빈 칸 수. 아주 얕은 공간 감각. */
    private int openNeighbours(GameView view, Direction d) {
        Point p = view.myHead().move(d);
        int open = 0;
        for (Direction n : Direction.values()) {
            Point q = p.move(n);
            if (view.inBounds(q.x(), q.y()) && !view.isWall(q.x(), q.y())) open++;
        }
        return open;
    }
}
