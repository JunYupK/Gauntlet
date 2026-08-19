package arena.core;

/**
 * 봇이 볼 수 있는 전부. 봇은 이것 말고는 세상에 접근할 수단이 없다.
 *
 * wall은 이동 전 벽 집합 W(t)이며 방어적 복사본이다.
 * 히스토리는 제공하지 않는다 — 봇은 무상태 순수 함수다.
 */
public record GameView(
        int width,
        int height,
        boolean[][] wall,
        Point myHead,
        Direction myDir,
        Point oppHead,
        Direction oppDir,
        int turn
) {
    public boolean isWall(int x, int y) {
        return wall[y][x];
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /** 그 방향으로 한 칸 가면 즉시 죽는가. 봇이 가장 자주 묻는 질문이다. */
    public boolean isDeadly(Direction d) {
        Point p = myHead.move(d);
        return !inBounds(p.x(), p.y()) || wall[p.y()][p.x()];
    }
}
