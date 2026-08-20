package arena.gate;

import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.SlowTrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TimeBudgetGateTest {

    private static List<GameView> positions;

    @BeforeAll
    static void samplePositions() {
        // G6는 시간을 재므로 국면 수를 줄여 테스트를 빠르게 유지한다.
        positions = PositionSampler.sample(500, 30, 30);
    }

    @Test
    void 아이디는_G6다() {
        assertEquals("G6", new TimeBudgetGate(positions, 5.0).id());
    }

    @Test
    void 느린_봇을_반려하고_실측값을_알려준다() {
        // SlowTrap은 한 수에 수십 ms를 쓰므로 국면 20개로도 충분히 판별된다.
        GateResult r = new TimeBudgetGate(positions.subList(0, 20), 5.0)
                .check(GateContextFixture.of(new SlowTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("p99"), r.detail());
    }

    @Test
    void 빠른_봇을_통과시킨다() {
        GateResult r = new TimeBudgetGate(positions, 5.0)
                .check(GateContextFixture.of(new CleanBot()));
        assertTrue(r.passed(), r.detail());
    }
}
