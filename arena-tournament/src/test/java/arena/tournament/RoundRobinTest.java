package arena.tournament;

import arena.bots.Bot;
import arena.bots.baseline.RandomBot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;
import arena.core.Direction;
import arena.core.GameView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;

class RoundRobinTest {

    private static final List<Long> SEEDS = LongStream.rangeClosed(1, 10).boxed().toList();
    private static final List<Bot> BOTS =
            List.of(new Gen00Bot(), new RandomBot(), new WallAvoidBot());

    @Test
    void 정사각_행렬을_낸다() {
        double[][] m = RoundRobin.run(BOTS, SEEDS, 30, 30);

        assertEquals(3, m.length);
        for (double[] row : m) {
            assertEquals(3, row.length);
        }
    }

    @Test
    void 대각선은_비운다() {
        double[][] m = RoundRobin.run(BOTS, SEEDS, 30, 30);

        for (int i = 0; i < 3; i++) {
            assertTrue(Double.isNaN(m[i][i]), "자기 자신과의 대전이 채워졌다");
        }
    }

    @Test
    void 마주보는_칸의_승률은_합이_1이다() {
        double[][] m = RoundRobin.run(BOTS, SEEDS, 30, 30);

        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                assertEquals(1.0, m[i][j] + m[j][i], 1e-9,
                        "(" + i + "," + j + ")의 승률 합이 1이 아니다");
            }
        }
    }

    @Test
    void 벽회피봇이_직진봇을_압도한다() {
        double[][] m = RoundRobin.run(BOTS, SEEDS, 30, 30);

        assertTrue(m[2][0] > 0.8, "벽회피봇의 대 직진봇 승률이 " + m[2][0] + "밖에 안 된다");
    }

    /**
     * 이름은 봇 작성자가 통제하는 문자열이고, RoundRobin이 짝짓는 서로 다른
     * 두 세대 사이에 이름이 겹치지 않는다는 보장이 없다. {@code bot.name()}을
     * 좌석 id로 그대로 SeriesRunner·Standing에 넘기는 회귀가 있다면,
     * {@link arena.core.Standing#seatOf}가 bot0Id를 먼저 검사하는 탓에 이름이
     * 같은 두 봇의 전적이 조용히 좌석 0으로만 쏠린다 — 강한 봇이 이겨도
     * 약한 봇이 이겨도 같은 셀이 찍히는 조용한 증거 반전이다. 그래서
     * assertDoesNotThrow만으로는 부족하고, 실제로 강한 쪽이 이긴 걸로
     * 정확히 기록되는지까지 확인한다.
     */
    @Test
    void 이름이_같은_두_봇이_섞여도_행렬이_왜곡되지_않는다() {
        Bot strong = new NamedBot("Dup", new WallAvoidBot());
        Bot weak = new NamedBot("Dup", new Gen00Bot());
        List<Bot> bots = List.of(strong, weak);

        double[][] m = assertDoesNotThrow(() -> RoundRobin.run(bots, SEEDS, 30, 30));

        assertTrue(Double.isNaN(m[0][0]));
        assertTrue(Double.isNaN(m[1][1]));
        assertEquals(1.0, m[0][1] + m[1][0], 1e-9);
        assertTrue(m[0][1] > 0.8,
                "이름이 같다는 이유로 벽회피봇의 승률이 뭉개졌다: " + m[0][1]);
    }

    /** 이름을 생성자로 지정할 수 있는 래퍼. 이름 충돌 시나리오 전용. */
    static final class NamedBot implements Bot {
        private final String name;
        private final Bot delegate;
        NamedBot(String name, Bot delegate) { this.name = name; this.delegate = delegate; }
        public String name() { return name; }
        public Direction move(GameView view) { return delegate.move(view); }
    }
}
