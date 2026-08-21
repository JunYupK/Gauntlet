package arena.tournament;

import arena.core.DeathReason;
import arena.core.Direction;
import arena.core.MatchResult;
import arena.core.Point;
import arena.core.Replay;
import arena.gate.GateReport;
import arena.gate.GateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordStoreTest {

    private GateReport rejected(String gate) {
        return new GateReport("Gen07Bot", false, gate, gate + " 위반",
                List.of(GateResult.fail(gate, gate + " 위반")));
    }

    private ChallengeReport challengeRejected() {
        return new ChallengeReport("Gen07Bot", "Gen06Bot", false, 0.48, 0.60,
                44, 8, 48, Double.NaN,
                List.of(new DiagnosisEntry(12, 87, "UP", "LEFT", 214, 31, 183)));
    }

    @Test
    void 첫_시도는_1번이다(@TempDir Path tmp) {
        assertEquals(1, new RecordStore(tmp).nextAttempt(7));
    }

    @Test
    void 저장할수록_시도_번호가_올라간다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);

        store.saveGateReport(7, 1, "class A {}", rejected("G3"));
        assertEquals(2, store.nextAttempt(7));

        store.saveGateReport(7, 2, "class B {}", rejected("G4"));
        assertEquals(3, store.nextAttempt(7));
    }

    @Test
    void 반려된_봇_소스도_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "class Rejected {}", rejected("G3"));

        Path source = tmp.resolve("gen-07/attempt-1/bot.java");
        assertTrue(Files.exists(source), "반려된 시도의 소스가 지워졌다");
        assertEquals("class Rejected {}", Files.readString(source));
    }

    @Test
    void 관문_리포트를_JSON으로_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "class A {}", rejected("G3"));

        String json = Files.readString(tmp.resolve("gen-07/attempt-1/gate-report.json"));
        assertTrue(json.contains("\"failedGate\""), json);
        assertTrue(json.contains("G3"), json);
    }

    @Test
    void 챔피언전_리포트를_JSON으로_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveChallengeReport(7, 3, challengeRejected());

        String json = Files.readString(tmp.resolve("gen-07/attempt-3/championship.json"));
        assertTrue(json.contains("\"scoreRate\""), json);
        assertTrue(json.contains("0.48"), json);
    }

    @Test
    void 이력에_반려_사유가_순서대로_쌓인다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "a", rejected("G3"));
        store.saveGateReport(7, 2, "b", rejected("G4"));
        store.saveChallengeReport(7, 3, challengeRejected());

        List<AttemptRecord> history = store.historyOf(7);

        assertEquals(3, history.size());
        assertEquals("G3", history.get(0).failedGate());
        assertEquals("G4", history.get(1).failedGate());
        assertEquals("CHAMPIONSHIP", history.get(2).stage());
        assertEquals("REJECTED", history.get(2).verdict());
    }

    // --- 재현 가능성: 같은 입력은 바이트 단위로 같은 JSON을 만든다 ---

    @Test
    void 같은_관문_리포트는_두_세대에_바이트_단위로_동일한_JSON을_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        GateReport report = rejected("G3");

        store.saveGateReport(1, 1, "class A {}", report);
        store.saveGateReport(2, 1, "class A {}", report);

        byte[] first = Files.readAllBytes(tmp.resolve("gen-01/attempt-1/gate-report.json"));
        byte[] second = Files.readAllBytes(tmp.resolve("gen-02/attempt-1/gate-report.json"));
        assertArrayEquals(first, second, "같은 입력인데 바이트가 다르다 — 재현 가능성이 깨졌다");
    }

    @Test
    void 같은_챔피언전_리포트는_두_세대에_바이트_단위로_동일한_JSON을_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        ChallengeReport report = challengeRejected();

        store.saveChallengeReport(1, 1, report);
        store.saveChallengeReport(2, 1, report);

        byte[] first = Files.readAllBytes(tmp.resolve("gen-01/attempt-1/championship.json"));
        byte[] second = Files.readAllBytes(tmp.resolve("gen-02/attempt-1/championship.json"));
        assertArrayEquals(first, second, "같은 입력인데 바이트가 다르다 — 재현 가능성이 깨졌다");
    }

    // --- NaN: holdoutScoreRate는 반려 시 NaN이다. JSON에 NaN 리터럴은 없다 ---

    @Test
    void 반려_리포트의_홀드아웃_승률_NaN이_문자열로_안전하게_직렬화된다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveChallengeReport(7, 1, challengeRejected());

        Path path = tmp.resolve("gen-07/attempt-1/championship.json");
        String json = Files.readString(path);
        // JSON 문법에 NaN 리터럴은 없다 — Jackson 기본 동작은 문자열 "NaN"으로 적는다.
        assertTrue(json.contains("\"holdoutScoreRate\" : \"NaN\""), json);

        // 그리고 다시 읽었을 때도 실제 Double.NaN으로 왕복돼야 한다(별도 설정 없이).
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ChallengeReport back = mapper.readValue(path.toFile(), ChallengeReport.class);
        assertTrue(Double.isNaN(back.holdoutScoreRate()));
    }

    // --- 재시도 한도(5회)와 nextAttempt의 관계: 강제하지 않고 보고만 한다 ---

    @Test
    void 다섯_번째_시도를_넘겨도_nextAttempt는_예외_없이_다음_번호를_보고한다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        for (int attempt = 1; attempt <= 5; attempt++) {
            store.saveGateReport(9, attempt, "class Bot" + attempt + " {}", rejected("G3"));
        }

        assertEquals(6, store.nextAttempt(9),
                "5회 한도를 강제하는 건 RecordStore의 책임이 아니다 — 다음 번호를 그대로 보고해야 한다");
        assertEquals(5, store.historyOf(9).size(), "다섯 번의 반려 시도가 전부 이력에 남아야 한다");
    }

    // --- 봇이 통제하는 문자열은 경로에 닿지 않는다 ---

    @Test
    void 봇_소스에_경로_탈출_문자열이_있어도_root_밖으로_새지_않는다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        String malicious = "../../../../etc/passwd 를 흉내내는 소스\nclass Evil {}";

        store.saveGateReport(3, 1, malicious, rejected("../../G3"));

        // 소스는 root 밑 지정된 위치(bot.java)에 "내용"으로만 들어가야 한다.
        Path expected = tmp.resolve("gen-03/attempt-1/bot.java");
        assertTrue(Files.exists(expected));
        assertEquals(malicious, Files.readString(expected));

        // root 바깥에는 아무 것도 생기지 않았어야 한다.
        Path outside = tmp.getParent();
        try (var siblings = Files.list(outside)) {
            assertTrue(siblings.allMatch(p -> p.equals(tmp) || !p.getFileName().toString().contains("etc")),
                    "root 밖에 파일이 생겼다 — 경로 탈출이 가능하다");
        }
    }

    // --- saveReplays: 인터페이스에 있지만 브리프 예시 테스트엔 없다 ---

    @Test
    void 리플레이를_JSON으로_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        Replay replay = new Replay(
                Replay.SCHEMA, "m-1", 30, 30, 42L, false,
                "bot0", new Point(1, 1), Direction.RIGHT,
                "bot1", new Point(28, 28), Direction.LEFT,
                "RL", new MatchResult(0, 1, DeathReason.P1_OUT_OF_BOUNDS),
                "hash-abc");

        store.saveReplays(5, List.of(replay));

        String json = Files.readString(tmp.resolve("gen-05/replays.json"));
        assertTrue(json.contains("\"matchId\" : \"m-1\""), json);
        assertTrue(json.contains("\"hash\" : \"hash-abc\""), json);
    }

    // --- historyOf의 경계 동작: 없는 세대 ---

    @Test
    void 시도가_없는_세대의_이력은_빈_리스트다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        assertTrue(store.historyOf(42).isEmpty());
    }

    // --- historyOf는 읽기 전용이어야 한다: 없는 시도 디렉터리를 만들면 안 된다 ---

    @Test
    void 이력_조회는_비어있는_시도_번호의_디렉터리를_만들지_않는다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        // attempt-1, attempt-3만 저장한다 — attempt-2는 (지워졌든 원래 없든) 비어 있다.
        store.saveGateReport(4, 1, "a", rejected("G3"));
        store.saveGateReport(4, 3, "c", rejected("G5"));

        store.historyOf(4);

        assertFalse(Files.exists(tmp.resolve("gen-04/attempt-2")),
                "읽기 전용이어야 할 historyOf가 빈 시도 디렉터리를 만들었다");
    }
}
