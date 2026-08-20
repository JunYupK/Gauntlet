package arena.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;

class SeriesRunnerTest {

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
        assertEquals("a", normal.bot0Id());
        assertEquals("b", swapped.bot0Id(), "교대 경기에서는 b가 0번 좌석에 앉아야 한다");
    }

    @Test
    void 병렬_실행이_순차_실행과_같은_결과를_낸다() {
        List<Replay> sequential = SeriesRunner.run(
                "a", avoid(), "b", hugLeft(), SEEDS, 30, 30, false);
        List<Replay> parallel = SeriesRunner.run(
                "a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);

        assertEquals(sequential.size(), parallel.size());
        for (int i = 0; i < sequential.size(); i++) {
            assertEquals(sequential.get(i).hash(), parallel.get(i).hash(),
                    "경기 " + i + "의 결과가 병렬 실행에서 달라졌다");
        }
    }

    @Test
    void 반복_실행이_항상_같은_결과를_낸다() {
        List<Replay> first = SeriesRunner.run("a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);
        List<Replay> second = SeriesRunner.run("a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).hash(), second.get(i).hash());
        }
    }
}
