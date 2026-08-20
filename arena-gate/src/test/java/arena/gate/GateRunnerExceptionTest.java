package arena.gate;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 관문이 검사 도중 예외를 던질 때 GateRunner가 어떻게 반응하는가.
 *
 * {@link ForbiddenApiGate#readClassFile}은 클래스 바이트를 찾지 못하면
 * {@link IllegalStateException}을 던진다(체크 예외가 아니므로 {@code
 * check()} 시그니처를 그대로 뚫고 나간다). GateRunner가 이걸 잡지 않으면
 * 봇 하나가 스캔 불가능하다는 이유로 나머지 봇 심사까지 통째로
 * 죽는다 — "봇은 반려당할 뿐, 하네스를 무너뜨릴 수 없다"는 계약을 깬다.
 *
 * 이 상황을 재현하려면 진짜로 클래스 바이트를 못 찾는 봇이 필요하다.
 * {@link ClassLoader#getResourceAsStream}이 항상 null을 돌려주는 로더로
 * 무해한 봇 클래스를 다시 정의해서 만든다 — 클래스 자체는 정상적으로
 * 로드·실행되지만(그래야 인스턴스를 만들 수 있으니), 그 바이트를 리소스로
 * 되찾을 방법만 없다.
 */
class GateRunnerExceptionTest {

    /** 무상태 정상 봇. G2는 통과해야 G3가 실제로 시도된다. */
    public static final class HarmlessBot implements Bot {
        @Override
        public String name() { return "HarmlessBot"; }

        @Override
        public Direction move(GameView view) { return Direction.UP; }
    }

    /** getResourceAsStream이 항상 실패하는 클래스로더. 클래스 정의 자체는 정상이다. */
    private static final class BlindLoader extends ClassLoader {
        BlindLoader(ClassLoader parent) { super(parent); }

        Class<?> redefine(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return null; // ForbiddenApiGate.readClassFile이 실패하는 지점
        }
    }

    private static Class<?> unscannableClassOf(Class<?> original) throws IOException {
        ClassLoader parent = original.getClassLoader();
        String resource = original.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream in = parent.getResourceAsStream(resource)) {
            assertNotNull(in, "테스트 전제가 깨졌다: 원본 클래스 바이트를 못 찾는다");
            bytes = in.readAllBytes();
        }
        return new BlindLoader(parent).redefine(original.getName(), bytes);
    }

    @Test
    void 클래스_바이트를_못_읽으면_G3를_실패로_돌리고_하네스는_죽지_않는다() throws Exception {
        Class<?> unscannable = unscannableClassOf(HarmlessBot.class);
        Bot bot = (Bot) unscannable.getDeclaredConstructor().newInstance();

        GateContext ctx = new GateContext(bot, unscannable, 30, 30,
                LongStream.rangeClosed(1, 50).boxed().toList());

        GateReport report = assertDoesNotThrow(() -> GateRunner.run(ctx),
                "관문 하나가 예외를 던졌다고 GateRunner.run 자체가 예외를 던지면 안 된다");

        assertFalse(report.passed());
        assertEquals("G3", report.failedGate(), "G2(무상태)는 통과하고 G3(바이트 읽기)에서 실패해야 한다");
        assertTrue(report.detail().contains("IllegalStateException"),
                "예외 종류가 반려 사유에 남아야 진단이 된다: " + report.detail());
        assertEquals(2, report.results().size(),
                "G2 통과 기록 1개 + G3 실패 기록 1개, 그 뒤 관문은 돌지 않아야 한다");
    }
}
