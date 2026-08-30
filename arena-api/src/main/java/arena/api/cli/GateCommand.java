package arena.api.cli;

import arena.api.Seeds;
import arena.bots.Bot;
import arena.bots.BotRegistry;
import arena.gate.GateContext;
import arena.gate.GateReport;
import arena.gate.GateRunner;
import arena.tournament.RecordStore;

import java.nio.file.Path;
import java.util.function.Function;

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
        return run(botName, Path.of("records"), GateCommand::judge);
    }

    /**
     * 기록 루트와 판정을 주입할 수 있는 시야. 프로덕션 경로
     * ({@link #run(String)})는 언제나 {@code records/}와 {@link #judge}를
     * 그대로 써서 이 오버로드로 위임한다.
     *
     * 존재 이유는 순전히 테스트다 — {@link arena.api.ArenaApplication}이
     * 등록 검증을 주입 가능하게 열어 둔 것과 같은 패턴이다. 두 축 모두
     * 필요하다. ① 기록 루트: 이 명령은 실제로 디스크에 쓰므로, 고정된
     * {@code records/}를 그대로 쓰면 테스트가 저장소의 진짜 기록을
     * 더럽힌다. ② 판정: "관문을 통과한 시도도 소스를 남기는가"를
     * 증명하려면 통과한 {@link GateReport}가 있어야 하는데, <b>이
     * 저장소에는 전체 심사 시드로 G7까지 실제로 통과하는 봇이 하나도
     * 없다</b>(D65·D66 — 베이스라인 3종은 자기 자신과 붙는 다리에서
     * 좌석 대칭성 때문에 패배가 구조적으로 발생하고, 나머지는 관문을
     * 시험하려고 쓴 픽스처다). 그래서 통과 경로는 판정을 주입하지
     * 않고서는 아예 밟아볼 수 없다 — 실제로 이 결함(통과하면 소스가
     * 저장되지 않는다)이 지금까지 발견되지 않은 이유이기도 하다.
     */
    static int run(String botName, Path recordsRoot, Function<Bot, GateReport> judge) {
        Bot bot;
        try {
            bot = BotRegistry.byName(botName);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return 2;
        }

        int generation = generationOf(botName);
        RecordStore store = null;
        if (generation >= 0) {
            store = new RecordStore(recordsRoot);
            // 스펙 §5: 세대당 재시도는 5회가 한도다. 6번째를 열려는
            // 시도는 판정도 기록도 하지 않고 여기서 곧장 거부한다 —
            // 그래야 attempt-6 디렉터리 자체가 생기지 않는다. 예전에는
            // CLAUDE.md §10이 말하듯 RecordStore.nextAttempt가 한도를
            // 넘겨도 6, 7, ...을 계속 내줬고, 한도를 강제할 책임은
            // "세대 루프"라는 아직 없는 코드에 떠넘겨져 있었다(R2 —
            // 기계 판정이어야 할 규칙이 사람의 재량에 맡겨져 있었다).
            if (store.nextAttempt(generation) > 5) {
                System.out.println("CONVERGED — 세대 " + generation + "은 재시도 5회를 소진했다");
                return 1;
            }
        }

        GateReport report = judge.apply(bot);

        // 통과든 반려든 똑같이 남긴다. 예전에는 통과 시 여기서 곧장
        // return 해서 saveGateReport가 반려 경로에서만 불렸다 — 그래서
        // bot.java가 "반려된 시도"에만 남고, 관문을 통과한 뒤 챔피언전에서
        // 48%로 떨어진 봇은 코드를 한 줄도 남기지 않았다. 그건 발표가
        // 정확히 필요로 하는 증거다(BRIEF §8, 스펙 §8.3은 시도 디렉터리마다
        // bot.java가 리포트 옆에 있는 그림을 보여준다). 부수 효과로
        // RecordStore.historyOf의 PASSED/GATE 갈래가 프로덕션에서
        // 도달 가능해진다 — 그전까지는 통과한 gate-report.json이 디스크에
        // 존재할 수 없어 죽은 코드였다.
        if (store != null) {
            store.saveGateReport(generation, store.nextAttempt(generation),
                    readSourceOrPlaceholder(botName), report);
        }

        if (report.passed()) {
            System.out.println("통과 — " + botName + "이 관문 G2~G7을 모두 넘었다");
            return 0;
        }

        System.out.println("반려 — " + report.failedGate());
        System.out.println(report.detail());
        return 1;
    }

    /** 프로덕션 판정. 관문 G2~G7을 실제로 돌린다. */
    private static GateReport judge(Bot bot) {
        return GateRunner.run(new GateContext(
                bot, bot.getClass(), Seeds.WIDTH, Seeds.HEIGHT, Seeds.JUDGING));
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
