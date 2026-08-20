package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;
import arena.gate.traps.CleanBot;
import arena.gate.traps.NondeterministicTrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeterminismGateTest {

    private static List<GameView> positions;

    @BeforeAll
    static void samplePositions() {
        positions = PositionSampler.sample(2_000, 30, 30);
    }

    @Test
    void 아이디는_G5다() {
        assertEquals("G5", new DeterminismGate(positions).id());
    }

    @Test
    void 같은_국면에_다른_답을_내는_봇을_반려한다() {
        GateResult r = new DeterminismGate(positions)
                .check(GateContextFixture.of(new NondeterministicTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("국면"), r.detail());
    }

    @Test
    void 결정론적_봇을_통과시킨다() {
        assertTrue(new DeterminismGate(positions)
                .check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(new DeterminismGate(positions)
                .check(GateContextFixture.of(new RandomBot())).passed());
    }

    /**
     * 국면 내용(wall)에 따라 답이 달라지면서, 동시에 자기가 받은 wall을
     * 스스로 훼손하는 봇. G5가 반복 호출마다 원본에서 새로 복사해 먹이지
     * 않으면, 첫 호출의 낙서가 두 번째 호출에서 "다른 답"으로 관측되어
     * 결정론적인 봇이 비결정론으로 오판된다 — 이 테스트는 그 오판이
     * 일어나지 않는지를 직접 확인한다. 공유 static {@code positions}가
     * 아니라 이 테스트 전용의 손으로 짠 국면 하나만 쓴다: 실제 표본은
     * (0,0)의 wall 값을 보장하지 않으므로, 진단 정확도가 표본 내용에
     * 우연히 좌우되면 안 된다.
     */
    @Test
    void 자기_국면을_훼손해도_반복_호출마다_원본_기준으로_판정한다() {
        GameView handcrafted = new GameView(30, 30, new boolean[30][30],
                new Point(15, 15), Direction.UP, new Point(5, 5), Direction.DOWN, 1);
        List<GameView> local = List.of(handcrafted);

        GateResult r = new DeterminismGate(local)
                .check(GateContextFixture.of(new WallReadingScribbleTrap()));

        assertTrue(r.passed(), r.detail());
    }

    /** wall(0,0)을 읽어 답을 정한 뒤, 자기가 받은 wall 전체를 true로 덮어쓴다. */
    static final class WallReadingScribbleTrap implements arena.bots.Bot {
        public String name() { return "WallReadingScribbleTrap"; }
        public Direction move(GameView view) {
            Direction answer = view.wall()[0][0] ? Direction.UP : Direction.DOWN;
            for (boolean[] row : view.wall()) {
                java.util.Arrays.fill(row, true);
            }
            return answer;
        }
    }
}
