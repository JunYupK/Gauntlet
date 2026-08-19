package arena.core;

public record Point(int x, int y) {

    public Point move(Direction d) {
        return new Point(x + d.dx(), y + d.dy());
    }

    public int manhattan(Point other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }
}
