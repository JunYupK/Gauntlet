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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class RecordStoreTest {

    /**
     * {@code records/} 아래 곁다리 하나가 이후 모든 명령을 종료 코드 3으로
     * 만들면 안 된다.
     *
     * {@code nextAttempt}는 {@code attempt-} 접두사만 보고 남은 부분을
     * {@code Integer.parseInt}에 그대로 먹였다 — {@code attempt-3.bak}
     * 하나가 {@link NumberFormatException}으로 터지고, 그건
     * {@code UncheckedIOException} catch에도 안 잡혀 CLI의 일반
     * {@code RuntimeException} catch까지 올라가 "하네스 오류(3)"가 된다.
     * 기록 디렉터리는 사람이 들여다보고 손대는 곳이라 백업 파일 하나가
     * 하네스를 망가진 것으로 보이게 만드는 셈이었다.
     */
    @Test
    void 숫자가_아닌_attempt_디렉터리는_조용히_무시한다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "class A {}", rejected("G3"));
        store.saveGateReport(7, 2, "class A {}", rejected("G4"));

        Path genDir = tmp.resolve("gen-07");
        Files.createDirectories(genDir.resolve("attempt-3.bak"));
        Files.createDirectories(genDir.resolve("attempt-old"));
        Files.createDirectories(genDir.resolve("attempt-"));
        Files.createDirectories(genDir.resolve("attempt--1"));
        Files.createDirectories(genDir.resolve("notes"));
        Files.writeString(genDir.resolve("attempt-9.txt"), "메모");

        assertEquals(3, assertDoesNotThrow(() -> store.nextAttempt(7)),
                "곁다리 이름이 시도 번호 계산을 오염시켰다");
    }

    /**
     * 이력의 detail 문자열은 loop-history.json으로 흘러 들어간다. 기본
     * 로케일에 맡기면 소수점이 쉼표인 로케일에서 같은 입력이 다른 바이트를
     * 만든다 — 이 산출물의 존재 이유가 바이트 동일성이다. 기본 로케일을
     * 실제로 바꿔 놓고 재서, 끝나면 되돌린다.
     */
    @Test
    void 이력_문자열은_로케일이_바뀌어도_같다(@TempDir Path tmp) {
        Locale original = Locale.getDefault();
        try {
            RecordStore store = new RecordStore(tmp);
            store.saveChallengeReport(7, 1, challengeRejected());

            Locale.setDefault(Locale.US);
            String us = store.historyOf(7).get(0).detail();

            Locale.setDefault(Locale.GERMANY);
            String de = store.historyOf(7).get(0).detail();

            assertEquals(us, de, "로케일에 따라 이력 문자열이 달라진다");
            assertTrue(de.contains("0.48"),
                    "독일어 로케일에서 소수점이 쉼표로 바뀌었다: " + de);
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 자릿수가 int를 넘는 이름도 parseInt를 터뜨리지 않고 무시된다. */
    @Test
    void 지나치게_긴_숫자_이름도_터지지_않는다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "class A {}", rejected("G3"));

        Files.createDirectories(tmp.resolve("gen-07").resolve("attempt-99999999999999999999"));

        assertEquals(2, assertDoesNotThrow(() -> store.nextAttempt(7)));
    }

    private GateReport rejected(String gate) {
        return new GateReport("Gen07Bot", false, gate, gate + " 위반",
                List.of(GateResult.fail(gate, gate + " 위반")));
    }

    private ChallengeReport challengeRejected() {
        return new ChallengeReport("Gen07Bot", "Gen06Bot", false, 0.48, 0.60,
                44, 8, 48, Double.NaN,
                List.of(new DiagnosisEntry(12, 87, "UP", "LEFT", 214, 31, 183)));
    }

    private GateReport passed() {
        return new GateReport("Gen08Bot", true, null, "",
                List.of(GateResult.pass("G1"), GateResult.pass("G2")));
    }

    private ChallengeReport challengePromoted() {
        return new ChallengeReport("Gen08Bot", "Gen07Bot", true, 0.68, 0.60,
                68, 5, 27, 0.62,
                List.of());
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

    // --- MatchResult.isDraw()가 만드는 "draw" 파생 필드가 실제 읽기 경로를 막지 않는다 ---

    @Test
    void 무승부_리플레이가_draw_필드_없이_실제_읽기_경로로_왕복한다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        Replay draw = new Replay(
                Replay.SCHEMA, "m-draw", 30, 30, 7L, false,
                "bot0", new Point(5, 5), Direction.RIGHT,
                "bot1", new Point(6, 5), Direction.LEFT,
                "RL", new MatchResult(-1, 3, DeathReason.HEAD_ON_COLLISION),
                "hash-draw");

        store.saveReplays(11, List.of(draw));

        // 저장된 JSON 자체에 파생 필드 "draw"가 없어야 한다 — 있다면
        // 엄격한 기본 설정으로는 아래 readReplays가 예외를 던졌을 것이다.
        String json = Files.readString(tmp.resolve("gen-11/replays.json"));
        assertFalse(json.contains("\"draw\""), "isDraw()가 파생 필드로 새 나갔다: " + json);

        // JsonNode로 얼버무리지 않고 RecordStore의 실제 읽기 경로로 되읽는다.
        List<Replay> back = store.readReplays(11);

        assertEquals(1, back.size());
        Replay r = back.get(0);
        assertTrue(r.result().isDraw());
        assertEquals(-1, r.result().winner());
        assertEquals(DeathReason.HEAD_ON_COLLISION, r.result().reason());
        assertEquals("hash-draw", r.hash());
        assertEquals(draw, r, "왕복 후 리플레이가 원본과 달라졌다");
    }

    // --- historyOf의 경계 동작: 없는 세대 ---

    @Test
    void 시도가_없는_세대의_이력은_빈_리스트다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        assertTrue(store.historyOf(42).isEmpty());
    }

    // --- 성공 경로도 두 verdict 모두 실제로 거친다 (리뷰 반려 1) ---

    @Test
    void 관문을_통과하면_이력에_PASSED로_남는다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(8, 1, "class Clean {}", passed());

        List<AttemptRecord> history = store.historyOf(8);

        assertEquals(1, history.size());
        assertEquals("PASSED", history.get(0).verdict());
        assertEquals("GATE", history.get(0).stage());
        assertNull(history.get(0).failedGate());
    }

    @Test
    void 챔피언전에서_승격하면_이력에_PROMOTED로_남는다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        store.saveChallengeReport(8, 1, challengePromoted());

        List<AttemptRecord> history = store.historyOf(8);

        assertEquals(1, history.size());
        assertEquals("PROMOTED", history.get(0).verdict());
        assertEquals("CHAMPIONSHIP", history.get(0).stage());
    }

    // --- 개행은 리터럴 "\n"으로 고정된다: OS(System.lineSeparator())에 기대지 않는다 ---

    @Test
    void JSON_출력에_CR이_섞이지_않는다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "class A {}", rejected("G3"));
        store.saveChallengeReport(7, 2, challengeRejected());

        byte[] gateBytes = Files.readAllBytes(tmp.resolve("gen-07/attempt-1/gate-report.json"));
        byte[] champBytes = Files.readAllBytes(tmp.resolve("gen-07/attempt-2/championship.json"));

        for (byte b : gateBytes) assertNotEquals((byte) '\r', b, "gate-report.json에 CR이 섞였다");
        for (byte b : champBytes) assertNotEquals((byte) '\r', b, "championship.json에 CR이 섞였다");
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

    // --- holdoutOf: 승격한 시도의 홀드아웃 승률만 화면 6이 읽을 수 있게 노출한다 ---

    @Test
    void 승격한_시도의_홀드아웃_승률을_읽는다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        store.saveChallengeReport(3, 1, new ChallengeReport(
                "Gen03Bot", "Gen02Bot", false, 0.48, 0.60, 20, 8, 22, Double.NaN, List.of()));
        store.saveChallengeReport(3, 2, new ChallengeReport(
                "Gen03Bot", "Gen02Bot", true, 0.71, 0.60, 65, 12, 23, 0.63, List.of()));

        assertEquals(0.63, store.holdoutOf(3), 1e-9);
    }

    @Test
    void 승격한_시도가_없으면_홀드아웃은_NaN이다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        store.saveChallengeReport(4, 1, new ChallengeReport(
                "Gen04Bot", "Gen03Bot", false, 0.48, 0.60, 20, 8, 22, Double.NaN, List.of()));

        assertTrue(Double.isNaN(store.holdoutOf(4)), "반려만 있는 세대의 홀드아웃은 NaN이어야 한다");
    }

    @Test
    void 기록이_아예_없는_세대의_홀드아웃도_NaN이다(@TempDir Path tmp) {
        assertTrue(Double.isNaN(new RecordStore(tmp).holdoutOf(9)));
    }

    /**
     * (리뷰 정정) 기존 세 테스트는 전부 "디스크상 마지막 시도"와 "마지막으로
     * 승격한 시도"가 우연히 일치한다 — {@code Championship.judge}가 반려
     * 리포트의 {@code holdoutScoreRate}를 항상 NaN으로 채우므로, 승격
     * 다음에 반려가 오는 순서를 시험하지 않으면 {@code r.promoted()} 가드를
     * 빼고 "마지막 championship.json을 무조건 덮어쓴다"로 바꿔도 세 테스트
     * 모두 그대로 통과한다. 그 순서를 직접 재현해야 가드가 실제로 하는
     * 일이 드러난다.
     */
    @Test
    void 승격_뒤에_반려가_와도_승격한_시도의_값을_읽는다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        store.saveChallengeReport(5, 1, new ChallengeReport(
                "Gen05Bot", "Gen04Bot", true, 0.71, 0.60, 65, 12, 23, 0.63, List.of()));
        store.saveChallengeReport(5, 2, new ChallengeReport(
                "Gen05Bot", "Gen04Bot", false, 0.48, 0.60, 20, 8, 22, Double.NaN, List.of()));

        assertEquals(0.63, store.holdoutOf(5), 1e-9,
                "마지막 시도가 반려여도 승격한 시도의 홀드아웃이 남아야 한다");
    }

    /**
     * 클래스 javadoc이 "승격 시도가 둘이면 마지막 것을 취한다"고 명시적으로
     * 주장하는 부분을 시험한다 — 이 시험이 없으면 그 주장은 아무도 지키지
     * 않는 문서일 뿐이다.
     */
    @Test
    void 승격한_시도가_둘이면_나중_것을_읽는다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        store.saveChallengeReport(6, 1, new ChallengeReport(
                "Gen06Bot", "Gen05Bot", true, 0.65, 0.60, 60, 10, 30, 0.61, List.of()));
        store.saveChallengeReport(6, 2, new ChallengeReport(
                "Gen06Bot", "Gen05Bot", true, 0.71, 0.60, 65, 12, 23, 0.63, List.of()));

        assertEquals(0.63, store.holdoutOf(6), 1e-9,
                "승격한 시도가 둘이면 마지막 승격의 홀드아웃을 읽어야 한다");
    }
}
