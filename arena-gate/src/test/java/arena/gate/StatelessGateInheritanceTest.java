package arena.gate;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G2가 상속으로 감춘 인스턴스 상태까지 잡는지 확인한다.
 *
 * {@code Class#getDeclaredFields()}는 그 클래스 자신에 선언된 필드만 돌려준다 —
 * 슈퍼클래스 필드는 보이지 않는다. 봇이 추상 부모 클래스에 가변 상태를 두면
 * 이 구멍으로 G2를 그냥 우회할 수 있으므로, StatelessGate는 클래스 계층을
 * Object까지 걸어 올라가며 각 단계에서 검사해야 한다.
 */
class StatelessGateInheritanceTest {

    private final Gate gate = new StatelessGate();

    /** 부모에만 가변 인스턴스 필드가 있고, 자식엔 자기 필드가 없다. */
    abstract static class LeakyBase implements Bot {
        private int hiddenCounter = 0;

        @Override
        public Direction move(GameView view) {
            hiddenCounter++;
            return view.myDir();
        }
    }

    static final class InheritedStateTrap extends LeakyBase {
        @Override
        public String name() { return "InheritedStateTrap"; }
    }

    @Test
    void 부모_클래스의_인스턴스_필드도_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new InheritedStateTrap()));

        assertFalse(r.passed(), "부모 클래스에 숨긴 상태도 잡아야 한다");
        assertTrue(r.detail().contains("hiddenCounter"),
                "위반 필드 이름을 알려줘야 한다: " + r.detail());
    }
}
