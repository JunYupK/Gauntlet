package arena.tournament;

import arena.core.Replay;
import arena.gate.GateReport;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    /**
     * 개행 문자를 "\n"으로 고정한다. Jackson의 기본 pretty printer는
     * {@code System.lineSeparator()}를 쓰므로, 손대지 않으면 이 기록을
     * 만든 OS에 따라 같은 입력도 다른 바이트(리눅스 LF vs. 윈도우 CRLF)가
     * 나올 수 있다 — 이 프로젝트의 산출물은 "다른 기계에서 다시 읽고
     * 재검증"돼야 하므로, 개행을 OS에 맡기지 않고 리터럴로 못박는다.
     */
    private static final DefaultPrettyPrinter PRETTY_PRINTER = new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));

    /**
     * {@code MatchResult.isDraw()}는 레코드 컴포넌트가 아니라 자바빈
     * 관례({@code isXxx()})를 따르는 파생 접근자다. Jackson은 기본
     * 설정에서 이런 {@code isXxx()}도 프로퍼티로 인식해 직렬화 결과에
     * 레코드에 없는 {@code "draw"} 필드를 추가로 써 넣고, 그 JSON을
     * 그대로 되읽으면(엄격한 기본 설정 그대로) {@code UnrecognizedPropertyException}이
     * 난다 — 재검증({@code record --verify})이 실제로 저장된 리플레이를
     * 다시 읽어 해시를 재계산하는 이 클래스에서는 이게 그대로 하네스
     * 오류로 번진다.
     *
     * 고친 자리는 두 곳 중 하나를 고를 수 있었다: (a) {@code MatchResult}에
     * {@code @JsonIgnore}를 붙이거나 (b) 읽는 쪽에서 알 수 없는 프로퍼티를
     * 관대하게 넘기거나. (a)는 {@code arena-core}가 어떤 프로덕션 의존성도
     * 갖지 않는다는 그 모듈 자신의 build.gradle 원칙(주석 참고)을 깨야만
     * 컴파일된다 — Jackson 애노테이션이라도 arena-core의 컴파일 classpath에
     * 올라간다. (b)는 이 MAPPER가 다루는 모든 JSON에 대해 "모르는 필드는
     * 조용히 넘긴다"를 전역으로 켜는 것이라, 진짜 스키마 드리프트(오타 난
     * 필드명, 지워야 했는데 안 지운 필드)까지 같이 삼켜버린다.
     *
     * 그래서 셋째 길을 택했다: {@code MatchResult}도 건드리지 않고 읽기
     * 관용도도 넓히지 않는다 — {@code PropertyAccessor.IS_GETTER}의
     * 가시성을 {@code NONE}으로 낮춰, Jackson이 애초에 {@code isXxx()}
     * 형태의 파생 접근자를 프로퍼티로 인식하지 않게 한다. 레코드
     * 컴포넌트({@code winner()}, {@code turns()}, {@code reason()})는
     * "is"로 시작하지 않으므로 이 설정의 영향을 받지 않고 그대로
     * 직렬화된다 — Jackson의 레코드 지원은 getter 가시성 규칙이 아니라
     * 정준 생성자 파라미터를 직접 프로퍼티로 삼기 때문이다. 결과적으로
     * "draw"는 애초에 쓰이지 않으므로, 기본(엄격한) 설정 그대로도 다시
     * 읽을 수 있다 — 드리프트 감지는 전혀 약해지지 않는다.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPrettyPrinter(PRETTY_PRINTER)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);

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
                    .filter(RecordStore::isAttemptDirName)
                    .mapToInt(n -> Integer.parseInt(n.substring("attempt-".length())))
                    .max()
                    .orElse(0) + 1;
        } catch (IOException e) {
            throw new UncheckedIOException("시도 번호를 셀 수 없다: " + genDir, e);
        }
    }

    /**
     * 현재 존재하는 가장 큰 attempt 번호. 아직 없으면 0. gate가 연 attempt에
     * challenge가 이어 쓰기 위한 것이다 — nextAttempt는 "다음 쓸 번호"라 gate가
     * 이미 연 attempt를 가리키지 못한다.
     */
    public int latestAttempt(int generation) {
        return nextAttempt(generation) - 1;
    }

    /**
     * {@code attempt-<숫자>} 형식인가. 접두사만 보고 통과시키면 안 된다 —
     * {@code attempt-3.bak}이나 {@code attempt-old} 같은 곁다리 하나가
     * {@code Integer.parseInt}에서 {@link NumberFormatException}으로 터지고,
     * 그건 {@link UncheckedIOException}에도 안 잡혀 CLI의 일반
     * {@code RuntimeException} catch까지 올라가 <b>이후 모든 명령이 종료
     * 코드 3</b>이 된다. 백업 파일 하나가 하네스를 망가진 것으로 보이게
     * 만드는 셈이다. 기록 디렉터리는 사람이 들여다보고 손대는 곳이므로
     * 그런 곁다리는 사고가 아니라 예상 가능한 일이다 — 조용히 무시한다.
     *
     * 숫자만 받으므로 {@code attempt--1}·{@code attempt-+3}·{@code attempt-}도
     * 함께 걸러진다({@code Integer.parseInt}는 앞의 둘을 받아들인다).
     */
    private static boolean isAttemptDirName(String name) {
        if (!name.startsWith("attempt-")) return false;

        String suffix = name.substring("attempt-".length());
        if (suffix.isEmpty()) return false;

        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return false;
        }
        // 자릿수가 int를 넘으면 parseInt가 터진다 — 그것도 곁다리로 취급한다.
        return suffix.length() <= 9;
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
     * {@link #saveReplays}가 저장한 리플레이를 진짜로 역직렬화해 되읽는다
     * (JsonNode로 얼버무리지 않는다 — 재검증이 이 클래스가 존재하는
     * 이유다). 파일이 없으면 빈 리스트를 돌려준다.
     */
    public List<Replay> readReplays(int gen) {
        Path path = generationDir(gen).resolve("replays.json");
        if (!Files.exists(path)) return List.of();

        try {
            return MAPPER.readValue(path.toFile(), new TypeReference<List<Replay>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("리플레이를 읽을 수 없다: " + path, e);
        }
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
                        // Locale.ROOT: 이 문자열은 loop-history.json으로 흘러간다.
                        // 기본 로케일에 맡기면 소수점이 쉼표인 로케일에서 같은
                        // 입력이 다른 바이트를 만든다.
                        String.format(Locale.ROOT, "승점 승률 %.2f (기준 %.2f)",
                                r.scoreRate(), r.threshold())));
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

    /**
     * 세대의 홀드아웃 승률. 승격한 시도의 값이며, 승격한 시도가 없거나
     * 기록이 없으면 {@link Double#NaN}이다.
     *
     * 승격 시도는 세대당 최대 하나다(승격하는 순간 그 세대가 끝난다).
     * 그래도 마지막 것을 취하도록 쓴 이유는, 기록 디렉터리가 사람이
     * 손대는 곳이라 둘이 들어있는 상태를 예외가 아니라 "가장 나중 것이
     * 맞다"로 처리하는 편이 안전하기 때문이다 — 여기서 터지면 번들
     * 생성 전체가 하네스 오류(3)로 죽는다.
     */
    public double holdoutOf(int generation) {
        double holdout = Double.NaN;

        for (int attempt = 1; attempt < nextAttempt(generation); attempt++) {
            Path championship = attemptPath(generation, attempt).resolve("championship.json");
            if (!Files.exists(championship)) continue;

            ChallengeReport r = readJson(championship, ChallengeReport.class);
            if (r.promoted()) holdout = r.holdoutScoreRate();
        }
        return holdout;
    }

    /**
     * 세대가 채택한 봇의 소스. 관문을 통과했거나 승격한 시도의
     * {@code bot.java}이며, 그런 시도가 없으면 비어 있다.
     *
     * 반려된 시도의 소스는 디스크에 그대로 남는다(BRIEF §8 — 실패
     * 횟수가 보이는 편이 발표에 유리하다). 여기서 고르지 않을 뿐이다:
     * 화면 4가 "이 세대의 코드"로 보여줄 것은 채택된 쪽이고, 반려된
     * 코드는 화면 3(루프 타임라인)의 소관이다.
     */
    public Optional<String> acceptedSourceOf(int generation) {
        for (AttemptRecord record : historyOf(generation)) {
            if (record.verdict().equals("REJECTED")) continue;

            Path source = attemptPath(generation, record.attempt()).resolve("bot.java");
            if (Files.exists(source)) return Optional.of(read(source));
        }
        return Optional.empty();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("소스를 읽을 수 없다: " + path, e);
        }
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
