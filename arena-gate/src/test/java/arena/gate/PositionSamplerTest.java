package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.BotFunction;
import arena.core.GameView;
import arena.core.Match;
import arena.core.Point;
import arena.core.Replay;
import arena.core.StartPositions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PositionSamplerTest {

    @Test
    void 요청한_개수만큼_수집한다() {
        assertEquals(1_000, PositionSampler.sample(1_000, 30, 30).size());
    }

    @Test
    void 같은_인자로_부르면_같은_국면이_나온다() {
        List<GameView> a = PositionSampler.sample(200, 30, 30);
        List<GameView> b = PositionSampler.sample(200, 30, 30);

        for (int i = 0; i < 200; i++) {
            assertEquals(a.get(i).myHead(), b.get(i).myHead(), "국면 " + i + "이 재현되지 않았다");
            assertEquals(a.get(i).turn(), b.get(i).turn());
        }
    }

    /**
     * "도달 가능하다"는 곧 "엔진이 실제로 만드는 보드와 같다"는 뜻이다.
     *
     * 이 테스트는 원래 머리 두 칸이 벽인지만 봤다 — 그래서 표본이
     * 시작 칸 2개만 벽으로 잡고 엔진이 잡는 뒤쪽 2칸을 빠뜨린 결함(F1)
     * 앞에서 그대로 GREEN이었다. 2칸짜리 보드에서도 머리 두 칸은
     * 여전히 벽이기 때문이다. 이제 표본 보드를 엔진이 같은 턴에 실제로
     * 갖고 있던 보드와 통째로 대조한다 — 엔진의 보드는 {@link
     * Match#playResult}에 관찰자를 달아 직접 받아오고, 이 테스트 안에서
     * 벽 규칙을 다시 베끼지 않는다(그러면 검사와 대상이 같은 실수를
     * 공유한다).
     *
     * {@code sample()}이 돌려주는 리스트는 {@link PositionSampler#viewsOf}
     * 결과를 리플레이 순서대로 이어붙인 것이므로, 리플레이 하나에 대한
     * {@code viewsOf}를 대조하면 그 리스트가 나눠주는 보드를 대조한
     * 것과 같다. 평평한 리스트만으로는 어느 국면이 몇 번째 턴인지
     * 되짚을 수 없어 이렇게 판다.
     */
    @Test
    void 수집된_국면은_모두_도달_가능한_상태다() {
        for (long seed : new long[]{ 1L, 7L, 42L }) {
            BotFunction a = v -> new WallAvoidBot().move(v);
            BotFunction b = v -> new RandomBot().move(v);

            Replay replay = Match.play("a", a, "b", b, seed, 30, 30);
            List<GameView> views = PositionSampler.viewsOf(replay);

            // 엔진이 매 턴 끝에 갖고 있던 보드와 머리 좌표. W(t)는 t=1이면
            // 시작 격자, t>1이면 직전 턴이 끝난 시점의 보드다.
            List<boolean[][]> engineWallAfter = new ArrayList<>();
            List<Point[]> engineHeadsAfter = new ArrayList<>();
            Match.playResult("a", a, "b", b, seed, 30, 30,
                    (turn, gridAfter, heads) -> {
                        engineWallAfter.add(gridAfter.wallSnapshot());
                        engineHeadsAfter.add(heads);
                    });

            boolean[][] initialWall = Match.initialGrid(
                    new StartPositions(replay.start0(), replay.dir0(),
                            replay.start1(), replay.dir1()), 30, 30).wallSnapshot();

            assertEquals(2 * replay.result().turns(), views.size(),
                    "시드 " + seed + ": 턴마다 시야 두 개가 나와야 한다");

            for (int turn = 1; turn <= replay.result().turns(); turn++) {
                boolean[][] expectedWall = (turn == 1)
                        ? initialWall
                        : engineWallAfter.get(turn - 2);

                for (int me = 0; me < 2; me++) {
                    GameView v = views.get((turn - 1) * 2 + me);

                    assertTrue(Arrays.deepEquals(expectedWall, v.wall()),
                            "시드 " + seed + " 턴 " + turn + ": 표본 보드가 엔진 보드와 다르다 ("
                                    + walls(expectedWall) + "칸 vs " + walls(v.wall()) + "칸)");

                    if (turn > 1) {
                        Point[] heads = engineHeadsAfter.get(turn - 2);
                        assertEquals(heads[me], v.myHead(),
                                "시드 " + seed + " 턴 " + turn + ": 내 머리가 엔진과 다르다");
                        assertEquals(heads[1 - me], v.oppHead(),
                                "시드 " + seed + " 턴 " + turn + ": 상대 머리가 엔진과 다르다");
                    }

                    assertTrue(v.inBounds(v.myHead().x(), v.myHead().y()), "머리가 격자 밖이다");
                    assertTrue(v.isWall(v.myHead().x(), v.myHead().y()), "머리 칸이 벽이 아니다");
                    assertTrue(v.isWall(v.oppHead().x(), v.oppHead().y()), "상대 머리 칸이 벽이 아니다");
                    assertNotEquals(v.myHead(), v.oppHead(), "두 머리가 같은 칸에 있다");
                }
            }
        }
    }

    /**
     * 엔진은 첫 턴에 이미 4칸(시작 칸 2개 + 그 뒤 2개)을 벽으로 갖는다
     * ({@code MatchTest.initialGrid는_정확히_4칸의_벽을_갖는다}가 엔진
     * 쪽을 못박는다). 표본도 같아야 한다 — 이게 어긋나면 "뒤로 가면
     * 죽는다"는 계약이 표본 안에서만 거짓이 된다.
     */
    @Test
    void 첫_턴_표본에는_시작_칸_뒤_칸까지_벽으로_들어있다() {
        Replay replay = Match.play("a", v -> new WallAvoidBot().move(v),
                "b", v -> new RandomBot().move(v), 1L, 30, 30);

        GameView first = PositionSampler.viewsOf(replay).get(0);

        assertEquals(4, walls(first.wall()), "첫 턴 벽이 4칸이 아니다");
        assertTrue(first.isDeadly(first.myDir().opposite()),
                "첫 턴에 뒤로 가는 수가 죽음으로 판정되지 않는다");
    }

    private static int walls(boolean[][] wall) {
        int n = 0;
        for (boolean[] row : wall) {
            for (boolean w : row) if (w) n++;
        }
        return n;
    }

    @Test
    void 국면은_경기_초반에만_몰려있지_않다() {
        long lateGame = PositionSampler.sample(1_000, 30, 30).stream()
                .filter(v -> v.turn() > 20)
                .count();
        assertTrue(lateGame > 100, "중후반 국면이 " + lateGame + "개뿐이다");
    }

    /**
     * 컨트롤러 판단: 표본은 G4·G5·G6가 공유해서 재사용한다 — 특히 G5는
     * 같은 국면을 같은 봇에게 두 번 먹여 비결정성을 잡는다. GameView는
     * wall 배열을 방어적으로 복사하지 않고 내부 참조를 그대로 내준다
     * (엔진 안에서는 Match가 매번 새 스냅샷을 만들어 주므로 안전했지만,
     * 여기서는 봇이 그 참조를 훼손할 수 있다). 봇이 자기가 받은 국면의
     * wall을 고쳐 써도 같은 표본 리스트 안의 "다른" 국면은 전혀 영향을
     * 받지 않아야 한다 — 그렇지 않으면 한 봇의 낙서가 이후 다른 봇을
     * 심사할 때 쓰이는 국면을 몰래 바꿔버린다.
     */
    @Test
    void 한_국면의_wall을_훼손해도_다른_국면은_영향받지_않는다() {
        List<GameView> views = PositionSampler.sample(50, 30, 30);

        boolean[][][] before = new boolean[views.size()][][];
        for (int i = 0; i < views.size(); i++) {
            before[i] = deepCopy(views.get(i).wall());
        }

        // 악의적이거나 부주의한 봇이 자기가 받은 국면의 wall을 직접 고쳐 쓴다.
        boolean[][] tampered = views.get(0).wall();
        for (boolean[] row : tampered) {
            Arrays.fill(row, true);
        }

        for (int i = 1; i < views.size(); i++) {
            assertTrue(Arrays.deepEquals(before[i], views.get(i).wall()),
                    "국면 " + i + "이 다른 국면의 훼손에 영향을 받았다");
        }
    }

    private static boolean[][] deepCopy(boolean[][] src) {
        boolean[][] copy = new boolean[src.length][];
        for (int y = 0; y < src.length; y++) {
            copy[y] = src[y].clone();
        }
        return copy;
    }
}
