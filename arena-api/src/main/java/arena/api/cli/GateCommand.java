package arena.api.cli;

import arena.api.Seeds;
import arena.bots.Bot;
import arena.bots.BotRegistry;
import arena.gate.GateContext;
import arena.gate.GateReport;
import arena.gate.GateRunner;
import arena.tournament.RecordStore;

import java.nio.file.Path;

/**
 * 관문 G2~G7을 돌린다. G1(컴파일)은 Gradle이 이미 판정했다.
 *
 * {@code botName}은 반드시 {@link BotRegistry#byName}을 통과해야 무엇이든
 * 시작된다 — 등록된 봇 중 어느 것과도 이름이 맞지 않으면
 * {@link IllegalArgumentException}을 여기서 붙잡아 사람이 읽을 오류와
 * 종료 코드 2를 낸다. 잡지 않았다면 예외가 {@code main}까지 새어 나가
 * 스택 트레이스로 끝났을 것이다 — "봇이 반려당한다"와 "이름이 잘못됐다"는
 * 서로 다른 실패이고, 후자는 하네스가 무너진 게 아니라 호출이 잘못된
 * 것이므로 반려(코드 1)와 종료 코드를 공유하지 않는다.
 */
public final class GateCommand {

    private GateCommand() {}

    public static int run(String botName) {
        Bot bot;
        try {
            bot = BotRegistry.byName(botName);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return 2;
        }

        GateReport report = GateRunner.run(new GateContext(
                bot, bot.getClass(), Seeds.WIDTH, Seeds.HEIGHT, Seeds.JUDGING));

        if (report.passed()) {
            System.out.println("통과 — " + botName + "이 관문 G2~G7을 모두 넘었다");
            return 0;
        }

        System.out.println("반려 — " + report.failedGate());
        System.out.println(report.detail());

        int generation = generationOf(botName);
        if (generation >= 0) {
            RecordStore store = new RecordStore(Path.of("records"));
            store.saveGateReport(generation, store.nextAttempt(generation),
                    readSourceOrPlaceholder(botName), report);
        }
        return 1;
    }

    /**
     * "Gen07Bot" → 7. 세대 봇이 아니면 -1.
     *
     * botName은 이 시점에 이미 {@link BotRegistry#byName}이 찾아낸 등록된
     * 봇의 이름이다 — CLI가 즉석에서 지어낸 문자열이 아니다. 그래도
     * {@code Integer.parseInt}가 받아들이는 범위(부호 문자 하나 + 숫자만)만
     * 통과하므로, 아래 {@link #readSourceOrPlaceholder}가 만드는 경로에
     * {@code /}나 {@code ..}가 섞일 여지는 애초에 없다.
     */
    static int generationOf(String botName) {
        if (!botName.startsWith("Gen") || !botName.endsWith("Bot")) return -1;
        try {
            return Integer.parseInt(botName.substring(3, botName.length() - 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readSourceOrPlaceholder(String botName) {
        Path source = Path.of("arena-bots/src/main/java/arena/bots/gen", botName + ".java");
        try {
            return java.nio.file.Files.readString(source);
        } catch (java.io.IOException e) {
            return "// 소스를 읽지 못했다: " + source;
        }
    }
}
