package arena.tournament;

import arena.core.Replay;
import arena.gate.GateReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 시도 이력을 파일로 남긴다.
 *
 * 반려된 봇 소스도 지우지 않는다. 실패 횟수가 보이는 편이 발표에
 * 유리하고, 반려 이력 자체가 "루프가 돌았다"의 증거다 (BRIEF §8).
 *
 * 경로는 {@code root/gen-%02d/attempt-%d/...} 형태로만 구성되며, 두 자리
 * 모두 호출자(하네스)가 넘기는 int다 — 봇이 통제하는 문자열(botName,
 * failedGate, detail, botSource 등)은 어디서도 경로 구성에 쓰이지 않고
 * 오직 파일 내용(소스 그대로이거나 JSON 값)으로만 들어간다. 그래서
 * 봇 작성자가 자신의 이름이나 소스에 {@code ../../etc/passwd} 같은
 * 문자열을 넣어도 root 밖으로 쓰기가 새 나갈 여지가 없다.
 *
 * 세대당 재시도 한도(5회, BRIEF §7)는 이 클래스가 강제하지 않는다.
 * {@link #nextAttempt(int)}는 디스크에 이미 남은 시도 번호를 세어
 * "다음 번호"를 보고할 뿐이다 — 5회를 넘겨도 예외 없이 6, 7, ...을
 * 계속 돌려준다. 한도를 넘겼을 때 실험을 CONVERGED로 끝낼지는 세대
 * 루프(다음 태스크)의 책임이다: 기록 저장소는 "무슨 일이 있었는지"의
 * 진실을 담는 곳이지, "다음에 무엇을 허용할지"를 결정하는 곳이 아니다.
 * 저장소가 스스로 한도를 강제하면, 한도를 초과한 시도 자체를 기록할
 * 방법이 없어져 "재시도가 다섯 번을 넘었다"는 사실 자체가 사라진다 —
 * 반려 이력을 지우지 않는다는 원칙(CLAUDE.md §2)과 상충한다.
 */
public final class RecordStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path root;

    public RecordStore(Path root) {
        this.root = root;
    }

    /**
     * 세대 {@code generation}의 다음 시도 번호. 디스크에 남은 attempt-N
     * 디렉터리 중 최댓값 + 1이며, 5회 한도를 넘겨도 강제하지 않고 계속
     * 다음 번호를 보고한다 (클래스 javadoc 참고). root나 세대 디렉터리가
     * 아직 없으면 1을 돌려준다 — 예외를 던지거나 디렉터리를 만들지 않는다.
     */
    public int nextAttempt(int generation) {
        Path genDir = generationDir(generation);
        if (!Files.isDirectory(genDir)) return 1;

        try (var entries = Files.list(genDir)) {
            return entries
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("attempt-"))
                    .mapToInt(n -> Integer.parseInt(n.substring("attempt-".length())))
                    .max()
                    .orElse(0) + 1;
        } catch (IOException e) {
            throw new UncheckedIOException("시도 번호를 셀 수 없다: " + genDir, e);
        }
    }

    public void saveGateReport(int gen, int attempt, String botSource, GateReport report) {
        Path dir = attemptDir(gen, attempt);
        write(dir.resolve("bot.java"), botSource);
        writeJson(dir.resolve("gate-report.json"), report);
    }

    public void saveChallengeReport(int gen, int attempt, ChallengeReport report) {
        writeJson(attemptDir(gen, attempt).resolve("championship.json"), report);
    }

    public void saveReplays(int gen, List<Replay> replays) {
        writeJson(generationDir(gen).resolve("replays.json"), replays);
    }

    /**
     * 세대의 시도 이력을 시도 번호 순으로 읽는다 (파일시스템 나열 순서가
     * 아니라, 1부터 nextAttempt-1까지 명시적으로 순회한다 — 파일시스템의
     * 디렉터리 나열 순서는 플랫폼에 따라 달라질 수 있어 그대로 믿지 않는다).
     * 세대 디렉터리가 없거나 시도가 하나도 없으면 빈 리스트를 돌려준다.
     */
    public List<AttemptRecord> historyOf(int generation) {
        List<AttemptRecord> history = new ArrayList<>();

        for (int attempt = 1; attempt < nextAttempt(generation); attempt++) {
            Path dir = attemptPath(generation, attempt);

            Path championship = dir.resolve("championship.json");
            if (Files.exists(championship)) {
                ChallengeReport r = readJson(championship, ChallengeReport.class);
                history.add(new AttemptRecord(generation, attempt,
                        r.promoted() ? "PROMOTED" : "REJECTED", "CHAMPIONSHIP", null,
                        String.format("승점 승률 %.2f (기준 %.2f)", r.scoreRate(), r.threshold())));
                continue;
            }

            Path gate = dir.resolve("gate-report.json");
            if (Files.exists(gate)) {
                GateReport r = readJson(gate, GateReport.class);
                history.add(new AttemptRecord(generation, attempt,
                        r.passed() ? "PASSED" : "REJECTED", "GATE",
                        r.failedGate(), r.detail()));
            }
        }
        return history;
    }

    private Path generationDir(int generation) {
        return root.resolve(String.format("gen-%02d", generation));
    }

    /** 시도 디렉터리 경로. 없으면 만든다 — 쓰기 경로(save*)에서만 쓴다. */
    private Path attemptDir(int generation, int attempt) {
        Path dir = attemptPath(generation, attempt);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("디렉터리를 만들 수 없다: " + dir, e);
        }
        return dir;
    }

    /**
     * 시도 디렉터리 경로만 계산한다 — 디스크에 아무것도 만들지 않는다.
     * {@code historyOf}처럼 읽기만 하는 경로에서 쓴다: 세대에 시도 번호가
     * 연속이 아니게 비어 있을 때(예: attempt-2가 지워지고 attempt-1,
     * attempt-3만 남았을 때)도 조회가 빈 attempt-2 디렉터리를 새로
     * 만들어버리는 부작용이 없어야 한다.
     */
    private Path attemptPath(int generation, int attempt) {
        return generationDir(generation).resolve("attempt-" + attempt);
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 쓸 수 없다: " + path, e);
        }
    }

    private static void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException("JSON을 쓸 수 없다: " + path, e);
        }
    }

    private static <T> T readJson(Path path, Class<T> type) {
        try {
            return MAPPER.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("JSON을 읽을 수 없다: " + path, e);
        }
    }
}
