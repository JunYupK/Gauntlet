package arena.gate;

import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.gate.traps.CleanBot;
import arena.gate.traps.StatefulTrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatelessGateTest {

    private final Gate gate = new StatelessGate();

    @Test
    void 아이디는_G2다() {
        assertEquals("G2", gate.id());
    }

    @Test
    void 인스턴스_필드를_가진_봇을_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new StatefulTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("callCount"),
                "위반 필드 이름을 알려줘야 한다: " + r.detail());
    }

    @Test
    void 무상태_봇을_통과시킨다() {
        assertTrue(gate.check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(gate.check(GateContextFixture.of(new StraightBot())).passed());
    }

    @Test
    void static_final_상수는_허용한다() {
        // WallAvoidBot은 private static final Direction[] PRIORITY를 갖는다.
        assertTrue(gate.check(GateContextFixture.of(new WallAvoidBot())).passed(),
                "static final 상수까지 반려하면 정상적인 봇을 못 만든다");
    }
}
