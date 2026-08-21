package arena.api;

import arena.bots.Bot;
import arena.bots.BotRegistry;
import arena.core.Direction;
import arena.core.GameView;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 등록 검증 실패가 실제로 main의 try/catch를 거쳐 종료 코드 3이 되는지를
 * 증명한다. BotRegistry.validate를 직접 부르는 테스트(BotRegistryTest)만으로는
 * 부족하다 — 그건 "규칙이 예외를 던진다"만 보여줄 뿐, "그 예외가
 * ArenaApplication에서 정확히 3으로 매핑되는가"는 이 클래스를 거쳐야만
 * 확인된다. 실제 BotRegistry의 등록 목록은 항상 유효하므로(그리고
 * private static final이라 오염시킬 수도 없으므로), ArenaApplication.run의
 * 3-인자 시야로 실패하는 검사를 주입해 이 경로를 재현한다.
 */
class ArenaApplicationTest {

    @Test
    void 등록_검증이_실패하면_3을_반환하고_원인을_담은_메시지를_남긴다() {
        Bot evil = fakeBot("Evil|Bot");
        Runnable brokenRegistration = () -> BotRegistry.validate(List.of(), List.of(evil));

        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured));
        int code;
        try {
            code = ArenaApplication.run(new String[]{"gate", "Whatever"}, brokenRegistration);
        } finally {
            System.setErr(originalErr);
        }

        assertEquals(3, code,
                "등록 검증 실패가 판정 거부(1)나 다른 코드로 샜다 — 하네스 오류는 3이어야 한다");
        assertTrue(captured.toString().contains("Evil|Bot"),
                "오류 메시지에 문제의 이름이 안 보인다: " + captured);
    }

    /**
     * 같은 JVM 안에서 검증 실패를 두 번 재현해도 (정적 초기화였다면
     * 두 번째부터 ExceptionInInitializerError 대신 메시지 없는
     * NoClassDefFoundError가 나왔을 지점) 매번 같은 코드·같은 메시지가
     * 나오는지 확인한다.
     */
    @Test
    void 등록_검증_실패는_같은_JVM에서_여러_번_불러도_매번_3을_반환한다() {
        Bot evil = fakeBot("Evil|Bot");
        Runnable brokenRegistration = () -> BotRegistry.validate(List.of(), List.of(evil));

        for (int i = 0; i < 3; i++) {
            PrintStream originalErr = System.err;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            System.setErr(new PrintStream(captured));
            int code;
            try {
                code = ArenaApplication.run(new String[]{"gate", "Whatever"}, brokenRegistration);
            } finally {
                System.setErr(originalErr);
            }

            assertEquals(3, code, (i + 1) + "번째 호출에서 코드가 3이 아니다");
            assertTrue(captured.toString().contains("Evil|Bot"),
                    (i + 1) + "번째 호출의 메시지에 문제의 이름이 없다: " + captured);
        }
    }

    @Test
    void 등록_검증이_통과하면_정상적으로_명령까지_실행된다() {
        // 등록 검증이 진짜(BotRegistry::validateRegistration)로 통과하면
        // 그 뒤의 명령까지 정상 실행된다는 걸 확인한다 — 없는 봇 이름이므로
        // GateCommand가 2를 반환한다(등록 검증 실패의 3과는 다른 코드).
        int code = ArenaApplication.run(
                new String[]{"gate", "NoSuchBot"}, BotRegistry::validateRegistration);

        assertEquals(2, code);
    }

    @Test
    void 인자가_없으면_사용법을_보여주고_2를_반환한다() {
        assertEquals(2, ArenaApplication.run(new String[]{}, BotRegistry::validateRegistration));
    }

    @Test
    void 봇_이름이_없으면_2를_반환한다() {
        assertEquals(2, ArenaApplication.run(new String[]{"gate"}, BotRegistry::validateRegistration));
    }

    private static Bot fakeBot(String name) {
        return new Bot() {
            @Override
            public String name() { return name; }

            @Override
            public Direction move(GameView view) {
                throw new UnsupportedOperationException("등록 검증 테스트 전용 — move는 호출되지 않아야 한다");
            }
        };
    }
}
