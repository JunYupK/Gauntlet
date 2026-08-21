package arena.tournament;

import arena.bots.Bot;
import arena.core.Match;
import arena.core.Replay;
import arena.core.SeriesRunner;
import arena.core.Standing;
import arena.diagnostics.LossAnalyzer;
import arena.diagnostics.MatchMetrics;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

/**
 * 화면이 읽을 정적 JSON을 만든다.
 *
 * 프론트엔드가 이 파일들만 읽으므로 발표 당일 백엔드가 떠 있지
 * 않아도 화면이 뜬다. 서버 장애라는 위험 범주 자체가 사라진다.
 */
public final class BundleBuilder {

    /**
     * 개행 문자를 "\n"으로 고정한다. {@link RecordStore}와 같은 이유다:
     * Jackson의 기본 pretty printer는 {@code System.lineSeparator()}를
     * 쓰므로, 손대지 않으면 이 번들을 만든 OS에 따라 같은 입력도 다른
     * 바이트(리눅스 LF vs. 윈도우 CRLF)가 나올 수 있다.
     */
    private static final DefaultPrettyPrinter PRETTY_PRINTER = new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));

    /**
     * {@code IS_GETTER} 가시성을 낮추는 이유는 {@link RecordStore#MAPPER}의
     * javadoc과 같다 — {@code gallery.json}이 담는 {@link Replay}에도
     * {@code MatchResult}가 들어 있어 같은 {@code "draw"} 파생 필드 문제가
     * 똑같이 생긴다. {@code arena-api}의 {@code record --verify}가 이
     * 파일을 실제로 역직렬화해 해시를 재계산하므로(arena-tournament는
     * arena-api를 모르지만, 소비자가 그렇게 쓴다) 여기서도 같은 처방이
     * 필요하다.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPrettyPrinter(PRETTY_PRINTER)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);

    /**
     * 라운드로빈 시드(전역 제약: 1~10). 심사·홀드아웃 시드와는 별개로
     * 고정된 값이다 — 마지막에 한 번만 돌리는 기록용 히트맵이지 승격
     * 판정이 아니므로, 호출자가 넘기는 {@code judgingSeeds}의 크기나
     * 정렬 순서에 기대지 않고 이 클래스가 스스로 못박는다.
     */
    private static final List<Long> ROUND_ROBIN_SEEDS = LongStream.rangeClosed(1, 10).boxed().toList();

    /**
     * 좌석 식별용 내부 예약 id. 세대·챔피언의 실제 {@code name()}과는
     * 무관하다. 세대 목록에 최종 챔피언 자신이 포함된 경우(마지막
     * 세대가 곧 최종 챔피언인 흔한 구성)에도, 또 두 이름이 우연히
     * 겹치는 경우에도 좌석 판정이 왜곡되지 않도록 서로 다른 상수로
     * 고정했다({@link Championship}이 같은 이유로 이미 쓰는 패턴).
     */
    private static final String GEN_ID = "__generation__";
    private static final String CHAMPION_ID = "__champion__";

    private BundleBuilder() {}

    public static void build(
            List<Bot> generations, Bot finalChampion,
            long gallerySeed, List<Long> judgingSeeds,
            int width, int height,
            RecordStore store, Path outputDir) {

        List<Replay> gallery = buildGallery(generations, finalChampion, gallerySeed, width, height);
        List<GenerationStat> stats = buildStats(
                generations, finalChampion, judgingSeeds, width, height, store);

        writeJson(outputDir.resolve("gallery.json"), gallery);
        writeJson(outputDir.resolve("generations.json"), stats);
        writeJson(outputDir.resolve("loop-history.json"), buildHistory(generations, store));
        writeJson(outputDir.resolve("roundrobin.json"), buildRoundRobin(generations, width, height));
    }

    /**
     * 모든 세대가 같은 시드로 최종 챔피언에게 도전하는 경기.
     * 패널끼리 비교하려면 상대와 시작 배치가 같아야 한다.
     *
     * 좌석 귀속 계산이 필요 없는 순수 기록이라(승패는 리플레이의
     * {@code winner} 인덱스와 {@code bot0Id}/{@code bot1Id}를 그대로
     * 보여주는 화면 쪽 책임), 여기서는 {@code bot.name()}을 그대로
     * 리플레이에 남겨도 안전하다 — 이름이 겹쳐도(예: 마지막 세대가
     * 곧 최종 챔피언인 경우) 그 자체로 유효한 자체 대전 리플레이가
     * 나올 뿐, 이 메서드 자신은 이름으로 아무것도 판정하지 않는다.
     */
    private static List<Replay> buildGallery(
            List<Bot> generations, Bot champion, long seed, int width, int height) {

        List<Replay> gallery = new ArrayList<>();
        for (Bot bot : generations) {
            gallery.add(Match.play(
                    bot.name(), bot::move,
                    champion.name(), champion::move,
                    seed, width, height));
        }
        return gallery;
    }

    /**
     * 세대별 지표를 같은 챔피언 상대로 잰다.
     *
     * SeriesRunner·Standing에는 {@code bot.name()}/{@code champion.name()}을
     * 절대 넘기지 않는다. 넘겼다면: 세대 목록의 마지막 원소가 곧
     * finalChampion과 이름이 같은 흔한 구성(이 클래스의 테스트 픽스처가
     * 실제로 그렇다)에서 SeriesRunner의 id0==id1이 되고, 좌석 교대
     * 경기(전체의 절반)에서도 리플레이의 {@code bot0Id}가 항상 같은
     * 문자열로 찍힌다. {@code r.bot0Id().equals(bot.name())}처럼 문자열을
     * 비교해 좌석을 판정하면 그 절반이 조용히 좌석 0으로 오판되어
     * occupancy·suicideRate·scoreRate가 전부 틀어진다 — 예외도 없이,
     * RoundRobin·Championship이 이미 겪은 것과 같은 사고다. 그래서 예약
     * id 둘만 쓰고, 실제 좌석은 {@link Standing#seatOf}로 판정한다.
     */
    private static List<GenerationStat> buildStats(
            List<Bot> generations, Bot champion, List<Long> seeds,
            int width, int height, RecordStore store) {

        List<GenerationStat> stats = new ArrayList<>();

        for (int gen = 0; gen < generations.size(); gen++) {
            Bot bot = generations.get(gen);

            List<Replay> replays = SeriesRunner.run(
                    GEN_ID, bot::move,
                    CHAMPION_ID, champion::move,
                    seeds, width, height, true);

            double totalTurns = 0;
            double totalOccupancy = 0;
            double totalSuicide = 0;

            for (Replay r : replays) {
                int mySeat = Standing.seatOf(r, GEN_ID);
                MatchMetrics m = LossAnalyzer.analyze(r);

                totalTurns += r.result().turns();
                totalOccupancy += m.occupancy()[mySeat];
                totalSuicide += m.suicideRate()[mySeat];
            }

            int n = replays.size();
            stats.add(new GenerationStat(
                    gen, bot.name(),
                    totalTurns / n,
                    totalOccupancy / n,
                    totalSuicide / n,
                    Standing.of(replays, GEN_ID).scoreRate(),
                    store.nextAttempt(gen) - 1));
        }
        return stats;
    }

    /**
     * 세대 번호 문자열을 키로 쓰는 {@link LinkedHashMap}이다 — 0부터
     * generations.size()-1까지 순서대로 넣으므로 순회 순서가 항상
     * 같고, 그래서 이 맵을 직렬화한 JSON도 실행마다 바이트 단위로
     * 동일하다(재현 가능성). {@link java.util.HashMap}을 썼다면 문자열
     * 키의 해시 버킷 배치가 순회 순서를 정하고, 그 배치가 JVM/버전에
     * 따라 달라질 여지가 생긴다.
     */
    private static Map<String, List<AttemptRecord>> buildHistory(
            List<Bot> generations, RecordStore store) {

        Map<String, List<AttemptRecord>> history = new LinkedHashMap<>();
        for (int gen = 0; gen < generations.size(); gen++) {
            history.put(String.valueOf(gen), store.historyOf(gen));
        }
        return history;
    }

    /**
     * 역대 전 세대 라운드로빈. 시드는 심사·홀드아웃과 별개로 고정된
     * {@link #ROUND_ROBIN_SEEDS}(1~10)를 쓴다.
     *
     * {@link RoundRobin#run}이 돌려주는 대각선의 {@link Double#NaN}을
     * 여기서 JSON {@code null}로 바꿔 내보낸다({@link #toWireMatrix}) —
     * 이 파일의 유일한 소비자가 자바스크립트 프론트엔드이기 때문이다.
     * {@code records/}처럼 자바가 다시 읽는 파일이라면 Jackson 기본
     * 동작(따옴표 붙은 {@code "NaN"} 문자열, 별도 설정 없이 double로
     * 왕복)을 그대로 둬도 되지만({@link RecordStore}·{@link ChallengeReport}가
     * 이미 그렇게 한다), 숫자 배열 안에 섞인 문자열 {@code "NaN"}은
     * JS 쪽에서 {@code cell.toFixed(2)}를 던지게 하거나 {@code cell > 0.5}를
     * 조용히 {@code false}로 만드는 타입 함정이다. {@code null}은 "빈 칸"의
     * 의미를 갖는 JSON 1급 값이라 그런 함정이 없다.
     */
    private static Map<String, Object> buildRoundRobin(
            List<Bot> generations, int width, int height) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bots", generations.stream().map(Bot::name).toList());
        payload.put("matrix", toWireMatrix(RoundRobin.run(generations, ROUND_ROBIN_SEEDS, width, height)));
        return payload;
    }

    /**
     * {@code double[][]}을 박싱된 {@code Double[][]}로 바꾸며 {@link Double#NaN}
     * 칸만 명시적으로 {@code null}로 치환한다. 박싱된 {@code Double}에 NaN
     * 값 자체를 그대로 담아 직렬화해도 Jackson은 여전히 {@code "NaN"}
     * 문자열을 적는다(원시 타입일 때와 동일한 특수 처리) — 그래서 값을
     * {@code null} 참조로 바꾸는 것만이 유효한 우회다.
     */
    private static Double[][] toWireMatrix(double[][] matrix) {
        Double[][] wire = new Double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            wire[i] = new Double[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                double v = matrix[i][j];
                wire[i][j] = Double.isNaN(v) ? null : v;
            }
        }
        return wire;
    }

    private static void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException("발표 번들을 쓸 수 없다: " + path, e);
        }
    }
}
