package arena.bots;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaselineBotTest {

    /** 벽이 하나도 없는 30x30 격자에서 내 머리가 (15,15), 방향은 RIGHT. */
    private GameView emptyView(Direction myDir) {
        return new GameView(30, 30, new boolean[30][30],
                new Point(15, 15), myDir,
                new Point(5, 5), Direction.LEFT, 1);
    }

    @Test
    void 직진봇은_항상_현재_방향을_유지한다() {
        StraightBot bot = new StraightBot();
        for (Direction d : Direction.values()) {
            assertEquals(d, bot.move(emptyView(d)));
        }
    }

    @Test
    void 랜덤봇은_같은_국면에_항상_같은_답을_낸다() {
        RandomBot bot = new RandomBot();
        GameView view = emptyView(Direction.RIGHT);
        Direction first = bot.move(view);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, bot.move(view), "랜덤봇이 결정론적이지 않다");
        }
    }

    @Test
    void 랜덤봇은_국면이_다르면_대체로_다른_답을_낸다() {
        RandomBot bot = new RandomBot();
        long distinct = java.util.stream.IntStream.range(0, 200)
                .mapToObj(t -> new GameView(30, 30, new boolean[30][30],
                        new Point(15, 15), Direction.RIGHT,
                        new Point(5, 5), Direction.LEFT, t))
                .map(bot::move)
                .distinct()
                .count();
        assertTrue(distinct >= 3, "랜덤봇이 사실상 한 방향만 낸다");
    }

    @Test
    void 벽회피봇은_죽지_않는_방향을_고른다() {
        boolean[][] wall = new boolean[30][30];
        // (15,15) 주변에서 UP, LEFT, RIGHT를 막는다. DOWN만 살길이다.
        wall[14][15] = true;  // UP
        wall[15][14] = true;  // LEFT
        wall[15][16] = true;  // RIGHT

        GameView view = new GameView(30, 30, wall,
                new Point(15, 15), Direction.RIGHT,
                new Point(5, 5), Direction.LEFT, 1);

        assertEquals(Direction.DOWN, new WallAvoidBot().move(view));
    }

    @Test
    void 벽회피봇은_사방이_막혀도_유효한_방향을_반환한다() {
        boolean[][] wall = new boolean[30][30];
        wall[14][15] = true;
        wall[16][15] = true;
        wall[15][14] = true;
        wall[15][16] = true;

        GameView view = new GameView(30, 30, wall,
                new Point(15, 15), Direction.RIGHT,
                new Point(5, 5), Direction.LEFT, 1);

        assertNotNull(new WallAvoidBot().move(view), "죽더라도 null을 내면 안 된다");
    }

    @Test
    void 벽회피봇은_격자_밖도_죽음으로_친다() {
        GameView view = new GameView(30, 30, new boolean[30][30],
                new Point(0, 0), Direction.LEFT,
                new Point(20, 20), Direction.LEFT, 1);

        Direction chosen = new WallAvoidBot().move(view);
        assertTrue(chosen == Direction.DOWN || chosen == Direction.RIGHT,
                "격자 밖으로 나가는 방향을 골랐다: " + chosen);
    }

    @Test
    void 모든_베이스라인_봇은_인스턴스_필드가_없다() {
        for (Class<?> c : new Class<?>[]{StraightBot.class, RandomBot.class, WallAvoidBot.class}) {
            for (var f : c.getDeclaredFields()) {
                assertTrue(java.lang.reflect.Modifier.isStatic(f.getModifiers()),
                        c.getSimpleName() + "에 인스턴스 필드가 있다: " + f.getName());
            }
        }
    }
}
