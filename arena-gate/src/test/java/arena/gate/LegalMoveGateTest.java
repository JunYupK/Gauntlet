package arena.gate;

import arena.bots.baseline.WallAvoidBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.CrashTrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegalMoveGateTest {

    private static List<GameView> positions;

    @BeforeAll
    static void samplePositions() {
        positions = PositionSampler.sample(2_000, 30, 30);
    }

    @Test
    void 아이디는_G4다() {
        assertEquals("G4", new LegalMoveGate(positions).id());
    }

    @Test
    void 예외를_던지는_봇을_반려하고_반례를_알려준다() {
        GateResult r = new LegalMoveGate(positions).check(GateContextFixture.of(new CrashTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("ArrayIndexOutOfBounds"), r.detail());
        assertTrue(r.detail().contains("myHead"), "반례 국면을 알려줘야 한다: " + r.detail());
    }

    @Test
    void 정상_봇을_통과시킨다() {
        assertTrue(new LegalMoveGate(positions)
                .check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(new LegalMoveGate(positions)
                .check(GateContextFixture.of(new WallAvoidBot())).passed());
    }

    @Test
    void null을_반환하는_봇을_반려한다() {
        GateResult r = new LegalMoveGate(positions).check(GateContextFixture.of(new NullBot()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("null"), r.detail());
    }

    /**
     * 컨트롤러 판단에 대한 회귀 방지: 낙서 봇이 자기가 받은 국면의 wall을
     * 직접 고쳐 써도, 표본 리스트의 나머지 국면들로 G4를 계속 판정할 수
     * 있어야 한다(다른 국면이 훼손되어 오판이 나오면 안 된다). 정상 봇을
     * 낙서 봇 "다음"에 같은 positions 리스트로 통과시켜 증명한다.
     */
    @Test
    void 낙서_봇_다음에_돌려도_다른_봇_판정은_멀쩡하다() {
        new LegalMoveGate(positions).check(GateContextFixture.of(new ScribbleTrap()));

        assertTrue(new LegalMoveGate(positions)
                .check(GateContextFixture.of(new WallAvoidBot())).passed());
    }

    static final class NullBot implements arena.bots.Bot {
        public String name() { return "NullBot"; }
        public Direction move(GameView view) { return null; }
    }

    /** 자기가 받은 국면의 wall을 직접 훼손하는 낙서 봇. */
    static final class ScribbleTrap implements arena.bots.Bot {
        public String name() { return "ScribbleTrap"; }
        public Direction move(GameView view) {
            for (boolean[] row : view.wall()) {
                java.util.Arrays.fill(row, true);
            }
            return view.myDir();
        }
    }
}
