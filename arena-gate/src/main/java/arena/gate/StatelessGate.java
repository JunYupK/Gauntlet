package arena.gate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * G2 — 봇은 인스턴스 필드를 가질 수 없다.
 *
 * 이 하나가 "같은 입력 → 같은 출력"을 인터페이스 수준에서 강제한다.
 * R1이 규율이 아니라 구조가 되는 지점이다.
 *
 * static final 상수는 허용한다. 방향 우선순위 배열 같은 것까지
 * 막으면 정상적인 봇을 만들 수 없다. 전역 가변 상태는 G3가 잡는다.
 *
 * {@code getDeclaredFields()}는 그 클래스 자신에 선언된 필드만 준다 —
 * 슈퍼클래스 필드는 보이지 않는다. 봇이 추상 부모 클래스에 상태를 숨기면
 * 그 구멍으로 조용히 통과하므로, {@code Object}까지 클래스 계층을 걸어
 * 올라가며 매 단계에서 검사한다.
 */
public final class StatelessGate implements Gate {

    @Override
    public String id() { return "G2"; }

    @Override
    public GateResult check(GateContext ctx) {
        List<String> violations = new ArrayList<>();

        for (Class<?> c = ctx.botClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic()) continue;               // 컴파일러가 만든 필드
                if (Modifier.isStatic(f.getModifiers())) continue;

                violations.add(f.getType().getSimpleName() + " " + f.getName());
            }
        }

        if (violations.isEmpty()) {
            return GateResult.pass(id());
        }
        return GateResult.fail(id(),
                "인스턴스 필드가 " + violations.size() + "개 있다: "
                        + String.join(", ", violations)
                        + " — 봇은 무상태 순수 함수여야 한다");
    }
}
