package arena.diagnostics;

import arena.core.Direction;
import arena.core.Grid;
import arena.core.Point;

import java.util.ArrayDeque;
import java.util.Deque;

/** 머리에서 닿을 수 있는 빈 칸의 수. 머리 자신은 세지 않는다. */
public final class FloodFill {

    private FloodFill() {}

    /**
     * BFS. 재귀가 아니라 명시적 큐를 쓴다 — 격자는 최대 30×30(=900칸)
     * 열려 있을 수 있고, 재귀로 짜면 그 깊이만큼 스택을 먹는다.
     */
    public static int reach(Grid grid, Point head) {
        int width = grid.width();
        int height = grid.height();
        boolean[][] seen = new boolean[height][width];

        Deque<Point> queue = new ArrayDeque<>();
        seen[head.y()][head.x()] = true;
        queue.add(head);

        int count = 0;
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            for (Direction d : Direction.values()) {
                Point n = p.move(d);
                if (!grid.inBounds(n)) continue;
                if (seen[n.y()][n.x()]) continue;
                if (grid.isWall(n)) continue;

                seen[n.y()][n.x()] = true;
                count++;
                queue.add(n);
            }
        }
        return count;
    }
}
