package arena.gate;

import arena.core.GameView;
import org.junit.jupiter.api.Test;

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

    @Test
    void 수집된_국면은_모두_도달_가능한_상태다() {
        for (GameView v : PositionSampler.sample(500, 30, 30)) {
            assertTrue(v.inBounds(v.myHead().x(), v.myHead().y()), "머리가 격자 밖이다");
            assertTrue(v.isWall(v.myHead().x(), v.myHead().y()), "머리 칸이 벽이 아니다");
            assertTrue(v.isWall(v.oppHead().x(), v.oppHead().y()), "상대 머리 칸이 벽이 아니다");
            assertNotEquals(v.myHead(), v.oppHead(), "두 머리가 같은 칸에 있다");
        }
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
