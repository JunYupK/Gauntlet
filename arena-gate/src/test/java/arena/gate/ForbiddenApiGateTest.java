package arena.gate;

import arena.bots.Bot;
import arena.bots.baseline.RandomBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.ClockTrap;
import arena.gate.traps.UnseededRandomTrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForbiddenApiGateTest {

    private final Gate gate = new ForbiddenApiGate();

    @Test
    void 아이디는_G3다() {
        assertEquals("G3", gate.id());
    }

    @Test
    void 시계를_읽는_봇을_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new ClockTrap()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("nanoTime"), r.detail());
    }

    @Test
    void 시드_없는_난수를_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new UnseededRandomTrap()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("Random"), r.detail());
    }

    @Test
    void 시드_있는_난수는_허용한다() {
        assertTrue(gate.check(GateContextFixture.of(new SeededRandomBot())).passed(),
                "시드 있는 Random까지 막으면 안 된다");
    }

    @Test
    void 파일_접근을_반려한다() {
        assertFalse(gate.check(GateContextFixture.of(new FileReadingBot())).passed());
    }

    @Test
    void 가변_static_필드를_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new MutableStaticBot()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("counter"), r.detail());
    }

    @Test
    void 깨끗한_봇과_베이스라인을_통과시킨다() {
        assertTrue(gate.check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(gate.check(GateContextFixture.of(new RandomBot())).passed());
    }

    // --- 이 테스트에서만 쓰는 봇들 ---

    static final class SeededRandomBot implements Bot {
        public String name() { return "SeededRandomBot"; }
        public Direction move(GameView view) {
            var rng = new java.util.Random(view.turn());   // 시드 있음 = 허용
            return Direction.values()[rng.nextInt(4)];
        }
    }

    static final class FileReadingBot implements Bot {
        public String name() { return "FileReadingBot"; }
        public Direction move(GameView view) {
            java.io.File f = new java.io.File("/tmp/hint");
            return f.exists() ? Direction.UP : Direction.DOWN;
        }
    }

    static final class MutableStaticBot implements Bot {
        static int counter = 0;   // non-final static = 전역 가변 상태
        public String name() { return "MutableStaticBot"; }
        public Direction move(GameView view) {
            counter++;
            return Direction.values()[counter % 4];
        }
    }
}
