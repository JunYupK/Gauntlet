package arena.core;

/** y축은 아래로 증가한다 (화면 좌표계). */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() { return dx; }
    public int dy() { return dy; }

    /** 리플레이 moves 문자열의 한 글자. 네 방향의 첫 글자가 서로 다르다. */
    public char code() { return name().charAt(0); }

    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    public static Direction fromCode(char c) {
        return switch (c) {
            case 'U' -> UP;
            case 'D' -> DOWN;
            case 'L' -> LEFT;
            case 'R' -> RIGHT;
            default -> throw new IllegalArgumentException("알 수 없는 방향 문자: " + c);
        };
    }
}
