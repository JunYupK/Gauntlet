package arena.api;

import arena.bots.Bot;
import arena.bots.BotRegistry;
import arena.core.Direction;
import arena.core.GameView;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

    /**
     * 네 코드가 전부 기계가 읽는 줄로 렌더링되는지 확인한다.
     *
     * {@code run(String[])}만으로는 코드 2밖에 값싸게 만들 수 없어서
     * (0·1은 관문 전체를, 3은 하네스를 실제로 부숴야 나온다) 렌더링을
     * 직접 시험한다. "진짜 판정 결과가 이 줄까지 흘러온다"는 배선은
     * 아래 {@code 프로덕션_진입점은_진짜_종료_코드를_줄로_찍는다}가 맡는다.
     */
    @Test
    void 종료_코드_넷이_모두_기계가_읽는_줄로_나간다() {
        for (int expected = 0; expected <= 3; expected++) {
            int code = expected;
            String printed = captureOut(() -> assertEquals(code, ArenaApplication.emitExitCode(code)));

            assertEquals("ARENA_EXIT_CODE=" + expected, printed.strip(),
                    "코드 " + expected + "이 그대로 줄에 실리지 않았다: " + printed);
        }
    }

    /**
     * 배선 증명: 실제 판정 경로를 통과한 코드가 그 줄에 실려 나오는가.
     *
     * 없는 봇 이름이라 판정은 호출 오류(2)로 끝난다. 이 테스트가 지키는
     * 계약은 "표준 출력의 **마지막** 줄이 반환된 코드와 일치한다"이다 —
     * 규칙서 §8이 파싱 방법으로 제시한 그대로다. CLI가 그 앞에 사람이
     * 읽는 메시지를 몇 줄 찍든 상관없어야 한다.
     */
    @Test
    void 프로덕션_진입점은_진짜_종료_코드를_줄로_찍는다() {
        int[] code = new int[1];
        String out = captureOut(() -> code[0] = ArenaApplication.run(new String[]{"gate", "NoSuchBot"}));

        assertEquals(2, code[0], "없는 봇 이름은 호출 오류(2)여야 한다");

        List<String> lines = out.lines().filter(l -> !l.isBlank()).toList();
        assertEquals("ARENA_EXIT_CODE=2", lines.get(lines.size() - 1).strip(),
                "마지막 줄이 종료 코드 줄이 아니다. 전체 출력:\n" + out);

        assertEquals(1, lines.stream().filter(l -> l.contains("ARENA_EXIT_CODE=")).count(),
                "종료 코드 줄이 한 번만 나와야 한다. 전체 출력:\n" + out);
    }

    /**
     * 이 줄은 Gradle이 띄운 자식 JVM에서도 기계가 읽어야 한다. 그
     * JVM의 System.out은 UTF-8이 아닐 수 있어서(실측: CLI의 한국어
     * 메시지가 ./gradlew gate 출력에서 ??로 깨진다) 이 줄만은 어떤
     * 인코딩에서도 바이트가 같아야 한다 — 즉 순수 ASCII여야 한다.
     */
    @Test
    void 종료_코드_줄은_인코딩을_타지_않는_순수_ASCII다() {
        String line = ArenaApplication.EXIT_CODE_LINE_PREFIX + 1;

        assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(line),
                "종료 코드 줄에 ASCII 밖 문자가 있다: " + line);
        assertArrayEquals(line.getBytes(StandardCharsets.UTF_8), line.getBytes(StandardCharsets.ISO_8859_1),
                "인코딩에 따라 바이트가 달라진다: " + line);
    }

    /** {@code body}를 도는 동안의 표준 출력을 가로채 문자열로 준다. */
    private static String captureOut(Runnable body) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(originalOut);
        }
        return captured.toString(StandardCharsets.UTF_8);
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
