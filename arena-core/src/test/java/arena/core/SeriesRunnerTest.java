package arena.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;

class SeriesRunnerTest {

    /**
     * 빈 시드로 시리즈를 돌리면 경기가 0판이고, 그 0판에서 평균을 내는
     * 소비자는 0/0 = NaN을 얻는다 — 실제로 BundleBuilder.buildStats가
     * 그 NaN을 generations.json에 조용히 써 넣고 있었다. 규칙의 정의는
     * SeedList 하나뿐이고(SeedListTest가 규칙 자체를 못박는다), 이
     * 테스트는 이 호출 지점이 실제로 그 규칙을 부르는지를 고정한다.
     */
    @org.junit.jupiter.api.Test
    void 빈_시드_목록을_거부한다() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SeriesRunner.run("a", v -> Direction.UP, "b", v -> Direction.UP,
                        java.util.List.of(), 30, 30, false));
    }

    /** 중복 시드도 같은 규칙으로 막힌다 — 같은 경기가 두 번 계산된다. */
    @org.junit.jupiter.api.Test
    void 중복된_시드_목록을_거부한다() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SeriesRunner.run("a", v -> Direction.UP, "b", v -> Direction.UP,
                        java.util.List.of(3L, 3L), 30, 30, false));
    }

    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    private static BotFunction hugLeft() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    private static final List<Long> SEEDS = LongStream.rangeClosed(1, 50).boxed().toList();

    @Test
    void 시드마다_2경기가_생긴다() {
        assertEquals(100, SeriesRunner.run("a", avoid(), "b", hugLeft(),
                SEEDS, 30, 30, false).size());
    }

    @Test
    void 절반은_좌석_교대_경기다() {
        long swapped = SeriesRunner.run("a", avoid(), "b", hugLeft(), SEEDS, 30, 30, false)
                .stream().filter(Replay::swapped).count();
        assertEquals(50, swapped);
    }

    @Test
    void 교대_경기는_같은_시작_위치에_봇만_바꿔_앉힌다() {
        List<Replay> replays = SeriesRunner.run(
                "a", avoid(), "b", hugLeft(), List.of(7L), 30, 30, false);

        Replay normal = replays.stream().filter(r -> !r.swapped()).findFirst().orElseThrow();
        Replay swapped = replays.stream().filter(Replay::swapped).findFirst().orElseThrow();

        assertEquals(normal.start0(), swapped.start0(),
                "시작 위치가 바뀌었다 — 미러링이 아니라 좌석 교대여야 한다");
        assertEquals(normal.start1(), swapped.start1());
        assertEquals(normal.dir0(), swapped.dir0(),
                "시작 방향이 바뀌었다 — 미러링이 아니라 좌석 교대여야 한다");
        assertEquals(normal.dir1(), swapped.dir1());
        assertEquals("a", normal.bot0Id());
        assertEquals("b", swapped.bot0Id(), "교대 경기에서는 b가 0번 좌석에 앉아야 한다");
    }

    @Test
    void 병렬_실행이_순차_실행과_같은_결과를_낸다() {
        List<Replay> sequential = SeriesRunner.run(
                "a", avoid(), "b", hugLeft(), SEEDS, 30, 30, false);
        List<Replay> parallel = SeriesRunner.run(
                "a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);

        // Replay는 record다 — 리스트 전체를 한 번에 비교하면 hash뿐
        // 아니라 swapped·matchId·start0/1·dir0/1을 포함한 모든 필드가
        // 순서까지 정확히 같은지 검증된다. hash만 비교하면 그 필드들은
        // 두 경로 사이에 아무것도 검증하지 않는 셈이 된다.
        assertEquals(sequential, parallel,
                "병렬 실행과 순차 실행의 리플레이 목록이 달라졌다");
    }

    @Test
    void 반복_실행이_항상_같은_결과를_낸다() {
        List<Replay> first = SeriesRunner.run("a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);
        List<Replay> second = SeriesRunner.run("a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).hash(), second.get(i).hash());
        }
    }
}
