package arena.core;

import java.util.Random;

/**
 * 시드로부터 두 봇의 시작 위치와 초기 방향을 만든다.
 *
 * java.util.Random은 알고리즘이 명세로 고정되어 있어 JVM이 바뀌어도
 * 같은 시드가 같은 수열을 낸다. R1의 전제다.
 */
public record StartPositions(Point p0, Direction d0, Point p1, Direction d1) {

    /** 가장자리 여백. 어느 방향으로 출발해도 최소 3턴은 살아남는다. */
    private static final int MARGIN = 3;

    /** 초반 즉시 접촉을 막는 최소 거리. */
    private static final int MIN_DISTANCE = 10;

    private static final int MAX_ATTEMPTS = 1000;

    public static StartPositions of(long seed, int width, int height) {
        Random rng = new Random(seed);

        int spanX = width - 2 * MARGIN;
        int spanY = height - 2 * MARGIN;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Point a = new Point(MARGIN + rng.nextInt(spanX), MARGIN + rng.nextInt(spanY));
            Point b = new Point(MARGIN + rng.nextInt(spanX), MARGIN + rng.nextInt(spanY));

            if (a.manhattan(b) >= MIN_DISTANCE) {
                Direction da = Direction.values()[rng.nextInt(4)];
                Direction db = Direction.values()[rng.nextInt(4)];
                return new StartPositions(a, da, b, db);
            }
        }

        throw new IllegalStateException(
                "시드 " + seed + ": " + MAX_ATTEMPTS + "회 시도에도 배치를 못 만들었다. "
                        + "격자가 너무 좁거나 MIN_DISTANCE가 너무 크다.");
    }
}
