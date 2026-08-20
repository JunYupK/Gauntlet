package arena.gate;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 자체 검토에서 찾은 구멍: {@code ForbiddenApiGate}가 {@code ctx.botClass()}
 * 하나만 스캔하면, 봇이 금지 호출을 중첩 클래스(익명·정적 중첩·로컬)로
 * 옮기는 것만으로 G3를 우회할 수 있다. G2가 상속으로 뚫렸던 것과 같은
 * 모양의 구멍이다 — 단일 클래스만 보는 구조적 검사는 봇 작성자가
 * "다른 클래스에 숨기기"로 쉽게 피해 간다.
 *
 * 람다 본문은 컴파일러가 같은 클래스 안의 private synthetic 메서드로
 * 넣기 때문에 이미 잡힌다(별도 테스트 불필요) — 진짜 별개의 .class
 * 파일이 되는 익명/정적 중첩 클래스만 이 구멍의 대상이다.
 */
class ForbiddenApiGateNestedClassTest {

    private final Gate gate = new ForbiddenApiGate();

    @Test
    void 정적_중첩_헬퍼_클래스에_숨긴_금지_호출을_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new HelperClassTrap()));
        assertFalse(r.passed(), "Helper 중첩 클래스로 옮긴 System.nanoTime()도 잡아야 한다");
        assertTrue(r.detail().contains("nanoTime"), r.detail());
    }

    @Test
    void 익명_내부_클래스에_숨긴_금지_호출을_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new AnonInnerTrap()));
        assertFalse(r.passed(), "익명 내부 클래스로 옮긴 System.nanoTime()도 잡아야 한다");
        assertTrue(r.detail().contains("nanoTime"), r.detail());
    }

    static final class HelperClassTrap implements Bot {
        public String name() { return "HelperClassTrap"; }
        public Direction move(GameView view) {
            return Direction.values()[Helper.pick()];
        }
        static final class Helper {
            static int pick() {
                return (int) Math.floorMod(System.nanoTime(), 4);   // ← 중첩 클래스에 숨긴 금지 호출
            }
        }
    }

    static final class AnonInnerTrap implements Bot {
        public String name() { return "AnonInnerTrap"; }
        public Direction move(GameView view) {
            java.util.function.Supplier<Long> s = new java.util.function.Supplier<Long>() {
                public Long get() { return System.nanoTime(); }   // ← 익명 클래스에 숨긴 금지 호출
            };
            return Direction.values()[(int) Math.floorMod(s.get(), 4)];
        }
    }
}
