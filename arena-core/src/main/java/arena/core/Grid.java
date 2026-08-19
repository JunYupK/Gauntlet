package arena.core;

/**
 * 격자의 점유 상태. 벽 여부와 소유자를 함께 관리한다.
 *
 * 소유자를 기록하는 이유가 둘 있다. 시각화에서 두 봇의 궤적을 다른 색으로
 * 그려야 하고, 사망 원인이 자기 벽인지 상대 벽인지 구분해야 한다.
 */
public final class Grid {

    public static final int EMPTY = -1;

    private final int width;
    private final int height;
    private final int[][] owner;   // [y][x]

    public Grid(int width, int height) {
        this.width = width;
        this.height = height;
        this.owner = new int[height][width];
        for (int[] row : owner) {
            java.util.Arrays.fill(row, EMPTY);
        }
    }

    public int width() { return width; }
    public int height() { return height; }

    public boolean inBounds(Point p) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height;
    }

    public boolean isWall(Point p) {
        return owner[p.y()][p.x()] != EMPTY;
    }

    public int ownerAt(Point p) {
        return owner[p.y()][p.x()];
    }

    public void claim(Point p, int botIndex) {
        owner[p.y()][p.x()] = botIndex;
    }

    /** 봇에게 넘길 방어적 복사본. 봇이 훼손해도 엔진 상태는 안전하다. */
    public boolean[][] wallSnapshot() {
        boolean[][] wall = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wall[y][x] = owner[y][x] != EMPTY;
            }
        }
        return wall;
    }

    public int[][] ownerSnapshot() {
        int[][] copy = new int[height][width];
        for (int y = 0; y < height; y++) {
            copy[y] = owner[y].clone();
        }
        return copy;
    }

    public Grid copy() {
        Grid g = new Grid(width, height);
        for (int y = 0; y < height; y++) {
            g.owner[y] = owner[y].clone();
        }
        return g;
    }
}
