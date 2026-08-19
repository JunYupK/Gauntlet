package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StartPositionsTest {

    @Test
    void 같은_시드는_항상_같은_배치를_낸다() {
        for (long seed = 1; seed <= 50; seed++) {
            StartPositions a = StartPositions.of(seed, 30, 30);
            StartPositions b = StartPositions.of(seed, 30, 30);
            assertEquals(a, b, "시드 " + seed + "이 재현되지 않았다");
        }
    }

    @Test
    void 다른_시드는_대체로_다른_배치를_낸다() {
        long distinct = java.util.stream.LongStream.rangeClosed(1, 50)
                .mapToObj(s -> StartPositions.of(s, 30, 30))
                .distinct()
                .count();
        assertTrue(distinct >= 45, "시드 50개 중 서로 다른 배치가 " + distinct + "개뿐이다");
    }

    @Test
    void 시작_위치는_가장자리에서_최소_3칸_안쪽이다() {
        for (long seed = 1; seed <= 50; seed++) {
            StartPositions sp = StartPositions.of(seed, 30, 30);
            for (Point p : new Point[]{sp.p0(), sp.p1()}) {
                assertTrue(p.x() >= 3 && p.x() <= 26, "x가 여백을 벗어남: " + p);
                assertTrue(p.y() >= 3 && p.y() <= 26, "y가 여백을 벗어남: " + p);
            }
        }
    }

    @Test
    void 두_시작_위치의_맨해튼_거리는_10_이상이다() {
        for (long seed = 1; seed <= 50; seed++) {
            StartPositions sp = StartPositions.of(seed, 30, 30);
            assertTrue(sp.p0().manhattan(sp.p1()) >= 10,
                    "시드 " + seed + "의 두 봇이 너무 가깝다");
        }
    }

    @Test
    void 심사_시드와_홀드아웃_시드는_겹치지_않는다() {
        var judging = java.util.stream.LongStream.rangeClosed(1, 50)
                .mapToObj(s -> StartPositions.of(s, 30, 30))
                .collect(java.util.stream.Collectors.toSet());
        var holdout = java.util.stream.LongStream.rangeClosed(1001, 1050)
                .mapToObj(s -> StartPositions.of(s, 30, 30))
                .collect(java.util.stream.Collectors.toSet());

        judging.retainAll(holdout);
        assertTrue(judging.size() <= 2,
                "두 시드 집합의 배치가 " + judging.size() + "개나 겹친다");
    }
}
