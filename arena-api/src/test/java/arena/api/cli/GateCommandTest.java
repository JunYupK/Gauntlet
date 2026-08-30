package arena.api.cli;

import arena.gate.GateReport;
import arena.gate.GateResult;
import arena.tournament.AttemptRecord;
import arena.tournament.RecordStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GateCommandTest {

    /**
     * BotRegistry.byName이 등록되지 않은 이름에서 곧바로 예외를 던지므로
     * 관문(G2~G7)이 전혀 돌지 않는다 — 이 테스트는 밀리초 안에 끝난다.
     * 반려(코드 1)와 구분되는 호출 오류(코드 2)를 고정한다.
     */
    @Test
    void 등록되지_않은_봇_이름은_2를_반환한다() {
        assertEquals(2, GateCommand.run("NoSuchBot"));
    }

    /**
     * 관문을 통과한 시도도 bot.java를 남겨야 한다.
     *
     * 예전에는 통과 시 곧장 return 해서 {@code saveGateReport}가 반려
     * 경로에서만 불렸다 — 소스가 <b>반려당했을 때만</b> 보존됐다는 뜻이다.
     * 그래서 관문을 넘고 챔피언전에서 48%로 떨어진 봇은 코드를 한 줄도
     * 남기지 않았는데, 그게 바로 발표가 필요로 하는 증거다(BRIEF §8).
     * 스펙 §8.3의 그림도 시도 디렉터리마다 bot.java가 리포트 옆에 있다.
     *
     * 판정을 주입하는 이유는 {@link GateCommand#run(String, Path, java.util.function.Function)}의
     * javadoc에 있다 — 이 저장소에는 전체 심사 시드로 G7까지 실제로
     * 통과하는 봇이 없어서(D65·D66) 통과 경로를 다른 방법으로는 밟아볼
     * 수 없다.
     */
    @Test
    void 관문을_통과한_시도도_bot_java와_리포트를_남긴다(@TempDir Path records) throws IOException {
        int code = GateCommand.run("Gen00Bot", records, bot -> passingReport(bot.name()));

        assertEquals(0, code, "통과는 종료 코드 0이어야 한다");

        Path attempt = records.resolve("gen-00").resolve("attempt-1");
        assertTrue(Files.exists(attempt.resolve("bot.java")),
                "통과한 시도에 bot.java가 없다: " + attempt);
        assertTrue(Files.exists(attempt.resolve("gate-report.json")),
                "통과한 시도에 gate-report.json이 없다: " + attempt);
        assertFalse(Files.readString(attempt.resolve("bot.java")).isBlank(),
                "bot.java가 비어 있다");
    }

    /** 반려 경로가 그대로 남는지도 같이 못박는다 — 통과 저장을 추가하며 잃으면 안 된다. */
    @Test
    void 반려된_시도도_여전히_bot_java와_리포트를_남긴다(@TempDir Path records) {
        int code = GateCommand.run("Gen00Bot", records, bot -> failingReport(bot.name()));

        assertEquals(1, code, "반려는 종료 코드 1이어야 한다");

        Path attempt = records.resolve("gen-00").resolve("attempt-1");
        assertTrue(Files.exists(attempt.resolve("bot.java")), "반려된 시도에 bot.java가 없다");
        assertTrue(Files.exists(attempt.resolve("gate-report.json")),
                "반려된 시도에 gate-report.json이 없다");
    }

    /**
     * 통과 저장의 부수 효과: {@link RecordStore#historyOf}의 PASSED/GATE
     * 갈래가 프로덕션에서 도달 가능해진다.
     *
     * 그전에는 통과한 gate-report.json이 디스크에 존재할 수 없었으므로
     * {@code r.passed() ? "PASSED" : "REJECTED"}의 참 갈래가 죽은 코드였다 —
     * 즉 발표 번들의 loop-history.json에 "관문은 넘었다"는 시도가 영원히
     * 나타날 수 없었다.
     */
    @Test
    void 통과한_시도는_이력에_PASSED_GATE로_나타난다(@TempDir Path records) {
        GateCommand.run("Gen00Bot", records, bot -> passingReport(bot.name()));

        List<AttemptRecord> history = new RecordStore(records).historyOf(0);

        assertEquals(1, history.size(), "시도가 하나 기록돼야 한다: " + history);
        assertEquals("PASSED", history.get(0).verdict());
        assertEquals("GATE", history.get(0).stage());
        assertNull(history.get(0).failedGate(), "통과했는데 failedGate가 채워졌다");
    }

    /** 세대 봇이 아닌 이름(베이스라인)은 기록을 남기지 않는다 — generationOf가 -1이다. */
    @Test
    void 세대_봇이_아니면_기록을_남기지_않는다(@TempDir Path records) {
        GateCommand.run("WallAvoidBot", records, bot -> passingReport(bot.name()));

        assertFalse(Files.exists(records.resolve("gen-00")),
                "베이스라인 이름으로 세대 디렉터리가 생겼다");
    }

    /**
     * 스펙 §5: 세대당 재시도는 5회가 한도다. attempt 1‥5가 이미 채워진
     * 세대에서 6번째 gate를 열려 하면 판정·기록 전에 CONVERGED로 거부돼야
     * 한다(코드 1) — attempt-6 디렉터리 자체가 생기면 안 된다.
     *
     * BotRegistry에 등록된 세대 봇은 지금 Gen00Bot 하나뿐이라(BotRegistry
     * GENERATIONS) 봇 이름은 "Gen00Bot"을 그대로 쓰고, 대신 attempt
     * 기록을 세대 0 아래(디스크상 "gen-00", 두 자리 zero-padding)에
     * 직접 채워 넣어 "이미 5회를 소진한 세대"를 재현한다.
     */
    @Test
    void 여섯번째_시도는_CONVERGED로_거부된다(@TempDir Path records) {
        RecordStore store = new RecordStore(records);
        for (int i = 1; i <= 5; i++) {
            store.saveGateReport(0, i, "class Gen00Bot{}", failingReport("Gen00Bot"));
        }

        int code = GateCommand.run("Gen00Bot", records, bot -> passingReport(bot.name()));

        assertEquals(1, code, "6번째 개방은 판정 거부(1)여야 한다");
        assertFalse(Files.exists(records.resolve("gen-00").resolve("attempt-6")),
                "5회를 소진했는데 attempt-6이 열렸다");
    }

    private static GateReport passingReport(String botName) {
        return new GateReport(botName, true, null, "", List.of(
                GateResult.pass("G2"), GateResult.pass("G3"), GateResult.pass("G4"),
                GateResult.pass("G5"), GateResult.pass("G6"), GateResult.pass("G7")));
    }

    private static GateReport failingReport(String botName) {
        return new GateReport(botName, false, "G4", "표본 17에서 null을 반환했다", List.of(
                GateResult.pass("G2"), GateResult.pass("G3"),
                GateResult.fail("G4", "표본 17에서 null을 반환했다")));
    }
}
